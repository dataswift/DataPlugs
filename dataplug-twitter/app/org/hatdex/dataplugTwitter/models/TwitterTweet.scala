/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 2, 2017
 */

package org.hatdex.dataplugTwitter.models

import org.hatdex.dataplug.apiInterfaces.models.ApiEndpointTableStructure
import play.api.libs.json._

//case class TwitterHashtag(
//  //indices: List[Int], // An array of integers indicating the offsets within the Tweet text where the hashtag begins and ends. The first integer represents the location of the # character in the Tweet text string. The second integer represents the location of the first character after the hashtag. Therefore the difference between the two numbers will be the length of the hashtag name plus one (for the ‘#’ character).
//  text: String // Name of the hashtag, minus the leading ‘#’ character.
//)
//
//case class TwitterMedia(
//    display_url: String,
//    expanded_url: String,
//    id: Long,
//    //indices: String,
//    media_url_https: String,
//    //sizes: TwitterSizes,
//    source_status_id: Option[Long],
//    `type`: String,
//    url: String
//) extends ApiEndpointTableStructure {
//  val dummyEntity = TwitterMedia.dummyEntity
//
//  implicit val twitterMediaFormat = TwitterMedia.twitterMediaFormat
//
//  def toJson: JsValue = Json.toJson(this)
//}
//
//object TwitterMedia extends ApiEndpointTableStructure {
//  implicit val twitterMediaFormat = Json.format[TwitterMedia]
//
//  val dummyEntity = TwitterMedia(
//    "display URL",
//    "expanded URL",
//    12345.toLong,
//    "media URL",
//    Some(12345.toLong),
//    "photo",
//    "url")
//
//  def toJson: JsValue = Json.toJson(dummyEntity)
//}
//
//case class TwitterSize(
//  h: Int,
//  resize: String,
//  w: Int
//)
//
//case class TwitterSizes(
//  thumb: Option[TwitterSize],
//  large: Option[TwitterSize],
//  medium: Option[TwitterSize],
//  small: Option[TwitterSize]
//)

//case class TwitterURL(
//  display_url: String,
//  expanded_url: String,
//  //indices: List[Int],
//  url: String
//)
//
//case class TwitterUserMention(
//  id: Long,
//  indices: List[Int],
//  name: String,
//  screen_name: String
//)

//case class TwitterEntity(hashtags: Option[List[TwitterHashtag]], // Represents hashtags which have been parsed out of the Tweet text.
//  media: Option[List[TwitterMedia]] // Represents media elements uploaded with the Tweet.
//  urls: Option[List[TwitterURL]], // Represents URLs included in the text of a Tweet or within textual fields of a user object.
//  user_mentions: Option[List[TwitterUserMention]] // Represents other Twitter users mentioned in the text of the Tweet.
//)
//
//object TwitterEntity {
//  val dummyEntity = TwitterEntity(Some(List(TwitterHashtag("hashtag!"))),
//  Some(List())
//    Some(List(TwitterURL("display URL", "expanded url", "url"))),
//    Some(List(TwitterUserMention(12345.toLong, "mentioned username", "screen name of the user")))
//  )
//}

case class TwitterPlaceAttributes(
  street_address: Option[String],
  locality: Option[String],
  region: Option[String],
  iso3: Option[String],
  postal_code: Option[String],
  phone: Option[String],
  twitter: Option[String],
  url: Option[String]
)

case class TwitterBoundingBox(
  //coordinates: List[List[List[Double]]],
  `type`: String
)

case class TwitterPlace(
  attributes: Option[TwitterPlaceAttributes],
  boundingBox: Option[TwitterBoundingBox],
  country: Option[String],
  country_code: Option[String],
  full_name: Option[String],
  id: String,
  name: Option[String],
  place_type: Option[String],
  url: Option[String]
)

object TwitterPlace {
  val dummyEntity = TwitterPlace(
    Some(TwitterPlaceAttributes(
      Some("street"), Some("locality"), Some("region"), Some("iso3"), Some("N11 4MP"), Some("phone"), Some("twitter"), Some("url")
    )),
    Some(TwitterBoundingBox(
      //List(List(List(3.5, 2.1))),
      "polygon")),
    Some("United Kingdom"),
    Some("UK"),
    Some("London, UK"),
    "a346njkn54jkb32",
    Some("London"),
    Some("city"),
    Some("url")
  )
}

case class TwitterCoordinates(
  //coordinates: List[Double],
  `type`: String
)

case class TwitterUserShort(
  id: Long,
  name: String,
  screen_name: String,
  followers_count: Int,
  friends_count: Int,
  listed_count: Int,
  favourites_count: Int,
  statuses_count: Int,
  lang: String
)

object TwitterUserShort {
  val dummyEntity = TwitterUserShort(
    123.toLong,
    "Larry",
    "larryb",
    5,
    120,
    2,
    5,
    1000,
    "en"
  )
}

// TODO: solve the table name collision in the embedded tweets.

//case class TwitterEmbeddedTweet(
//  coordinates: Option[TwitterCoordinates],
//  created_at: String,
//  entities: TwitterEntity,
//  favorite_count: Option[Int],
//  favorited: Option[Boolean],
//  id: Long,
//  in_reply_to_screen_name: Option[String],
//  in_reply_to_status_id: Option[Long],
//  in_reply_to_user_id: Option[Long],
//  lang: Option[String],
//  place: Option[TwitterPlace],
//  possibly_sensitive: Option[Boolean],
//  retweet_count: Int,
//  retweeted: Boolean,
//  source: String,
//  text: String,
//  truncated: Boolean
//)
//
//object TwitterEmbeddedTweet {
//  val dummyEntity = TwitterEmbeddedTweet(
//    Some(TwitterCoordinates(
//      //List(1.0, 2.0),
//      "point")),
//    "createdAt",
//    //dummyTwitterEntity,
//    Some(123),
//    Some(true),
//    12345.toLong,
//    Some("inReplyToScreenName"),
//    Some(12345.toLong),
//    Some(12345.toLong),
//    Some("lang"),
//    Some(TwitterPlace.dummyEntity),
//    Some(false),
//    15,
//    true,
//    "source",
//    "actual tweet text",
//    false
//  )
//}

case class TwitterTweet(
    coordinates: Option[TwitterCoordinates], // Represents the geographic location of this Tweet as reported by the user or client application. The inner coordinates array is formatted as geoJSON (longitude first, then latitude).
    created_at: String, // UTC time when this Tweet was created.
    // entities: TwitterEntity,
    favorite_count: Option[Int],
    favorited: Option[Boolean],
    id: Long,
    in_reply_to_screen_name: Option[String],
    in_reply_to_status_id: Option[Long],
    in_reply_to_user_id: Option[Long],
    lang: Option[String],
    place: Option[TwitterPlace],
    possibly_sensitive: Option[Boolean],
    quoted_status_id: Option[Long],
    //quoted_status: Option[TwitterEmbeddedTweet],
    retweet_count: Int,
    retweeted: Boolean,
    //retweeted_status: Option[TwitterEmbeddedTweet],
    source: String,
    text: String,
    truncated: Boolean,
    user: TwitterUserShort
) extends ApiEndpointTableStructure {
  val dummyEntity = TwitterTweet.dummyEntity

  implicit val twitterTweetFormat = TwitterTweet.tweetFormat

  def toJson: JsValue = Json.toJson(this)
}

object TwitterTweet extends ApiEndpointTableStructure {
  //  implicit val sizeFormat = Json.format[TwitterSize]
  //  implicit val sizesFormat = Json.format[TwitterSizes]
  //  implicit val hashtagFormat = Json.format[TwitterHashtag]
  //  implicit val urlFormat = Json.format[TwitterURL]
  //  implicit val userMentionFormat = Json.format[TwitterUserMention]
  //  implicit val entityFormat = Json.format[TwitterEntity]
  //  implicit val embeddedTweetFormat = Json.format[TwitterEmbeddedTweet]

  implicit val placeAttributesFormat = Json.format[TwitterPlaceAttributes]
  implicit val boundingBoxFormat = Json.format[TwitterBoundingBox]
  implicit val placeFormat = Json.format[TwitterPlace]
  implicit val coordinatesFormat = Json.format[TwitterCoordinates]
  implicit val userShortFormat = Json.format[TwitterUserShort]

  implicit val tweetFormat = Json.format[TwitterTweet]

  val dummyEntity = TwitterTweet(
    Some(TwitterCoordinates(
      //List(1.0, 2.0),
      "point")),
    "createdAt",
    // TwitterEntity.dummyEntity,
    Some(123),
    Some(true),
    12345.toLong,
    Some("inReplyToScreenName"),
    Some(12345.toLong),
    Some(12345.toLong),
    Some("lang"),
    Some(TwitterPlace.dummyEntity),
    Some(false),
    Some(12345.toLong),
    15,
    true,
    "source",
    "actual tweet text",
    false,
    TwitterUserShort.dummyEntity)

  def toJson: JsValue = Json.toJson(dummyEntity)
}
