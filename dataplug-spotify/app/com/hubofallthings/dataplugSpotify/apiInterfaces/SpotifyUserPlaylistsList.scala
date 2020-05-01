/*
 * Copyright (C) 2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Marios Tsekis <marios.tsekis@dataswift.io> 4, 2019
 */

package com.hubofallthings.dataplugSpotify.apiInterfaces

import com.google.inject.Inject
import com.hubofallthings.dataplug.apiInterfaces.DataPlugOptionsCollector
import com.hubofallthings.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import com.hubofallthings.dataplug.apiInterfaces.models.{ ApiEndpoint, ApiEndpointCall, ApiEndpointMethod, ApiEndpointVariant, ApiEndpointVariantChoice }
import com.hubofallthings.dataplug.services.UserService
import com.hubofallthings.dataplug.utils.Mailer
import com.hubofallthings.dataplugSpotify.models.SpotifyUsersPlaylist
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.hubofallthings.dataplugCalendar.apiInterfaces.authProviders._
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient

class SpotifyUserPlaylistsList @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val provider: GoogleProvider) extends DataPlugOptionsCollector with RequestAuthenticatorOAuth2 {

  val namespace: String = "spotify"
  val endpoint: String = "playlists/user"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.spotify.com",
    "/v1/me/playlists",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("limit" -> "100"),
    Map(),
    Some(Map()))

  override def generateEndpointChoices(responseBody: Option[JsValue]): Seq[ApiEndpointVariantChoice] = {
    responseBody.flatMap { responseBody =>
      (responseBody \ "items").asOpt[Seq[SpotifyUsersPlaylist]].map { playlists =>
        playlists.map { playlist =>
          val pathParameters = SpotifyUserPlaylistTracksInterface.defaultApiEndpoint.pathParameters + ("playlistId" -> playlist.id)
          val variant = ApiEndpointVariant(
            ApiEndpoint("spotify/playlist/tracks", "Spotify Playlist Tracks", None),
            Some(playlist.id),
            Some(playlist.name),
            Some(SpotifyUserPlaylistTracksInterface.defaultApiEndpoint.copy(
              pathParameters = pathParameters,
              storageParameters = Some(Map("playlistName" -> playlist.name)))))

          ApiEndpointVariantChoice(playlist.id, playlist.name, active = true, variant)
        }
      }
    }.getOrElse(Seq())
  }
}
