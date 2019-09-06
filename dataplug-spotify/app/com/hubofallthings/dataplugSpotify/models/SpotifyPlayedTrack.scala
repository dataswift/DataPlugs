/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 2, 2017
 */

package com.hubofallthings.dataplugSpotify.models

import play.api.libs.json.{ Json, Reads }

case class SpotifyPlayedTrack(
    track: SpotifyTrack,
    played_at: String)

case class SpotifyPlaylistTrack(
    track: SpotifyTrack,
    added_at: String)

case class SpotifyTrack(
    artists: List[SpotifyArtist],
    available_markets: List[String],
    duration_ms: Int,
    explicit: Boolean,
    href: String,
    id: String,
    name: String,
    preview_url: Option[String],
    `type`: String,
    uri: String)

case class SpotifyArtist(
    href: String,
    id: String,
    name: String,
    `type`: String,
    uri: String)

object SpotifyPlayedTrack {
  implicit val spotifyPlayedTrackReads: Reads[SpotifyPlayedTrack] = Json.reads[SpotifyPlayedTrack]
}

object SpotifyPlaylistTrack {
  implicit val spotifyPlaylistTrackReads: Reads[SpotifyPlaylistTrack] = Json.reads[SpotifyPlaylistTrack]
}

object SpotifyTrack {
  implicit val spotifyTrackReads: Reads[SpotifyTrack] = Json.reads[SpotifyTrack]
}

object SpotifyArtist {
  implicit val spotifyArtistReads: Reads[SpotifyArtist] = Json.reads[SpotifyArtist]
}
