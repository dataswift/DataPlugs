package org.hatdex.dataplugFitbit.controllers

import com.mohiva.play.silhouette.impl.providers.SocialProviderRegistry
import org.hatdex.dataplug.apiInterfaces.models.ApiEndpointVariantChoice
import org.hatdex.dataplug.controllers.DataPlugViewSet
import org.hatdex.dataplug.models.User
import org.hatdex.dataplug.{ views => dataplugViews }
import org.hatdex.dataplugFitbit.{ views => fitbitViews }
import play.api.data.Form
import play.api.i18n.Messages
import play.api.mvc.{ Call, RequestHeader }
import play.twirl.api.Html

class DataPlugViewSetFitbit extends DataPlugViewSet {
  def connect(
    socialProviderRegistry: SocialProviderRegistry,
    endpointVariants: Option[Seq[ApiEndpointVariantChoice]],
    variantsForm: Form[List[String]])(implicit request: RequestHeader, user: User, messages: Messages): Html = {

    fitbitViews.html.connect(socialProviderRegistry, endpointVariants, variantsForm)
  }

  def signIn(form: Form[String])(implicit request: RequestHeader, messages: Messages): Html =
    dataplugViews.html.signIn(form)

  def indexRedirect: Call =
    org.hatdex.dataplugFitbit.controllers.routes.Application.index()
}
