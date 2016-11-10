/*
 * Copyright (C) HAT Data Exchange Ltd - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.utils

import java.io.StringReader
import java.security.Security
import java.security.interfaces.RSAPublicKey
import javax.inject.Inject

import com.nimbusds.jose.JWSVerifier
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.SignedJWT
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.hatdex.hat.api.services.HatClient
import org.hatdex.dataplug.models.User
import org.hatdex.dataplug.services.UserService
import org.joda.time.DateTime
import play.api.Logger
import play.api.cache.{ CacheApi, NamedCache }
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.Future
import scala.util.Try

class JwtPhataAuthenticatedRequest[A](val identity: User, val request: Request[A])
  extends WrappedRequest[A](request)

class JwtPhataAuthenticatedAction @Inject() (
    wSClient: WSClient,
    @NamedCache("session-cache") cache: CacheApi,
    configuration: play.api.Configuration,
    userService: UserService) extends ActionBuilder[JwtPhataAuthenticatedRequest] {

  val logger = Logger("JwtPhataAuthentication")
  def invokeBlock[A](request: Request[A], block: (JwtPhataAuthenticatedRequest[A]) => Future[Result]): Future[Result] = {
    request.headers.get("X-Auth-Token")
      .orElse(request.getQueryString("X-Auth-Token"))
      .orElse(request.getQueryString("token"))
      .map(validateJwtToken)
      .map { eventualMaybeUser =>
        eventualMaybeUser.flatMap { maybeIdentity =>
          maybeIdentity map { identity =>
            userService.save(identity)
              .flatMap(_ => userService.retrieve(identity.loginInfo).map(_.get)) // must have a user when we've just inserted one
              .flatMap(saved => block(new JwtPhataAuthenticatedRequest(saved, request)))
          } getOrElse {
            Future.successful(Results.Unauthorized)
          }

        } recover {
          case e =>
            logger.error(s"Error while authenticating: ${e.getMessage}")
            Results.Unauthorized(Json.obj("status" -> "unauthorized", "message" -> s"No valid login information"))
        }
      }
      .getOrElse(Future.successful(Results.Unauthorized))
  }

  def validateJwtToken(token: String): Future[Option[User]] = {
    val expectedSubject = "hat"
    val expectedResources = configuration.getStringSeq("auth.allowedResources").get
    val expectedAccessCope = "validate"
    val maybeSignedJWT = Try(SignedJWT.parse(token))

    maybeSignedJWT.map { signedJWT =>
      val claimSet = signedJWT.getJWTClaimsSet
      val fresh = claimSet.getExpirationTime.after(DateTime.now().toDate)
      val subjectMatches = claimSet.getSubject == expectedSubject
      val resourceMatches = Option(claimSet.getClaim("resource").asInstanceOf[String]).map { resource =>
        expectedResources.exists(er => resource.startsWith(er))
      } getOrElse {
        false
      }
      val accessScopeMatches = Option(claimSet.getClaim("accessScope")).contains(expectedAccessCope)

      if (fresh && subjectMatches && resourceMatches && accessScopeMatches) {
        val identity = User("hatlogin", claimSet.getIssuer, List())
        verifiedIdentityCached(identity, signedJWT)
      }
      else {
        logger.debug(s"JWT token validation failed: fresh - ${fresh}, subject - ${subjectMatches}, resource - ${resourceMatches}, scope - ${accessScopeMatches}")
        Future(None)
      }
    } getOrElse {
      // JWT parse error
      Future(None)
    }
  }

  private def verifiedIdentityCached(identity: User, signedJWT: SignedJWT): Future[Option[User]] = {
    val maybeCachedPublicKey = cache.get(identity.userId).map { publicKey: RSAPublicKey =>
      Future.successful(Some(publicKey))
    }

    val eventuallySomePublicKey = maybeCachedPublicKey getOrElse {
      val hatClient = new HatClient(wSClient, identity.userId)
      hatClient.retrievePublicKey()
      val eventualHatPublicKey = for {
        publicKeyString <- hatClient.retrievePublicKey()
        publicKey <- readPublicKey(publicKeyString)
      } yield {
        cache.set(identity.userId, publicKey)
        Some(publicKey)
      }
      eventualHatPublicKey
    }

    val eventualPublicKey = eventuallySomePublicKey recover {
      case e =>
        logger.error(s"Error retrieving public key for $identity: ${e.getMessage}")
        None
    }

    val maybeIdentity = for {
      publicKey <- eventualPublicKey.map(_.get)
    } yield {
      val verifier: JWSVerifier = new RSASSAVerifier(publicKey)
      val verified = signedJWT.verify(verifier)
      if (verified) {
        logger.debug("JWT token signature verified")
        Some(identity)
      }
      else {
        logger.debug(s"JWT token signature failed for ${publicKey.toString}")
        None
      }
    }

    maybeIdentity recover {
      case e =>
        logger.error(s"Error while finding identity: ${e.getMessage}")
        throw e
    }

  }

  Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())
  def readPublicKey(publicKey: String): Future[RSAPublicKey] = {
    Future {
      val reader = new PEMParser(new StringReader(publicKey))
      val temp: SubjectPublicKeyInfo = reader.readObject().asInstanceOf[SubjectPublicKeyInfo]
      val converter = new JcaPEMKeyConverter()
      converter.getPublicKey(temp).asInstanceOf[RSAPublicKey]
    }
  }
}

class JwtPhataAwareRequest[A](val maybeUser: Option[User], val request: Request[A])
  extends WrappedRequest[A](request)

class JwtPhataAwareAction @Inject() (
    wSClient: WSClient,
    @NamedCache("session-cache") cache: CacheApi,
    configuration: play.api.Configuration,
    userService: UserService,
    jwtAuthenticatedAction: JwtPhataAuthenticatedAction) extends ActionBuilder[JwtPhataAwareRequest] {

  val logger = Logger("JwtPhataAuthentication")

  def invokeBlock[A](request: Request[A], block: (JwtPhataAwareRequest[A]) => Future[Result]): Future[Result] = {
    request.headers.get("X-Auth-Token")
      .orElse(request.getQueryString("X-Auth-Token"))
      .orElse(request.getQueryString("token"))
      .map(jwtAuthenticatedAction.validateJwtToken)
      .map { eventualMaybeUser =>
        eventualMaybeUser.flatMap { maybe =>
          logger.debug(s"User auth checked, got back user ${maybe}")
          val eventuallySavedUser = maybe map { identity =>
            userService.save(identity)
              .flatMap(_ => userService.retrieve(identity.loginInfo)) // must have a user when we've just inserted one
          } getOrElse {
            Future.successful(None)
          }
          eventuallySavedUser flatMap { maybeUser =>
            block(new JwtPhataAwareRequest(maybeUser, request))
          }
        } recoverWith {
          case e =>
            logger.error(s"Error while authenticating: ${e.getMessage}")
            block(new JwtPhataAwareRequest(None, request))
        }
      }
      .getOrElse(block(new JwtPhataAwareRequest(None, request)))
  }
}
