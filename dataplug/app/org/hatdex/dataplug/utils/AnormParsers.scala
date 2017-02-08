/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.utils

import anorm.{ Column, MetaDataItem, TypeDoesNotMatch }
import play.api.libs.json.{ JsValue, Json }

object AnormParsers {
  implicit def rowToJsValue: Column[JsValue] = Column.nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case pgo: org.postgresql.util.PGobject => Right(Json.parse(pgo.getValue))
      case _                                 => Left(TypeDoesNotMatch("Cannot convert " + value + ":" + value.asInstanceOf[AnyRef].getClass + " to JsValue for column " + qualified))
    }
  }

  implicit def rowToMaybeJsValue: Column[Option[JsValue]] = Column.columnToOption[JsValue]
}
