/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 2, 2017
 */

package com.hubofallthings.dataplugSpotify.models

import play.api.libs.json.{ Json, Reads }

case class SpotifyUsersPlaylist(
    id: String,
    name: String,
    snapshot_id: String)

object SpotifyUsersPlaylist {
  implicit val spotifyusersPlaylistReads: Reads[SpotifyUsersPlaylist] = Json.reads[SpotifyUsersPlaylist]
}
