package org.hatdex.dataplug.apiInterfaces.models

import org.joda.time.DateTime
import play.api.libs.json._

object ApiEndpointMethod {

  sealed abstract class EndpointMethod(method: String) {
    override def toString = this.getClass.getName.toUpperCase
  }

  case class Get(private val method: String) extends EndpointMethod("Get") {
    def apply() = Get("Get")
  }

  case class Delete(private val method: String) extends EndpointMethod("Delete") {
    def apply() = Delete("Delete")
  }

  case class Post(private val method: String, body: String) extends EndpointMethod("Post") {
    def apply(body: String) = Post("Post", body)
  }

  case class Put(private val method: String, body: String) extends EndpointMethod("Put") {
    def apply(body: String) = Put("Put", body)
  }

}

case class ApiEndpointCall(
  url: String,
  path: String,
  method: ApiEndpointMethod.EndpointMethod,
  pathParameters: Map[String, String],
  queryParameters: Map[String, String],
  headers: Map[String, String])

case class ApiEndpoint(
  name: String,
  description: String,
  details: Option[String])

case class ApiEndpointVariant(
  endpoint: ApiEndpoint,
  variant: Option[String],
  variantDescription: Option[String],
  configuration: Option[ApiEndpointCall])

case class ApiEndpointStatus(
  phata: String,
  apiEndpoint: ApiEndpointVariant,
  endpointCall: ApiEndpointCall,
  timestamp: DateTime,
  successful: Boolean,
  message: Option[String])

object JsonProtocol {
  implicit val endpointMethodGetFormat = Json.format[ApiEndpointMethod.Get]
  implicit val endpointMethodDeleteFormat = Json.format[ApiEndpointMethod.Delete]
  implicit val endpointMethodPostFormat = Json.format[ApiEndpointMethod.Post]
  implicit val endpointMethodPutFormat = Json.format[ApiEndpointMethod.Put]

  implicit val endpointMethodFormat: Format[ApiEndpointMethod.EndpointMethod] = new Format[ApiEndpointMethod.EndpointMethod] {
    def reads(json: JsValue): JsResult[ApiEndpointMethod.EndpointMethod] = (json \ "method").as[String] match {
      case "Get"    => Json.fromJson[ApiEndpointMethod.Get](json)(endpointMethodGetFormat)
      case "Delete" => Json.fromJson[ApiEndpointMethod.Delete](json)(endpointMethodDeleteFormat)
      case "Post"   => Json.fromJson[ApiEndpointMethod.Post](json)(endpointMethodPostFormat)
      case "Put"    => Json.fromJson[ApiEndpointMethod.Put](json)(endpointMethodPutFormat)
      case method   => JsError(s"Unexpected JSON value $method in $json")
    }

    def writes(method: ApiEndpointMethod.EndpointMethod): JsValue = {
      method match {
        case method: ApiEndpointMethod.Get    => Json.toJson(method)(endpointMethodGetFormat)
        case method: ApiEndpointMethod.Delete => Json.toJson(method)(endpointMethodDeleteFormat)
        case method: ApiEndpointMethod.Post   => Json.toJson(method)(endpointMethodPostFormat)
        case method: ApiEndpointMethod.Put    => Json.toJson(method)(endpointMethodPutFormat)
      }
    }
  }

  implicit val endpointFormat = Json.format[ApiEndpoint]
  implicit val endpointCallFormat = Json.format[ApiEndpointCall]
  implicit val endpointVariantFormat = Json.format[ApiEndpointVariant]
  implicit val endpointStatusFormat = Json.format[ApiEndpointStatus]
}