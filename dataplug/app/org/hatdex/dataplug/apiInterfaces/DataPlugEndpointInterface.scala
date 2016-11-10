package org.hatdex.dataplug.apiInterfaces

import org.hatdex.hat.api.models.{ ApiDataField, ApiDataRecord, ApiDataTable, ApiDataValue }
import org.joda.time.DateTime
import play.api.http.Status._
import play.api.libs.json.{ JsObject, JsValue }
import play.api.libs.ws.{ WSClient, WSResponse }

import scala.concurrent.{ ExecutionContext, Future }

trait DataPlugEndpointInterface {
  val wsClient: WSClient
  val sourceName: String
  val endpointName: String

  protected def buildRequest(params: ApiEndpoint): Future[WSResponse] = {
    val wsRequest = wsClient.url(params.url + params.path)
      .withQueryString(params.queryParameters.toList: _*)
      .withHeaders(params.headers.toList: _*)

    val response = params.method match {
      case Get        => wsRequest.get()
      case Post(body) => wsRequest.post(body)
      case Delete     => wsRequest.delete()
      case Put(body)  => wsRequest.put(body)
    }

    response
  }

  def fetch(params: ApiEndpoint)(implicit ec: ExecutionContext): Future[Option[ApiEndpoint]] = {
    buildRequest(params) flatMap { result =>
      result.status match {
        case OK =>
          processResults(result.json) map { _ =>
            buildContinuation(result.json, params)
          }
        case _ => Future.successful(None)
      }
    }
  }

  def translateDataForHat(content: JsValue, tableName: String): ApiDataTable = {
    // FIXME: handle list of objects
    val fields = content.as[JsObject].value collect {
      case (key, value: JsValue) =>
        ApiDataField(None, None, None, None, key, Some(Seq(ApiDataValue(None, None, None, value.toString(), None, None))))
    }

    // FIXME: handle simple vs object values (recursively)
    val subtables = None

    val table = ApiDataTable(None, None, None, tableName, sourceName, Some(fields.toSeq), subtables)

    table
  }

  def buildHatDataRecord(content: JsValue): ApiDataRecord = {
    val tables = Some(Seq(translateDataForHat(content, endpointName)))
    val recordName = s"$sourceName-$endpointName-${extractRecordTimestamp(content).getMillis}"
    val recordTimestamp = Some(extractRecordTimestamp(content).toLocalDateTime)
    val record = ApiDataRecord(None, None, recordTimestamp, recordName, tables)
    record
  }

  def extractRecordTimestamp(content: JsValue): DateTime = DateTime.now()

  def processResults(content: JsValue): Future[Unit]

  def buildContinuation(content: JsValue, params: ApiEndpoint): Option[ApiEndpoint]
}
