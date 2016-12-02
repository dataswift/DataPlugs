package org.hatdex.dataplug.services

import javax.inject.{ Inject, Named }

import akka.actor.ActorRef
import com.mohiva.play.silhouette.api.{ LoginInfo, Provider }
import com.mohiva.play.silhouette.api.services.IdentityService
import com.mohiva.play.silhouette.impl.providers.{ CommonSocialProfile, SocialProvider, SocialProviderRegistry }
import org.hatdex.dataplug.actors.DataPlugManagerActor.{ Start, Stop }
import org.hatdex.dataplug.apiInterfaces.{ DataPlugOptionsCollector, DataPlugOptionsCollectorRegistry }
import org.hatdex.dataplug.apiInterfaces.models.ApiEndpointVariantChoice
import org.hatdex.dataplug.models.User
import play.api.Logger

import scala.concurrent.{ ExecutionContext, Future }

class DataplugSyncerActorManager @Inject() (
    socialProviderRegistry: SocialProviderRegistry,
    dataPlugEndpointService: DataPlugEndpointService,
    optionsCollectionRegistry: DataPlugOptionsCollectorRegistry,
    @Named("dataPlugManager") dataPlugManagerActor: ActorRef) {

  def updateApiVariantChoices(user: User, variantChoices: Seq[ApiEndpointVariantChoice])(implicit ec: ExecutionContext): Future[Unit] = {
    dataPlugEndpointService.updateApiVariantChoices(user.userId, variantChoices) map {
      case _ =>
        variantChoices foreach { variantChoice =>
          if (variantChoice.active) {
            dataPlugManagerActor ! Start(variantChoice.variant, user.userId, variantChoice.variant.configuration)
          }
          else {
            dataPlugManagerActor ! Stop(variantChoice.variant, user.userId)
          }
        }
    }
  }

  def startAllActiveVariantChoices()(implicit ec: ExecutionContext): Future[Unit] = {
    Logger.info("Starting active API endpoint syncing")
    dataPlugEndpointService.retrieveAllEndpoints map { phataVariants =>
      Logger.info(s"Retrieved endpoints to sync: ${phataVariants.mkString("\n")}")
      phataVariants foreach {
        case (phata, variant) =>
          dataPlugManagerActor ! Start(variant, phata, variant.configuration)
      }
    } recoverWith {
      case e =>
        Logger.error(s"Could not retrieve endpoints to sync: ${e.getMessage}")
        Future.failed(e)
    }
  }

  def runPhataActiveVariantChoices(phata: String)(implicit ec: ExecutionContext): Future[Unit] = {
    Logger.info("Starting active API endpoint syncing")
    dataPlugEndpointService.retrievePhataEndpoints(phata) map { phataVariants =>
      Logger.info(s"Retrieved endpoints to sync: ${phataVariants.mkString("\n")}")
      phataVariants foreach {
        case variant =>
          dataPlugManagerActor ! Start(variant, phata, variant.configuration)
      }
    } recoverWith {
      case e =>
        Logger.error(s"Could not retrieve endpoints to sync: ${e.getMessage}")
        Future.failed(e)
    }
  }

  def currentProviderApiVariantChoices(user: User, providerName: String)(implicit executionContext: ExecutionContext): Future[Seq[ApiEndpointVariantChoice]] = {
    val socialProvider = socialProviderRegistry.get[SocialProvider](providerName)

    val optionsCollectors = socialProvider.map { provider =>
      optionsCollectionRegistry.getSeqProvider[provider.type, DataPlugOptionsCollector]
    } getOrElse Seq()

    val optionsLists = optionsCollectors.map { collector =>
      for {
        apiCall <- collector.buildFetchParameters(None)
        choices <- collector.get(apiCall, user.userId, null)
        enabledVariants <- dataPlugEndpointService.enabledApiVariantChoices(user.userId)
      } yield {
        choices.map { choice =>
          choice.copy(
            active = enabledVariants.exists(v => v.variant.variant == choice.variant.variant),
            variant = enabledVariants.find(_.key == choice.key).map(_.variant).getOrElse(choice.variant))
        }
      }
    }

    Future.sequence(optionsLists).map(_.flatten)
  }

}
