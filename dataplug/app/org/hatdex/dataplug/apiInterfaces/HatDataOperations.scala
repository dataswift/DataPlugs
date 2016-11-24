package org.hatdex.dataplug.apiInterfaces

import org.hatdex.commonPlay.utils.Utils
import org.hatdex.hat.api.models.{ ApiDataField, ApiDataRecord, ApiDataTable, ApiDataValue }
import org.joda.time.{ DateTime, LocalDateTime }
import play.api.Logger
import play.api.libs.json.{ JsArray, JsObject, JsValue }

import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

trait HatDataOperations {
  protected val quietTranslationErrors: Boolean
  protected val logger: Logger

  protected def extractRecordTimestamp(content: JsValue): DateTime

  protected def translateDataForHat(content: JsValue, sourceName: String, tableName: String, recordDate: Option[LocalDateTime] = None): ApiDataTable = {
    // FIXME: handle list of objects
    content match {
      case values: JsArray =>
        if (quietTranslationErrors) {
          logger.warn("HAT currently does not support lists of data deep inside a structure")
        }
        else {
          logger.error("HAT currently does not support lists of data deep inside a structure")
          throw new RuntimeException("HAT currently does not support lists of data deep inside a structure")
        }
    }

    val fields = content match {
      case value: JsObject =>
        val fields = value.value collect {
          case (key, content: JsValue) =>
            ApiDataField(None, None, None, None, key, Some(Seq(ApiDataValue(None, None, recordDate, content.toString(), None, None))))
        }
        Utils.seqOption(fields.toSeq)
      case _ =>
        None
    }

    val subtables = content match {
      case value: JsObject =>
        val tables = value.value collect {
          case (key, content: JsObject) =>
            translateDataForHat(content, sourceName, key, recordDate)
        }
        Utils.seqOption(tables.toSeq)
      case _ =>
        None
    }

    ApiDataTable(None, None, None, tableName, sourceName, fields, subtables)
  }

  protected def mapDataToRecord(dataRecord: JsValue, sourceName: String, endpointName: String): ApiDataRecord = {
    val recordTimestamp = Some(extractRecordTimestamp(dataRecord).toLocalDateTime)
    val recordName = s"$sourceName-$endpointName-${extractRecordTimestamp(dataRecord).getMillis}"
    val dataTable = translateDataForHat(dataRecord, sourceName, endpointName, recordTimestamp)
    ApiDataRecord(None, None, recordTimestamp, recordName, Some(Seq(dataTable)))
  }

  protected def buildHatDataRecord(content: JsValue, sourceName: String, endpointName: String): Future[Seq[ApiDataRecord]] = {
    content match {
      case dataArray: JsArray   => Future.successful(dataArray.value.map(mapDataToRecord(_, sourceName, endpointName)))
      case dataRecord: JsObject => Future.successful(Seq(mapDataToRecord(dataRecord, sourceName, endpointName)))
      case _                    => Future.failed(new RuntimeException(s"Could not build HAT Data Record from $content"))
    }
  }
}
