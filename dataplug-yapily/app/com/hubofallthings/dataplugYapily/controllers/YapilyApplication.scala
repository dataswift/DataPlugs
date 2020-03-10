/*
 * Copyright (C) 2020 Dataswift Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Marios Tsekis <marios.tsekis@dataswift.io>, 3 2020
 */

package com.hubofallthings.dataplugYapily.controllers

import akka.Done
import com.hubofallthings.dataplug.controllers.DataPlugViewSet
import com.hubofallthings.dataplug.controllers.Application
import com.hubofallthings.dataplug.services.{ DataPlugEndpointService, DataplugSyncerActorManager }
import com.hubofallthings.dataplug.utils.PhataAuthenticationEnvironment
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.util.Clock
import com.mohiva.play.silhouette.impl.providers.SocialProviderRegistry
import com.typesafe.config.ConfigFactory
import javax.inject.Inject
import play.api.libs.json.Json
import play.api.libs.ws.{ WSAuthScheme, WSClient }
import play.api.mvc.{ Action, AnyContent, ControllerComponents }

import scala.concurrent.Future

class YapilyApplication @Inject() (
    wsClient: WSClient,
    components: ControllerComponents,
    configuration: play.api.Configuration,
    socialProviderRegistry: SocialProviderRegistry,
    silhouette: Silhouette[PhataAuthenticationEnvironment],
    dataPlugViewSet: DataPlugViewSet,
    dataPlugEndpointService: DataPlugEndpointService,
    syncerActorManager: DataplugSyncerActorManager,
    clock: Clock)
  extends Application(components, configuration, socialProviderRegistry, silhouette, dataPlugViewSet, dataPlugEndpointService, syncerActorManager, clock) {

  override def index(): Action[AnyContent] = SecuredAction.async { implicit request =>
    val result = if (request.identity.linkedUsers.exists(_.providerId == provider)) {
      logger.info(s"Data provider already connected, proceeding with data synchronization")
      // If the required social profile is connected, proceed with syncing signup
      for {
        variantChoices <- syncerActorManager.currentProviderApiVariantChoices(request.identity, provider)(ioEC)
        apiEndpointStatuses <- dataPlugEndpointService.listCurrentEndpointStatuses(request.identity.userId)
        result <- if (chooseVariants) {
          Future.successful(handleUserWithChoice(variantChoices, apiEndpointStatuses))
        }
        else {
          handleUserFullSelection(variantChoices, apiEndpointStatuses)
        }
      } yield result
    }
    else {
      logger.info(s"Data provider is not setup, redirecting to authentication process")
      createUser()
      Future.successful(Redirect(com.hubofallthings.dataplug.controllers.routes.SocialAuthController.authenticate(provider)))
    }

    result
      .recover {
        case e =>
          // Assume that if any error has happened, it may be fixed by re-authenticating with the provider
          logger.error(s"Error occurred: ${e.getMessage}. Redirecting to $provider OAuth service.", e)
          Redirect(com.hubofallthings.dataplug.controllers.routes.SocialAuthController.authenticate(provider))
      }
  }

  private def createUser(): Future[Done] = {
    logger.debug(s"Creating user")
    val config = ConfigFactory.load()
    val applicationId = config.getString("silhouette.yapily.clientID")
    val applicationSecret = config.getString("silhouette.yapily.clientSecret")
    val body = Json.obj(
      "applicationUserId" -> "4ae2024a-f1c2-4caf-9bb4-78f33eaaddba",
      "referenceId" -> "test")
    wsClient.url("https://api.yapily.com/users").addHttpHeaders(("Content-Type", "application/json"))
      .withAuth(applicationId, applicationSecret, WSAuthScheme.BASIC).post(body).map { _ =>
        Done
      }
  }
}
