/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.utils

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

object FutureTransformations {
  def transform[A](o: Option[Future[A]])(implicit ec: ExecutionContext): Future[Option[A]] =
    o.map(f => f.map(Option(_))).getOrElse(Future.successful(None))

  def transform[A](o: Option[Future[Option[A]]]): Future[Option[A]] =
    o.getOrElse(Future.successful(None))

  def transform[A](t: Try[A])(implicit ec: ExecutionContext): Future[A] = {
    Future {
      t
    }.flatMap {
      case Success(s)     => Future.successful(s)
      case Failure(error) => Future.failed(error)
    }
  }
}
