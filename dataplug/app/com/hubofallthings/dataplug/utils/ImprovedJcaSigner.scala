/*
 * Copyright (C) 2016-2020 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Marios Tsekis <marios.tsekis@dataswift.io>, 4 2020
 */

package com.hubofallthings.dataplug.utils

import com.mohiva.play.silhouette.api.exceptions.CryptoException
import com.mohiva.play.silhouette.crypto.JcaSigner.{ BadSignature, InvalidMessageFormat, UnknownVersion }
import com.mohiva.play.silhouette.crypto.{ JcaSigner, JcaSignerSettings }
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.apache.commons.codec.binary.Hex
import play.api.Logger

import scala.util.{ Failure, Success, Try }

class ImprovedJcaSigner(settings: JcaSignerSettings) extends JcaSigner(settings) {
  protected val logger: Logger = Logger(this.getClass)

  override def extract(message: String): Try[String] = {
    logger.debug(s"Message is $message")
    for {
      (_, actualSignature, actualData) <- fragment(message)
      (_, expectedSignature, _) <- fragment(sign2(actualData))
    } yield {
      logger.debug(s"Actual signature: $actualSignature")
      logger.debug(s"Expect signature: $expectedSignature")
      logger.debug(s"Same signatures?: ${expectedSignature == actualSignature}")
      if (constantTimeEquals(expectedSignature, actualSignature)) {
        actualData
      }
      else {
        throw new CryptoException(BadSignature)
      }
    }
  }

  /**
   * Signs (MAC) the given data using the given secret key.
   *
   * @param data The data to sign.
   * @return A message authentication code.
   */
  def sign2(data: String): String = {
    val message = settings.pepper + data + settings.pepper
    logger.debug(s"Pepper + message + pepper: ${message}")
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(new SecretKeySpec(settings.key.getBytes("UTF-8"), "HmacSHA1"))
    val signature = Hex.encodeHexString(mac.doFinal(message.getBytes("UTF-8")))
    val version = 1
    logger.debug(s"$version-$signature-$data")
    s"$version-$signature-$data"
  }

  /**
   * Fragments the message into its parts.
   *
   * @param message The message to fragment.
   * @return The message parts.
   */
  private def fragment(message: String): Try[(String, String, String)] = {
    message.split("-", 3) match {
      case Array(version, signature, data) if version == "1" => Success((version, signature, data))
      case Array(version, _, _) => Failure(new CryptoException(UnknownVersion.format(version)))
      case _ => Failure(new CryptoException(InvalidMessageFormat))
    }
  }

  /**
   * Constant time equals method.
   *
   * Given a length that both Strings are equal to, this method will always run in constant time. This prevents
   * timing attacks.
   */
  private def constantTimeEquals(a: String, b: String): Boolean = {
    if (a.length != b.length) {
      logger.debug(s"Signatures are not equal length")
      false
    }
    else {
      logger.debug(s"Signatures have equal length")
      var equal = 0
      for (i <- 0 until a.length) {
        equal |= a(i) ^ b(i)
      }
      equal == 0
    }
  }
}
