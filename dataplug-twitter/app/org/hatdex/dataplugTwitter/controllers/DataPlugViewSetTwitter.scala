/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 2, 2017
 */

package org.hatdex.dataplugTwitter.controllers

import com.mohiva.play.silhouette.impl.providers.SocialProviderRegistry
import org.hatdex.dataplug.apiInterfaces.models.ApiEndpointVariantChoice
import org.hatdex.dataplug.controllers.DataPlugViewSetDefault
import org.hatdex.dataplug.models.User
import org.hatdex.dataplug.{ views => dataplugViews }
import org.hatdex.dataplugTwitter.{ views => dataplugSocialViews }
import play.api.data.Form
import play.api.i18n.Messages
import play.api.mvc.{ Call, RequestHeader }
import play.twirl.api.Html

class DataPlugViewSetTwitter extends DataPlugViewSetDefault {
  override def connect(
    socialProviderRegistry: SocialProviderRegistry,
    endpointVariants: Option[Seq[ApiEndpointVariantChoice]],
    variantsForm: Form[List[String]])(implicit request: RequestHeader, user: User, messages: Messages): Html = {

    dataplugSocialViews.html.connect(socialProviderRegistry, endpointVariants, variantsForm)
  }

  override def indexRedirect: Call =
    org.hatdex.dataplug.controllers.routes.Application.index()
}
