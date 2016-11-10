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
