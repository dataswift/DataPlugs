package org.hatdex.dataplugSpotify.models

import play.api.libs.json.{ Json, Reads }

case class SpotifyUsersPlaylist(
    id: String,
    name: String,
    snapshot_id: String)

object SpotifyUsersPlaylist {
  implicit val spotifyusersPlaylistReads: Reads[SpotifyUsersPlaylist] = Json.reads[SpotifyUsersPlaylist]
}
