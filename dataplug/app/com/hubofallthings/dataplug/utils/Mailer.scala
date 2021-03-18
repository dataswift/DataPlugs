/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplug.utils

import com.hubofallthings.dataplug.views
import play.api.i18n.Messages
import play.api.mvc.RequestHeader
import play.api.{Configuration, UsefulException}

import javax.inject.Inject
import scala.util.Try

class Mailer @Inject()(configuration: Configuration, ms: MailService) {

  private val plugName = configuration.getOptional[String]("service.name").getOrElse("MISCONFIGURED")
  private val adminEmails = configuration.getOptional[Seq[String]]("administrators").getOrElse(Seq())

  def serverErrorNotify(request: RequestHeader, exception: UsefulException)(implicit m: Messages): Unit = {
    // wrap any errors
    Try {
      ms.sendEmailAsync(adminEmails: _*)(
        subject = s"DataPlug $plugName server error: #${exception.getMessage}",
        bodyHtml = views.html.mails.emailServerError(request, exception).toString(),
        bodyText = views.html.mails.emailServerError(request, exception).toString())
    }
  }

  def serverExceptionNotify(request: RequestHeader, exception: Throwable)(implicit m: Messages): Unit = {
    // wrap any errors
    Try {
      ms.sendEmailAsync(adminEmails: _*)(
        subject = s"DataPlug $plugName server error: ${exception.getMessage} for ${request.path + request.rawQueryString}",
        bodyHtml = views.html.mails.emailServerThrowable(request, exception).toString(),
        bodyText = views.html.mails.emailServerThrowable(request, exception).toString())
    }
  }

  def serverExceptionNotifyInternal(message: String, exception: Throwable): Unit = {
    // wrap any errors
    Try {
      ms.sendEmailAsync(adminEmails: _*)(
        subject = s"DataPlug $plugName server error: ${exception.getMessage}",
        bodyHtml = views.html.mails.emailServerThrowableInternal(message, exception).toString(),
        bodyText = views.html.mails.emailServerThrowableInternal(message, exception).toString())
    }
  }
}

