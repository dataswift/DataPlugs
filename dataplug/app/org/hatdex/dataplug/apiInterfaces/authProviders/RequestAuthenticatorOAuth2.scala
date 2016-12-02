package org.hatdex.dataplug.apiInterfaces.authProviders

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.{ OAuth2Info, OAuth2Provider }
import org.hatdex.dataplug.apiInterfaces.models.ApiEndpointCall
import org.hatdex.dataplug.services.UserService
import play.api.Logger

import scala.concurrent.{ ExecutionContext, Future }

trait RequestAuthenticatorOAuth2 extends RequestAuthenticator {
  type AuthInfoType = OAuth2Info

  protected val userService: UserService
  protected val authInfoRepository: AuthInfoRepository
  protected val tokenHelper: OAuth2TokenHelper
  protected val provider: OAuth2Provider
  protected val logger: Logger

  def authenticateRequest(params: ApiEndpointCall, hatAddress: String)(implicit ec: ExecutionContext): Future[ApiEndpointCall] = {
    val eventualUser = userService.retrieve(LoginInfo("hatlogin", hatAddress)).map(_.get)
    val existingAuthInfo = eventualUser flatMap { user =>
      val providerLoginInfo = user.linkedUsers.find(_.providerId == provider.id).get
      authInfoRepository.find[AuthInfoType](providerLoginInfo.loginInfo).map(li => (providerLoginInfo, li.get))
    }

    val eventualAuthInfo = existingAuthInfo flatMap {
      case (providerUser, authInfo) =>
        // Refresh token if refreshToken is available, otherwise try using what we have
        authInfo.refreshToken flatMap { refreshToken =>
          logger.debug("Got refresh token, refreshing")
          tokenHelper.refresh(providerUser.loginInfo, refreshToken)
        } getOrElse {
          logger.debug("No refresh token, trying what we have")
          Future.successful(authInfo)
        }
    }

    eventualAuthInfo map { authInfo =>
      params.copy(headers = Map("Authorization" -> s"Bearer ${authInfo.accessToken}"))
    }
  }
}

