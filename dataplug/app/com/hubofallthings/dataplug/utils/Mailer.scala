/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplug.utils

import javax.inject.Inject

import com.hubofallthings.dataplug.views
import play.api.Configuration
import play.api.UsefulException
import play.api.i18n.Messages
import play.api.mvc.RequestHeader
import play.twirl.api.Html

import scala.util.Try

class Mailer @Inject() (configuration: Configuration, ms: MailService) {
  import scala.language.implicitConversions

  implicit def html2String(html: Html): String = html.toString

  private val plugName = configuration.getOptional[String]("service.name").getOrElse("MISCONFIGURED")
  private val adminEmails = configuration.getOptional[Seq[String]]("administrators").getOrElse(Seq())

  def serverErrorNotify(request: RequestHeader, exception: UsefulException)(implicit m: Messages): Unit = {
    // wrap any errors
    Try {
      val emailFrom = configuration.get[String]("play.mailer.from")
      ms.sendEmailAsync(adminEmails: _*)(
        subject = s"DataPlug $plugName server error: #${exception.getMessage}",
        bodyHtml = views.html.mails.emailServerError(request, exception),
        bodyText = views.html.mails.emailServerError(request, exception).toString())
    }
  }

  def serverExceptionNotify(request: RequestHeader, exception: Throwable)(implicit m: Messages): Unit = {
    // wrap any errors
    Try {
      val emailFrom = configuration.get[String]("play.mailer.from")
      ms.sendEmailAsync(adminEmails: _*)(
        subject = s"DataPlug $plugName server error: ${exception.getMessage} for ${request.path + request.rawQueryString}",
        bodyHtml = views.html.mails.emailServerThrowable(request, exception),
        bodyText = views.html.mails.emailServerThrowable(request, exception).toString())
    }
  }

  def serverExceptionNotifyInternal(message: String, exception: Throwable): Unit = {
    // wrap any errors
    Try {
      val emailFrom = configuration.get[String]("play.mailer.from")
      ms.sendEmailAsync(adminEmails: _*)(
        subject = s"DataPlug $plugName server error: ${exception.getMessage}",
        bodyHtml = views.html.mails.emailServerThrowableInternal(message, exception),
        bodyText = views.html.mails.emailServerThrowableInternal(message, exception).toString())
    }
  }
}

