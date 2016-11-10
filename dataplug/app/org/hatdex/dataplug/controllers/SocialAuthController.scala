package org.hatdex.dataplug.controllers

import javax.inject.Inject

import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.exceptions.ProviderException
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util.Clock
import com.mohiva.play.silhouette.impl.providers._
import org.hatdex.commonPlay.utils.MailService
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.{ PhataAuthenticationEnvironment, SilhouettePhataAuthenticationController }
import play.api.i18n.{ I18nSupport, Messages, MessagesApi }
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Action

import scala.concurrent.Future

/**
 * The social auth controller.
 *
 * @param messagesApi The Play messages API.
 * @param silhouette The Silhouette stack.
 * @param userService The user service implementation.
 * @param authInfoRepository The auth info service implementation.
 * @param socialProviderRegistry The social provider registry.
 * @param webJarAssets The webjar assets implementation.
 */
class SocialAuthController @Inject() (
    val messagesApi: MessagesApi,
    configuration: play.api.Configuration,
    silhouette: Silhouette[PhataAuthenticationEnvironment],
    userService: UserService,
    authInfoRepository: AuthInfoRepository,
    socialProviderRegistry: SocialProviderRegistry,
    clock: Clock) extends SilhouettePhataAuthenticationController(silhouette, clock, configuration) with I18nSupport with Logger {

  /**
   * Authenticates a user against a social provider.
   *
   * @param provider The ID of the provider to authenticate against.
   * @return The result to display.
   */
  def authenticate(provider: String) = SecuredAction.async { implicit request =>
    (socialProviderRegistry.get[SocialProvider](provider) match {
      case Some(p: SocialProvider with CommonSocialProfileBuilder) =>
        p.authenticate().flatMap {
          case Left(result) => Future.successful(result)
          case Right(authInfo) => for {
            profile <- p.retrieveProfile(authInfo)
            socialUser <- userService.save(profile)
            _ <- userService.link(request.identity, socialUser)
            authInfo <- authInfoRepository.save(profile.loginInfo, authInfo)
            //            authenticator <- silhouette.env.authenticatorService.create(profile.loginInfo)
            //            value <- silhouette.env.authenticatorService.init(authenticator)
            //            result <- silhouette.env.authenticatorService.embed(value, Redirect(routes.Application.index()))
            result <- Future.successful(Redirect(routes.Application.index()))
          } yield {
            silhouette.env.eventBus.publish(LoginEvent(socialUser, request))
            result
          }
        }
      case _ => Future.failed(new ProviderException(s"Cannot authenticate with unexpected social provider $provider"))
    }).recover {
      case e: ProviderException =>
        logger.error("Unexpected provider error", e)
        Redirect(routes.Application.index()).flashing("error" -> Messages("could.not.authenticate"))
    }
  }
}
