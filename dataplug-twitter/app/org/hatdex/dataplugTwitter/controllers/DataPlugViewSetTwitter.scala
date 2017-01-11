/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 1, 2017
 */

package org.hatdex.dataplugTwitter.controllers

import com.mohiva.play.silhouette.impl.providers.SocialProviderRegistry
import org.hatdex.dataplug.apiInterfaces.models.ApiEndpointVariantChoice
import org.hatdex.dataplug.controllers.DataPlugViewSet
import org.hatdex.dataplug.models.User
import org.hatdex.dataplug.{ views => dataplugViews }
import org.hatdex.dataplugTwitter.{ views => dataplugSocialViews }
import play.api.data.{ Form, Forms }
import play.api.i18n.Messages
import play.api.mvc.{ Call, RequestHeader }
import play.twirl.api.Html

class DataPlugViewSetTwitter extends DataPlugViewSet {
  def connect(
    socialProviderRegistry: SocialProviderRegistry,
    endpointVariants: Option[Seq[ApiEndpointVariantChoice]],
    variantsForm: Form[List[String]])(implicit request: RequestHeader, user: User, messages: Messages): Html = {

    dataplugSocialViews.html.connect(socialProviderRegistry, endpointVariants, variantsForm)
  }

  def signIn(form: Form[String])(implicit request: RequestHeader, messages: Messages): Html =
    dataplugViews.html.signIn(form)

  def indexRedirect: Call =
    org.hatdex.dataplugTwitter.controllers.routes.Application.index()
}
