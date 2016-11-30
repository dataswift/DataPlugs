package org.hatdex.dataplug.apiInterfaces

import java.net.URLEncoder

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.hatdex.dataplug.actors.HatClientActor
import org.hatdex.dataplug.apiInterfaces.authProviders.RequestAuthenticator
import org.hatdex.dataplug.apiInterfaces.models._
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.hat.api.models.{ ApiDataRecord, ApiDataTable, ApiRecordValues }
import org.joda.time.DateTime
import play.api.Logger
import play.api.cache.CacheApi
import play.api.http.Status._
import play.api.libs.json.{ JsArray, JsValue, Json }
import play.api.libs.ws.{ WSClient, WSResponse }

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }

trait DataPlugEndpointInterface extends HatDataOperations with RequestAuthenticator {
  val endpointName: String
  val refreshInterval: FiniteDuration
  protected val wsClient: WSClient
  protected val sourceName: String
  protected val logger: Logger
  val defaultApiEndpoint: ApiEndpointCall
  val cacheApi: CacheApi
  val mailer: Mailer

  protected val apiEndpointTableStructures: Map[String, ApiEndpointTableStructure]

  private lazy val apiTableStructures = apiEndpointTableStructures map {
    case (k, v: ApiEndpointTableStructure) =>
      k -> buildHATDataTableStructure(v.dummyEntity.toJson, sourceName, k).get
  }

  /**
   * Fetch data from an API endpoint as per parametrised configuration, for a specific HAT client
   *
   * @param params API endpoint parameters generic (stateless) for the endpoint
   * @param hatAddress HAT Address (domain)
   * @param hatClient HAT client actor for specific HAT
   * @return Potentially updated set of parameters, e.g. with new timestamps
   */
  def fetch(fetchParams: ApiEndpointCall, hatAddress: String, hatClientActor: ActorRef)(implicit ec: ExecutionContext, timeout: Timeout): Future[DataPlugFetchStep] = {
    val authenticatedFetchParameters = authenticateRequest(fetchParams, hatAddress)

    authenticatedFetchParameters flatMap { requestParameters =>
      buildRequest(requestParameters)
    } flatMap { result =>
      result.status match {
        case OK =>
          processResults(result.json, hatAddress, hatClientActor, fetchParams) map { _ =>
            buildContinuation(result.json, fetchParams)
              .map(DataPlugFetchContinuation)
              .getOrElse(DataPlugFetchNextSync(buildNextSync(result.json, fetchParams)))
          } recoverWith {
            case error =>
              mailer.serverExceptionNotifyInternal(
                s"""
                   | Processing Response for HAT $hatAddress.
                   | Fetch Parameters: $fetchParams.
                   | Content: ${Json.prettyPrint(result.json)}
          """.stripMargin, error)
              Future.failed(error)
          }

        case UNAUTHORIZED =>
          logger.warn(s"Unauthorized request $fetchParams - ${result.status}: ${result.body}")
          Future.successful(DataPlugFetchNextSync(fetchParams))
        case NOT_FOUND =>
          logger.warn(s"Not found for request $fetchParams - ${result.status}: ${result.body}")
          Future.failed(new RuntimeException(s"Not found for request $fetchParams - ${result.status}: ${result.body}"))
        case _ =>
          logger.warn(s"Unsuccessful response from api endpoint $fetchParams - ${result.status}: ${result.body}")
          Future.successful(DataPlugFetchNextSync(fetchParams))
      }
    } recoverWith {
      case e =>
        logger.warn(s"Error when querying api endpoint $fetchParams - ${e.getMessage}", e)
        Future.failed(e)
    }
  }

  protected def buildRequest(params: ApiEndpointCall): Future[WSResponse] = {
    val path = params.pathParameters.foldLeft(params.path) { (path, parameter) =>
      path.replace(s"[${parameter._1}]", URLEncoder.encode(parameter._2, "UTF-8"))
    }
    val wsRequest = wsClient.url(params.url + path)
      .withQueryString(params.queryParameters.toList: _*)
      .withHeaders(params.headers.toList: _*)

    val response = params.method match {
      case ApiEndpointMethod.Get(_)        => wsRequest.get()
      case ApiEndpointMethod.Post(_, body) => wsRequest.post(body)
      case ApiEndpointMethod.Delete(_)     => wsRequest.delete()
      case ApiEndpointMethod.Put(_, body)  => wsRequest.put(body)
    }

    response
  }

  protected def ensureDataTable(tableName: String, hatAddress: String, hatClientActor: ActorRef)(implicit ec: ExecutionContext, timeout: Timeout): Future[ApiDataTable] = {
    val cacheKey = s"apitable:$hatAddress:$sourceName:$tableName"
    val maybeCachedTable = cacheApi.get[ApiDataTable](cacheKey)
    maybeCachedTable map { cachedTable =>
      Future.successful(cachedTable)
    } getOrElse {
      (hatClientActor ? HatClientActor.FindDataTable(tableName, sourceName)).mapTo[ApiDataTable] map { tableFound =>
        cacheApi.set(cacheKey, tableFound)
        tableFound
      } recoverWith {
        case findError =>
          logger.warn(s"Finding table failed: ${findError.getMessage}")
          val tableStructure = apiTableStructures(tableName)
          (hatClientActor ? HatClientActor.CreateDataTable(tableStructure)).mapTo[ApiDataTable]
      }
    }
  }

  protected def processResults(content: JsValue, hatAddress: String, hatClientActor: ActorRef, fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Unit] = {
    val eventualHatData = buildHatDataRecord(JsArray(Seq(content)), sourceName, endpointName, None, Map())

    eventualHatData flatMap { hatData =>
      uploadHatData(Seq(hatData), hatAddress, hatClientActor)
    }

  }

  protected def ensureDataTables(hatAddress: String, hatClientActor: ActorRef)(implicit ec: ExecutionContext, timeout: Timeout): Future[Map[String, ApiDataTable]] = {
    Future.sequence(apiTableStructures.map {
      case (k, v) =>
        ensureDataTable(k, hatAddress, hatClientActor)
    }) map { tables =>
      Map(tables.map { table =>
        table.name -> table
      }.toSeq: _*)
    }
  }

  protected def uploadHatData(data: Seq[ApiDataRecord], hatAddress: String, hatClientActor: ActorRef)(implicit ec: ExecutionContext, timeout: Timeout): Future[Unit] = {
    val dataRecords = data map { dataRecord =>
      ApiRecordValues(
        ApiDataRecord(None, None, lastUpdated = dataRecord.lastUpdated, name = dataRecord.name, None),
        dataRecord.tables.map(tables => tables.flatMap(ApiDataTable.extractValues)).getOrElse(Seq())
      )
    }
    if (dataRecords.nonEmpty) {
      hatClientActor ? HatClientActor.PostData(dataRecords) map { case _ => () }
    }
    else {
      Future.successful(())
    }
  }

  /**
   * Build api endpoint parameters for a new data request. Most Plugs will need to overwrite
   *
   * @param params API endpoint parameters generic (stateless) for the endpoint
   * @return Potentially updated set of parameters, e.g. with new timestamps
   */
  def buildFetchParameters(params: Option[ApiEndpointCall])(implicit ec: ExecutionContext): Future[ApiEndpointCall] = {
    Future.successful(params getOrElse defaultApiEndpoint)
  }

  /**
   * Extract timestamp of data record to be stored in the HAT - HAT allows timestamp fields to
   * be set in the right format for easier handling later
   *
   * @param content JSON value of a successful response
   * @return DateTime-formatted timestamp - now by default
   */
  protected def extractRecordTimestamp(content: JsValue): DateTime = DateTime.now()

  /**
   * Build data fetch continuation API call parameters if the source has data paging.
   * Responsible for checking if there should be a further call for fetching data and configuring the endpoint
   *
   * @param content JSON value of a successful response
   * @param params pre-set API endpoint parameters to build from
   * @return Optional API endpoint configuration for continuation - None means no continuation
   */
  protected def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall]

  /**
   * Set up API call details for next synchronisation - set up any fields available at the last operation of the current
   * synchronisation round.
   *
   * @param content JSON value of a successful response
   * @param params pre-set API endpoint parameters to build from
   * @return Optional API endpoint configuration for continuation - None means no continuation
   */
  protected def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall

  /**
   * Asynchronously save endpoint status for a given hat address if data has been fetched successfully.
   *
   * @param content JSON value of a successful response
   * @param hatAddress address of the HAT for which to update plug status
   */
  //  protected def saveEndpointStatus(content: JsValue, hatAddress: String): Future[Unit]
}
