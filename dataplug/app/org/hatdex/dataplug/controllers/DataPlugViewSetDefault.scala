/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.controllers

import com.mohiva.play.silhouette.impl.providers.SocialProviderRegistry
import org.hatdex.dataplug.apiInterfaces.models.ApiEndpointVariantChoice
import org.hatdex.dataplug.models.User
import org.hatdex.dataplug.{ views => dataplugViews }
import play.api.data.Form
import play.api.i18n.Messages
import play.api.mvc.{ Call, RequestHeader }
import play.twirl.api.Html

class DataPlugViewSetDefault extends DataPlugViewSet {
  def connect(
    socialProviderRegistry: SocialProviderRegistry,
    endpointVariants: Option[Seq[ApiEndpointVariantChoice]],
    variantsForm: Form[List[String]])(implicit request: RequestHeader, user: User, messages: Messages): Html = {

    dataplugViews.html.connect(socialProviderRegistry, endpointVariants,
      request.session.get("redirect").getOrElse(defaultRedirect),
      variantsForm)
  }

  def signIn(form: Form[String], errorMessage: Option[String])(implicit request: RequestHeader, messages: Messages): Html =
    dataplugViews.html.signIn(form, errorMessage)

  def indexRedirect: Call =
    org.hatdex.dataplug.controllers.routes.Application.index()

  def signupComplete(
    socialProviderRegistry: SocialProviderRegistry,
    endpointVariants: Option[Seq[ApiEndpointVariantChoice]])(implicit user: User, request: RequestHeader, messages: Messages): Html = {
    dataplugViews.html.complete(socialProviderRegistry, endpointVariants,
      request.session.get("redirect").getOrElse(defaultRedirect))
  }

  def disconnect(
    socialProviderRegistry: SocialProviderRegistry,
    endpointVariants: Option[Seq[ApiEndpointVariantChoice]],
    chooseVariants: Boolean)(implicit user: User, request: RequestHeader, messages: Messages): Html = {
    dataplugViews.html.disconnect(socialProviderRegistry, endpointVariants,
      request.session.get("redirect").getOrElse(defaultRedirect),
      chooseVariants)
  }

  protected def defaultRedirect(implicit user: User) = s"https://${user.userId}/#/dashboard"
}
