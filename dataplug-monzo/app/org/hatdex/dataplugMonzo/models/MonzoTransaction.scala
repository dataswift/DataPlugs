package org.hatdex.dataplugMonzo.models

import org.hatdex.dataplug.apiInterfaces.models.ApiEndpointTableStructure
import org.hatdex.hat.api.json.DateTimeMarshalling
import org.joda.time.DateTime
import play.api.libs.json._

case class MonzoMerchantMetadata(
    created_for_merchant: Option[String],
    created_for_transaction: Option[String],
    enriched_from_settlement: Option[String],
    foursquare_category: Option[String],
    foursquare_category_icon: Option[String],
    foursquare_id: Option[String],
    foursquare_website: Option[String],
    google_places_icon: Option[String],
    google_places_id: Option[String],
    google_places_name: Option[String],
    suggested_name: Option[String],
    suggested_tags: Option[String],
    twitter_id: Option[String],
    website: Option[String])

object MonzoMerchantMetadata {
  val dummyValue = MonzoMerchantMetadata(
    Some("merch_000092sud4sNntVo2yybaL"),
    Some("tx_00009EZuD0DlLwfk4tskT3"),
    Some("tx_00009EZuD0DlLwfk4tskT3"),
    Some("Gift Shop"),
    Some("https://ss3.4sqi.net/img/categories_v2/shops/giftshop_88.png"),
    Some("4e72231afa76b23d31c0aef3"),
    Some(""),
    Some("https://maps.gstatic.com/mapfiles/place_api/icons/shopping-71.png"),
    Some("ChIJL-PEtqoEdkgRNQbcoGM-c6w"),
    Some("Cards Galore"),
    Some("Cards Galore"),
    Some("#gifts #personal"),
    Some("CardsGaloreUK"),
    Some("http://cardsgalore.co.uk/"))
}

case class MonzoAddress(
    short_formatted: Option[String],
    formatted: Option[String],
    address: Option[String],
    city: Option[String],
    region: Option[String],
    country: Option[String],
    postcode: Option[String],
    latitude: Option[Double],
    longitude: Option[Double],
    zoom_level: Option[Int],
    approximate: Option[Boolean])

object MonzoAddress {
  val dummyEntity = MonzoAddress(
    Some("13 Bow Lane, London EC4M 9AL"),
    Some("13 Bow Lane, London, England EC4M 9AL, United Kingdom"),
    Some("13 Bow Lane"),
    Some("London"),
    Some("England"),
    Some("GBR"),
    Some("EC4M 9AL"),
    Some(51.5133143),
    Some(-0.09362329999999999),
    Some(17),
    Some(false))
}

case class MonzoMerchant(
    id: String,
    group_id: Option[String],
    created: DateTime,
    name: String,
    logo: Option[String],
    emoji: Option[String],
    category: String,
    online: Boolean,
    atm: Option[Boolean],
    address: Option[MonzoAddress],
    updated: Option[DateTime],
    metadata: Option[MonzoMerchantMetadata],
    disabled_feedback: Option[Boolean])

object MonzoMerchant {
  val dummyEntity = MonzoMerchant(
    "merch_00009EZuD1QuqXAzUeUr4b",
    Some("grp_000092sud4urefqOe8oEK1"),
    DateTime.now(),
    "Cards Galore",
    Some("https://mondo-logo-cache.appspot.com/twitter/CardsGaloreUK/?size=large"),
    Some("🎁"),
    "shopping",
    online = false,
    atm = Some(false),
    Some(MonzoAddress.dummyEntity),
    Some(DateTime.now()),
    Some(MonzoMerchantMetadata.dummyValue),
    disabled_feedback = Some(false))
}

case class MonzoAttachment(
    created: DateTime,
    external_id: String,
    file_type: Option[String],
    file_url: Option[String],
    id: String,
    `type`: Option[String],
    url: Option[String],
    user_id: String) extends ApiEndpointTableStructure {
  val dummyEntity = MonzoAttachment.dummyEntity
  implicit val monzoTransactionFormat = MonzoAttachment.monzoAttachmentFormat

  def toJson = Json.toJson(this)
}

object MonzoAttachment extends ApiEndpointTableStructure {
  import play.api.libs.json.JodaWrites._
  import play.api.libs.json.JodaReads._
  implicit val monzoAttachmentFormat = Json.format[MonzoAttachment]

  val dummyEntity = MonzoAttachment(
    DateTime.now(),
    "tx_00009Ez9skNazt30jFzzo9",
    Some("image/jpeg"),
    Some("https://s3-eu-west-1.amazonaws.com/mondo-production-image-uploads/garbage"),
    "attach_00009EzGCErGVCmIU2YPqr",
    Some("image/jpeg"),
    Some("https://s3-eu-west-1.amazonaws.com/mondo-production-image-uploads/garbage"),
    "user_00009EsRjHWhLFBdZZ1kDR")

  def toJson: JsValue = Json.toJson(dummyEntity)
}

case class MonzoTransaction(
    account_balance: Int,
    account_id: String,
    amount: Int,
    created: Option[DateTime],
    updated: Option[DateTime],
    currency: String,
    description: String,
    id: String,
    merchant: Option[MonzoMerchant],
    notes: String,
    is_load: Boolean,
    settled: Option[DateTime],
    category: String,
    scheme: Option[String],
    local_amount: Option[Int],
    local_currency: Option[String],
    attachments: Option[List[MonzoAttachment]]) extends ApiEndpointTableStructure {
  val dummyEntity = MonzoTransaction.dummyEntity
  implicit val monzoTransactionFormat = MonzoTransaction.monzoTransactionFormat

  def toJson = Json.toJson(this)
}

object MonzoTransaction extends ApiEndpointTableStructure with DateTimeMarshalling {
  val dummyEntity = MonzoTransaction(
    13013,
    "accountId",
    -510,
    Option(DateTime.now()),
    Option(DateTime.now()),
    "GBP",
    "THE DE BEAUVOIR DELI C LONDON        GBR",
    "tx_00008zIcpb1TB4yeIFXMzx",
    Some(MonzoMerchant.dummyEntity),
    "Salmon sandwich",
    is_load = false,
    Option(DateTime.now()),
    "eating_out",
    Some("gps_mastercard"),
    Some(-510),
    Some("GBP"),
    Some(List(MonzoAttachment.dummyEntity)))

  implicit val monzoAddressFormat = Json.format[MonzoAddress]
  implicit val monzoMerchantMetadataFormat = Json.format[MonzoMerchantMetadata]
  implicit val monzoAttachmentFormat = Json.format[MonzoAttachment]
  implicit val monzoMerchantFormat = Json.format[MonzoMerchant]
  implicit val monzoTransactionFormat = Json.format[MonzoTransaction]

  def toJson: JsValue = Json.toJson(dummyEntity)
}

