package org.hatdex.dataplug.apiInterfaces

sealed trait ApiEndpointMethod {
  override def toString = this.getClass.getName.toUpperCase
}
case object Get extends ApiEndpointMethod
case object Delete extends ApiEndpointMethod
case class Post(body: String) extends ApiEndpointMethod
case class Put(body: String) extends ApiEndpointMethod

case class ApiEndpoint(
  url: String,
  path: String,
  method: ApiEndpointMethod,
  queryParameters: Map[String, String],
  headers: Map[String, String])
