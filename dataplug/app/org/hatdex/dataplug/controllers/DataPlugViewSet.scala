package org.hatdex.dataplug.controllers

import com.mohiva.play.silhouette.impl.providers.SocialProviderRegistry
import org.hatdex.dataplug.apiInterfaces.models.ApiEndpointVariantChoice
import org.hatdex.dataplug.models.User
import play.api.data.{ Form, Forms }
import play.api.i18n.Messages
import play.api.mvc.{ Call, RequestHeader }
import play.twirl.api.Html

trait DataPlugViewSet {
  val variantsForm = Form("endpointVariants" -> Forms.list(Forms.text))

  def connect(
    socialProviderRegistry: SocialProviderRegistry,
    endpointVariants: Option[Seq[ApiEndpointVariantChoice]],
    variantsForm: Form[List[String]])(implicit request: RequestHeader, user: User, messages: Messages): Html

  def signIn(form: Form[String])(implicit request: RequestHeader, messages: Messages): Html

  def indexRedirect: Call
}
