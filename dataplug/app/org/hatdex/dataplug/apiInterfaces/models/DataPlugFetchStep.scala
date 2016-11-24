package org.hatdex.dataplug.apiInterfaces.models

sealed trait DataPlugFetchStep

case class DataPlugFetchContinuation(call: ApiEndpointCall) extends DataPlugFetchStep
case class DataPlugFetchNextSync(call: ApiEndpointCall) extends DataPlugFetchStep
