/*
 * Copyright (C) HAT Data Exchange Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
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

  def serverErrorNotify(request: RequestHeader, exception: UsefulException)(implicit m: Messages): Unit = {
    // wrap any errors
    Try {
      val emailFrom = configuration.getString("play.mailer.from").get
      val adminEmails = configuration.getStringSeq("administrators").getOrElse(Seq())
      ms.sendEmailAsync(adminEmails: _*)(
        subject = s"MarketSquare Production server errorr #${exception.id}",
        bodyHtml = views.html.mails.emailServerError(request, exception),
        bodyText = views.html.mails.emailServerError(request, exception).toString())
    }
  }

  def serverExceptionNotify(request: RequestHeader, exception: Throwable)(implicit m: Messages): Unit = {
    // wrap any errors
    Try {
      val emailFrom = configuration.getString("play.mailer.from").get
      val adminEmails = configuration.getStringSeq("administrators").getOrElse(Seq())
      ms.sendEmailAsync(adminEmails: _*)(
        subject = s"MarketSquare Production server error: ${exception.getMessage} for ${request.path + request.rawQueryString}",
        bodyHtml = views.html.mails.emailServerThrowable(request, exception),
        bodyText = views.html.mails.emailServerThrowable(request, exception).toString())
    }
  }
}

