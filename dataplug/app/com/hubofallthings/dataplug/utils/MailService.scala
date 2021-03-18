/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplug.utils

import akka.actor.ActorSystem
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService
import com.amazonaws.services.simpleemail.model._
import com.google.inject.ImplementedBy
import play.api.{ Configuration, Logger }

import java.nio.charset.StandardCharsets
import javax.inject.Inject
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

@ImplementedBy(classOf[MailServiceImpl])
trait MailService {
  def sendEmailAsync(recipients: String*)(subject: String, bodyHtml: String, bodyText: String): Unit

  def sendEmail(recipients: String*)(subject: String, bodyHtml: String, bodyText: String): Unit
}

class MailServiceImpl @Inject() (system: ActorSystem, mailerClient: AmazonSimpleEmailService, conf: Configuration)(implicit ec: ExecutionContext) extends MailService {

  private val logger = Logger(getClass)
  private val from = conf.get[String]("mailer.from")
  private val mock = conf.get[Boolean]("mailer.mock")

  def sendEmailAsync(recipients: String*)(subject: String, bodyHtml: String, bodyText: String): Unit = {
    system.scheduler.scheduleOnce(100.milliseconds) {
      sendEmail(recipients: _*)(subject, bodyHtml, bodyText)
    }
  }

  def sendEmail(recipients: String*)(subject: String, bodyHtml: String, bodyText: String): Unit = {
    if (mock) logger.info(s"not sending email about '$subject' to ${recipients.mkString(", ")}")
    else {
      val message = new Message(content(subject), new Body(content(bodyText)).withHtml(content(bodyHtml)))
      val request = new SendEmailRequest(from, new Destination(recipients.asJava), message)
      mailerClient.sendEmail(request)
    }
  }

  private def content(s: String): Content =
    new Content(s).withCharset(StandardCharsets.UTF_8.name())

}