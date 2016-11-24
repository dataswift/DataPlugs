/*
 * Copyright (C) HAT Data Exchange Ltd - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */
package org.hatdex.dataplug.actors

import java.util.UUID

import akka.actor.{ Actor, ActorLogging, Props, _ }
import akka.pattern.pipe
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import org.hatdex.dataplug.models.{ HatClientCredentials, JwtToken }
import org.hatdex.hat.api.services.HatClient
import org.joda.time.{ DateTime, Duration }
import play.api.Logger
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Try

object HatClientActor {
  def props(ws: WSClient, hat: String, config: Config, credentials: HatClientCredentials): Props =
    Props(new HatClientActor(ws, hat, config, credentials))

  sealed trait AuthMessage

  case class FetchDataDebit(id: UUID)

  case class FetchingFailed(message: String)

  private case class Connected(token: String, jwtToken: JwtToken) extends AuthMessage

  private case class Unauthorized(message: String) extends AuthMessage

  private case object Connect extends AuthMessage

  private case object Disconnected extends AuthMessage

}

class HatClientActor(ws: WSClient, hat: String, config: Config, credentials: HatClientCredentials) extends Actor with ActorLogging with Stash {

  import HatClientActor._

  lazy val hatProtocol = {
    (hatSecure, mockClient) match {
      case (_, true)      => ""
      case (true, false)  => "https://"
      case (false, false) => "http://"
    }
  }
  val logger = Logger(s"[actor] ${self.path.name}")
  // Use the actor's dispatcher as execution context for api calls
  implicit val ec: ExecutionContext = context.dispatcher
  val hatSecure = config.getOrElse("dexter.secure", false)
  val mockClient = config.getOrElse("dexter.mock", false)
  val hatAddress = if (mockClient) {
    ""
  }
  else {
    hat
  }

  val hatClient = new HatClient(ws, hatAddress, hatProtocol)

  var maybeToken: Option[String] = None
  var maybeJwtToken: Option[JwtToken] = None

  def receive: Receive = {
    case Connect =>
      logger.debug(s"Connecting to $hat with $credentials")
      val authResponse = hatClient.authenticateForToken(credentials.username, credentials.password) map { token =>
        logger.debug(s"Got back token $token from $hat")
        val triedJwtToken = JwtToken.parseSigned(token, None)
        val maybeResponse: Try[AuthMessage] = triedJwtToken map { jwtToken =>
          if (jwtToken.valid) {
            Connected(token, jwtToken)
          }
          else {
            logger.warn(s"Token validation for $hat failed")
            Unauthorized("Token validation failed")
          }
        } recover {
          case e =>
            logger.warn(s"Token parsing for $hat failed")
            Unauthorized("Token parsing failed")
        }
        // There will always be a response to send back
        maybeResponse.get
      } recover {
        case e =>
          logger.error(s"Connecting to $hat failed: $e", e)
          Unauthorized(s"Connecting to $hat failed: $e")
      }
      authResponse pipeTo self

    case Connected(token, jwtToken) =>
      logger.debug(s"HAT $hat connected, unstashing and becoming connected")
      maybeToken = Some(token)
      maybeJwtToken = Some(jwtToken)

      val tokenValidDuration = new Duration(DateTime.now, jwtToken.expirationTime.minusMinutes(5))
      val finiteDuration = new FiniteDuration(tokenValidDuration.toStandardMinutes.getMinutes.toLong, MINUTES)
      context.system.scheduler.scheduleOnce(finiteDuration, self, Disconnected)

      unstashAll()
      context.become(connected)

    case Disconnected =>
      logger.debug(s"HAT $hat disconnected, reconnect!")
      self ! Connect

    case Unauthorized(message) =>
      logger.error(s"Client unauthorized for $hat: $message")
      //      unstashAll()
      context.become(unauthenticated)

    case msg: FetchDataDebit =>
      logger.debug(s"Stashing FetchDataDebit message for $hat")
      logger.debug(s"Fetch sender: $sender")
      stash()
      self ! Connect

    case message =>
      logger.debug(s"Received unknown message for $hat: $message")
  }

  def connected: Receive = {
    case FetchDataDebit(ddid) =>
      logger.debug(s"Fetching Data Debit $ddid for $hat")
      val ddValues = hatClient.dataDebitValues(maybeToken.get, ddid) recover {
        case e =>
          logger.error(s"Could not fetch Data Debit $ddid values: ${e.getMessage}", e)
          FetchingFailed(s"Could not fetch Data Debit $ddid values: ${e.getMessage}")
      }
      ddValues pipeTo sender
    case Disconnected =>
      logger.debug(s"HAT $hat connection expired, disconnected becoming normal and reconnecting!")
      self ! Connect
      context.become(receive)
  }

  def unauthenticated: Receive = {
    case FetchDataDebit(ddid) =>
      logger.error(s"Data could not be retrieved - could not authenticate with HAT $hat for Data Debit $ddid")
      sender ! FetchingFailed(s"Data could not be retrieved - could not authenticate with HAT $hat for Data Debit $ddid")
      // Go back to the initial state for error recovery
      context.become(receive)
    case _ =>
      logger.error(s"HAT client is not authenticated to $hat")
      sender ! FetchingFailed(s"HAT client is not authenticated to $hat")
  }

}
