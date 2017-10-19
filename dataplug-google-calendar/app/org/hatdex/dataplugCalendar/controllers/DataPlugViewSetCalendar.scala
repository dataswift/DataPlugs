package org.hatdex.dataplugCalendar.controllers

import com.mohiva.play.silhouette.impl.providers.SocialProviderRegistry
import org.hatdex.dataplug.apiInterfaces.models.ApiEndpointVariantChoice
import org.hatdex.dataplug.controllers.{ DataPlugViewSet, DataPlugViewSetDefault }
import org.hatdex.dataplug.models.User
import org.hatdex.dataplug.{ views => dataplugViews }
import org.hatdex.dataplugCalendar.{ views => dataplugCalendarViews }
import play.api.data.{ Form, Forms }
import play.api.i18n.Messages
import play.api.mvc.{ Call, RequestHeader }
import play.twirl.api.Html

class DataPlugViewSetCalendar extends DataPlugViewSetDefault {
  override def connect(
    socialProviderRegistry: SocialProviderRegistry,
    endpointVariants: Option[Seq[ApiEndpointVariantChoice]],
    variantsForm: Form[List[String]])(implicit request: RequestHeader, user: User, messages: Messages): Html = {

    dataplugCalendarViews.html.connect(socialProviderRegistry, endpointVariants, variantsForm)
  }

  override def indexRedirect: Call =
    org.hatdex.dataplug.controllers.routes.Application.index()
}
