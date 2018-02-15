/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.utils

import javax.inject.Inject

import akka.actor.ActorSystem
import com.google.inject.ImplementedBy
import play.api.Configuration
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.mailer.{ Email, MailerClient }

import scala.concurrent.duration._

@ImplementedBy(classOf[MailServiceImpl])
trait MailService {
  def sendEmailAsync(recipients: String*)(subject: String, bodyHtml: String, bodyText: String): Unit
  def sendEmail(recipients: String*)(subject: String, bodyHtml: String, bodyText: String): Unit
}

class MailServiceImpl @Inject() (system: ActorSystem, mailerClient: MailerClient, val conf: Configuration) extends MailService {
  lazy val from = conf.get[String]("play.mailer.from")

  def sendEmailAsync(recipients: String*)(subject: String, bodyHtml: String, bodyText: String): Unit = {
    system.scheduler.scheduleOnce(100.milliseconds) {
      sendEmail(recipients: _*)(subject, bodyHtml, bodyText)
    }
  }

  def sendEmail(recipients: String*)(subject: String, bodyHtml: String, bodyText: String): Unit =
    mailerClient.send(Email(subject, from, recipients, Some(bodyText), Some(bodyHtml)))
}