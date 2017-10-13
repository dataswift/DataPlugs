package org.hatdex.dataplugMonzo.controllers

import com.mohiva.play.silhouette.impl.providers.SocialProviderRegistry
import org.hatdex.dataplug.apiInterfaces.models.ApiEndpointVariantChoice
import org.hatdex.dataplug.controllers.DataPlugViewSetDefault
import org.hatdex.dataplug.models.User
import org.hatdex.dataplugMonzo.{ views => dataplugSocialViews }
import play.api.data.Form
import play.api.i18n.Messages
import play.api.mvc.{ Call, RequestHeader }
import play.twirl.api.Html

class DataPlugViewSetMonzo extends DataPlugViewSetDefault {
  override def connect(
    socialProviderRegistry: SocialProviderRegistry,
    endpointVariants: Option[Seq[ApiEndpointVariantChoice]],
    variantsForm: Form[List[String]])(implicit request: RequestHeader, user: User, messages: Messages): Html = {

    dataplugSocialViews.html.connect(socialProviderRegistry, endpointVariants, variantsForm)
  }

  override def indexRedirect: Call =
    org.hatdex.dataplug.controllers.routes.Application.index()
}
