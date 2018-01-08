/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.utils

import javax.inject.Inject

import org.hatdex.commonPlay
import org.hatdex.commonPlay.utils.MailService
import org.hatdex.dataplug.views
import play.api.UsefulException
import play.api.i18n.Messages
import play.api.mvc.RequestHeader

import scala.util.Try

class Mailer @Inject() (configuration: play.api.Configuration, ms: MailService)
  extends commonPlay.utils.Mailer(configuration, ms) {

  private val plugName = configuration.getString("service.name").getOrElse("MISCONFIGURED")
  private val adminEmails = configuration.getStringSeq("administrators").getOrElse(Seq())

  def serverErrorNotify(request: RequestHeader, exception: UsefulException)(implicit m: Messages): Unit = {
    // wrap any errors
    Try {
      val emailFrom = configuration.getString("play.mailer.from").get
      ms.sendEmailAsync(adminEmails: _*)(
        subject = s"DataPlug $plugName server error: #${exception.getMessage}",
        bodyHtml = views.html.mails.emailServerError(request, exception),
        bodyText = views.html.mails.emailServerError(request, exception).toString())
    }
  }

  def serverExceptionNotify(request: RequestHeader, exception: Throwable)(implicit m: Messages): Unit = {
    // wrap any errors
    Try {
      val emailFrom = configuration.getString("play.mailer.from").get
      ms.sendEmailAsync(adminEmails: _*)(
        subject = s"DataPlug $plugName server error: ${exception.getMessage} for ${request.path + request.rawQueryString}",
        bodyHtml = views.html.mails.emailServerThrowable(request, exception),
        bodyText = views.html.mails.emailServerThrowable(request, exception).toString())
    }
  }

  def serverExceptionNotifyInternal(message: String, exception: Throwable): Unit = {
    // wrap any errors
    Try {
      val emailFrom = configuration.getString("play.mailer.from").get
      ms.sendEmailAsync(adminEmails: _*)(
        subject = s"DataPlug $plugName server error: ${exception.getMessage}",
        bodyHtml = views.html.mails.emailServerThrowableInternal(message, exception),
        bodyText = views.html.mails.emailServerThrowableInternal(message, exception).toString())
    }
  }
}

