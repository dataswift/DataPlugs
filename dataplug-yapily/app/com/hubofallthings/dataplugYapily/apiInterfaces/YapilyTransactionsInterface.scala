/*
 * Copyright (C) 2020 Dataswift Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Marios Tsekis <marios.tsekis@dataswift.io>, 3 2020
 */

package com.hubofallthings.dataplugYapily.apiInterfaces

import akka.Done
import akka.actor.Scheduler
import akka.util.Timeout
import com.google.inject.Inject
import com.hubofallthings.dataplug.actors.Errors.SourceDataProcessingException
import com.hubofallthings.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import com.hubofallthings.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import com.hubofallthings.dataplug.services.UserService
import com.hubofallthings.dataplug.utils.{ AuthenticatedHatClient, FutureTransformations, Mailer }
import com.hubofallthings.dataplugYapily.apiInterfaces.authProviders.YapilyProvider
import com.hubofallthings.dataplugYapily.models.YapilyTransactions
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import play.api.Logger
import play.api.libs.json.{ JsArray, JsObject, JsValue }
import play.api.libs.ws.WSClient

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

class YapilyTransactionsInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: YapilyProvider) extends YapilyEndpointInterface(provider) with RequestAuthenticatorOAuth2 {

  val namespace: String = "yapily"
  val endpoint: String = "transactions"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint: ApiEndpointCall = YapilyTransactionsInterface.defaultApiEndpoint

  val refreshInterval: FiniteDuration = 1.day

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    logger.debug("Building continuation")
    val count = (content \ "meta" \ "count").asOpt[Int].getOrElse(0)
    val totalCount = (content \ "meta" \ "pagination" \ "totalCount").asOpt[Int].getOrElse(0)
    val offset = (content \ "meta" \ "pagination" \ "self" \ "offset").asOpt[Int].getOrElse(0)

    if (count + offset >= totalCount) {
      None
    }
    else {
      Some(params.copy(queryParameters = params.queryParameters + ("offset" -> (offset + count).toString)))
    }
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    logger.debug("Building next sync")
    val maybeTransaction = (content \ "data").asOpt[Seq[YapilyTransactions]].map(t => t.head)

    maybeTransaction.map { transaction =>
      transaction.date.map(_.toString).map { from =>
        val updatedParams = params.queryParameters + ("from" -> from) - "offset"
        params.copy(queryParameters = updatedParams)
      }.getOrElse(params)
    } getOrElse {
      params
    }
  }

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClient: AuthenticatedHatClient,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Done] = {

    logger.debug("processing results")

    val validatedData: Try[JsArray] = validateMinDataStructure(content)

    // Shape results into HAT data records
    val resultsPosted = for {
      validatedData <- FutureTransformations.transform(validatedData) // Parse calendar events into strongly-typed structures
      _ <- uploadHatData(namespace, endpoint, validatedData, hatAddress, hatClient) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
      Done
    }

    resultsPosted
  }

  override def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    (rawData \ "data").toOption.map {
      case data: JsArray if data.validate[Seq[YapilyTransactions]].isSuccess =>
        logger.debug(s"Validated JSON object: ${data.value.length}")
        Success(data)
      case data: JsObject =>
        logger.error(s"Error validating data, some of the required fields missing: ${data.toString}")
        Failure(SourceDataProcessingException(s"Error validating data, some of the required fields missing."))
      case data =>
        logger.error(s"Error parsing JSON object: ${data.toString} ${data.validate[Seq[YapilyTransactions]]}")
        Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }.getOrElse {
      logger.error(s"Error parsing JSON object: ${rawData.toString}")
      Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }
  }
}

object YapilyTransactionsInterface {
  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.yapily.com",
    "/accounts/[accountId]/transactions",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("limit" -> "1000"),
    Map(),
    Some(Map()))
}
