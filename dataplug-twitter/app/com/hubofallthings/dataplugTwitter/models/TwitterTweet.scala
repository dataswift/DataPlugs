/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 2, 2017
 */

package com.hubofallthings.dataplugTwitter.models

import play.api.libs.json._

case class TwitterPlaceAttributes(
    street_address: Option[String],
    locality: Option[String],
    region: Option[String],
    iso3: Option[String],
    postal_code: Option[String],
    phone: Option[String],
    twitter: Option[String],
    url: Option[String])

case class TwitterBoundingBox(
    `type`: String)

case class TwitterPlace(
    attributes: Option[TwitterPlaceAttributes],
    boundingBox: Option[TwitterBoundingBox],
    country: Option[String],
    country_code: Option[String],
    full_name: Option[String],
    id: String,
    name: Option[String],
    place_type: Option[String],
    url: Option[String])

case class TwitterCoordinates(
    `type`: String)

case class TwitterTweet(
    lastUpdated: String, // Field inserted by the plug and derived from 'created_at' value; here only for backwards compatibility
    coordinates: Option[TwitterCoordinates], // Represents the geographic location of this Tweet as reported by the user or client application. The inner coordinates array is formatted as geoJSON (longitude first, then latitude).
    created_at: String, // UTC time when this Tweet was created.
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
    retweet_count: Int,
    retweeted: Boolean,
    source: String,
    text: String,
    truncated: Boolean,
    user: TwitterUser)

object TwitterTweet {
  implicit val placeAttributesFormat = Json.format[TwitterPlaceAttributes]
  implicit val boundingBoxFormat = Json.format[TwitterBoundingBox]
  implicit val placeFormat = Json.format[TwitterPlace]
  implicit val coordinatesFormat = Json.format[TwitterCoordinates]

  implicit val tweetFormat = Json.format[TwitterTweet]
}
