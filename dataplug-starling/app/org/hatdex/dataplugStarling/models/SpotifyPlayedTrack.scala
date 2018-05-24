package org.hatdex.dataplugStarling.models

import play.api.libs.json.{ Json, Reads }

case class SpotifyPlayedTrack(
    track: SpotifyTrack,
    played_at: String)

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

object SpotifyTrack {
  implicit val spotifyTrackReads: Reads[SpotifyTrack] = Json.reads[SpotifyTrack]
}

object SpotifyArtist {
  implicit val spotifyArtistReads: Reads[SpotifyArtist] = Json.reads[SpotifyArtist]
}
