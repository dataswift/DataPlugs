/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 11, 2017
 */

package com.hubofallthings.dataplugMonzo.apiInterfaces

import akka.Done
import akka.actor.Scheduler
import akka.util.Timeout
import com.google.inject.Inject
import com.hubofallthings.dataplug.apiInterfaces.DataPlugEndpointInterface
import com.hubofallthings.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import com.hubofallthings.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod, ApiEndpointTableStructure }
import com.hubofallthings.dataplug.services.UserService
import com.hubofallthings.dataplug.utils.{ AuthenticatedHatClient, FutureTransformations, Mailer }
import com.hubofallthings.dataplugMonzo.apiInterfaces.authProviders.MonzoProvider
import com.hubofallthings.dataplugMonzo.models.{ MonzoAttachment, MonzoTransaction }
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class MonzoTransactionsInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: MonzoProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  // JSON type formatters

  val namespace: String = "monzo"
  val endpoint: String = "transactions"
  protected val logger: Logger = Logger(this.getClass)

  protected val apiEndpointTableStructures: Map[String, ApiEndpointTableStructure] = Map(
    "transactions" -> MonzoTransaction,
    "attachments" -> MonzoAttachment)

  val defaultApiEndpoint = MonzoTransactionsInterface.defaultApiEndpoint

  val refreshInterval = 5.minutes

  /**
   * Builds Monzo Paging API continuation - assumes records ordered by time, oldest first,
   * `since` based on transaction ID
   *
   * @param content Response content
   * @param params API endpoint call paramters used to retrieve the data
   * @returns Optional ApiEndpointCall - None if last page of results has been reached
   */
  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    val maybeTransactions = (content \ "transactions").asOpt[JsArray]

    val maybeLastTransactionId = maybeTransactions flatMap { transactions =>
      if (transactions.value.length == MonzoTransactionsInterface.resultsPerPage) {
        None
      }
      else {
        transactions.value.lastOption.flatMap { transaction =>
          (transaction \ "id").asOpt[String]
        }
      }
    }

    maybeLastTransactionId map { lastTransactionId =>
      val updatedParams = params.queryParameters + ("since" -> lastTransactionId)
      params.copy(queryParameters = updatedParams)
    }
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    val maybeTransactions = (content \ "transactions").asOpt[JsArray]

    val maybeLastTransactionId = maybeTransactions flatMap { transactions =>
      transactions.value.lastOption.flatMap { transaction =>
        (transaction \ "id").asOpt[String]
      }
    }

    maybeLastTransactionId map { lastTransactionId =>
      val updatedParams = params.queryParameters + ("since" -> lastTransactionId)
      params.copy(queryParameters = updatedParams)
    } getOrElse {
      params
    }
  }

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClient: AuthenticatedHatClient,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Done] = {

    for {
      transactions <- FutureTransformations.transform(validateMinDataStructure(content))
      _ <- uploadHatData(namespace, endpoint, transactions, hatAddress, hatClient) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
      Done
    }
  }

  override def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    (rawData \ "transactions").toOption.map {
      case data: JsArray if data.validate[List[MonzoTransaction]].isSuccess =>
        logger.debug(s"Validated JSON object:\n${data.toString}")
        Success(data)
      case data: JsArray =>
        logger.error(s"Error validating data, some of the required fields missing:\n${data.toString}")
        Failure(new RuntimeException(s"Error validating data, some of the required fields missing."))
      case _ =>
        logger.error(s"Error parsing JSON object: ${rawData.toString}")
        Failure(new RuntimeException(s"Error parsing JSON object."))
    }.getOrElse {
      logger.error(s"Error parsing JSON object: ${rawData.toString}")
      Failure(new RuntimeException(s"Error parsing JSON object."))
    }
  }
}

object MonzoTransactionsInterface {
  val resultsPerPage = 100
  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.monzo.com",
    "/transactions",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("account_id" -> "account_id", "expand[]" -> "merchant", "limit" -> resultsPerPage.toString),
    Map(),
    Some(Map()))
}
