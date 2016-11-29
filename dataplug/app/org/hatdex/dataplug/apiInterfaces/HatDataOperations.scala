package org.hatdex.dataplug.apiInterfaces

import org.hatdex.commonPlay.utils.Utils
import org.hatdex.hat.api.models.{ ApiDataField, ApiDataRecord, ApiDataTable, ApiDataValue }
import org.joda.time.{ DateTime, LocalDateTime }
import play.api.Logger
import play.api.libs.json._

import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

trait HatDataOperations {
  protected val logger: Logger

  protected def extractRecordTimestamp(content: JsValue): DateTime

  protected def buildHATDataTableStructure(content: JsValue, sourceName: String, tableName: String): Try[ApiDataTable] = {
    val verifiedValue: Try[JsValue] = content match {
      case values: JsArray =>
        val error = "HAT currently does not support lists of data deep inside a structure"
        logger.error(error)
        Failure(new RuntimeException(error))
      case value =>
        Success(value)
    }

    val subtables = verifiedValue flatMap {
      case value: JsObject =>
        val tables = value.value collect {
          case (key, content: JsObject) =>
            buildHATDataTableStructure(content, sourceName, key)
        }

        Utils.flatten(tables.toSeq) // Flatten Sequence of Tries to Try of Sequence
          .map(Utils.seqOption) // Wrap in Option depending if sequence is empty
      case _ =>
        Success(None)
    }

    val fields = verifiedValue map {
      case value: JsObject =>
        val fields = value.value collect {
          case (key, content: JsBoolean) => ApiDataField(None, None, None, None, key, None)
          case (key, content: JsString)  => ApiDataField(None, None, None, None, key, None)
          case (key, content: JsNumber)  => ApiDataField(None, None, None, None, key, None)
        }
        Utils.seqOption(fields.toSeq)
      case _ =>
        None
    }

    for {
      subtables <- subtables
      fields <- fields
    } yield ApiDataTable(None, None, None, tableName, sourceName, fields, subtables)
  }

  protected def translateDataForHat(content: JsValue, sourceName: String, tableName: String,
    recordDate: Option[LocalDateTime] = None, tableStructure: ApiDataTable): Try[ApiDataTable] = {
    val verifiedValue: Try[JsValue] = content match {
      case values: JsArray =>
        val error = "HAT currently does not support lists of data deep inside a structure"
        logger.error(error)
        Failure(new RuntimeException(error))
      case value =>
        Success(value)
    }

    val subtables = verifiedValue flatMap {
      case value: JsObject =>
        val tables = value.value collect {
          case (key, content: JsObject) =>
            tableStructure.subTables.map { subtables =>
              subtables.find(_.name == key).map { subtableStructure =>
                translateDataForHat(content, sourceName, key, recordDate, subtableStructure)
              } getOrElse {
                Failure(new RuntimeException(s"No Subtable $key provided in data structure, necessary to process data at $tableName:$key"))
              }
            } getOrElse {
              Failure(new RuntimeException(s"No Subtables provided in data structure, necessary to process data at $tableName:$key"))
            }
        }
        Utils.flatten(tables.toSeq) // Flatten Sequence of Tries to Try of Sequence
          .map(Utils.seqOption) // Wrap in Option depending if sequence is empty
      case _ =>
        Success(None)
    }

    val fields = verifiedValue flatMap {
      case value: JsObject =>
        val fields = value.value collect {
          case (key, content: JsBoolean) => buildDataField(content.toString(), key, recordDate, tableStructure)
          case (key, content: JsString)  => buildDataField(content.value, key, recordDate, tableStructure)
          case (key, content: JsNumber)  => buildDataField(content.toString(), key, recordDate, tableStructure)
        }
        Utils.flatten(fields.toSeq) // Flatten Sequence of Tries to Try of Sequence
          .map(Utils.seqOption) // Wrap in Option depending if sequence is empty
      case _ =>
        Success(None)
    }

    for {
      subtables <- subtables
      fields <- fields
    } yield tableStructure.copy(fields = fields, subTables = subtables) //ApiDataTable(None, None, None, tableName, sourceName, fields, subtables)
  }

  private def buildDataField(content: String, key: String, recordDate: Option[LocalDateTime], tableStructure: ApiDataTable): Try[ApiDataField] = {
    val triedDataField = Try(tableStructure.fields.get.find(_.name == key).get)
    triedDataField flatMap { apiDataField =>
      if (apiDataField.id.isEmpty || apiDataField.tableId.isEmpty) {
        Failure(new RuntimeException(s"Data Field $key has no ID set"))
      }
      else {
        val dataValue = ApiDataValue(None, None, recordDate, content, Some(apiDataField), None)
        Success(apiDataField.copy(values = Some(Seq(dataValue))))
      }
    }
  }

  protected def buildHatDataRecord(content: JsArray, sourceName: String,
    recordName: String, recordTimestamp: Option[DateTime],
    tableStructures: Map[String, ApiDataTable]): Future[ApiDataRecord] = {
    logger.debug(s"building HAT data record for $sourceName, $recordName (@$recordTimestamp), content: ${Json.prettyPrint(content)}")

    val recordDataTables = content.value collect {
      case dataItem: JsObject =>
        dataItem.value.map {
          case (endpointName, dataValue) =>
            val tableStructure = tableStructures(endpointName)
            logger.debug(s"Building HAT data table for $endpointName: ${Json.prettyPrint(dataValue)}")
            translateDataForHat(dataValue, sourceName, endpointName, recordTimestamp.map(_.toLocalDateTime), tableStructure)
        }
    }

    val triedDataRecord = Utils.flatten(recordDataTables.flatten) map { dataTables =>
      Future.successful(ApiDataRecord(None, None, recordTimestamp.map(_.toLocalDateTime), recordName, Some(dataTables)))
    } recover {
      case e =>
        Future.failed(e)
    }
    triedDataRecord.get
  }
}
