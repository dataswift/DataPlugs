/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 9, 2017
 */

package com.hubofallthings.dataplugSpotify.apiInterfaces

import akka.actor.Scheduler
import com.google.inject.Inject
import com.hubofallthings.dataplug.apiInterfaces.DataPlugOptionsCollector
import com.hubofallthings.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import com.hubofallthings.dataplug.apiInterfaces.models.{ ApiEndpoint, ApiEndpointCall, ApiEndpointMethod, ApiEndpointVariant, ApiEndpointVariantChoice }
import com.hubofallthings.dataplug.services.UserService
import com.hubofallthings.dataplug.utils.Mailer
import com.hubofallthings.dataplugSpotify.apiInterfaces.authProviders.SpotifyProvider
import com.hubofallthings.dataplugSpotify.models.SpotifyUsersPlaylist
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient

class SpotifyProfileCheck @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: SpotifyProvider) extends DataPlugOptionsCollector with RequestAuthenticatorOAuth2 {

  val namespace: String = "spotify"
  val endpoint: String = "profile"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.spotify.com",
    "/v1/me/playlists",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map(),
    Map(),
    Some(Map()))

  def generateEndpointChoices(responseBody: Option[JsValue]): Seq[ApiEndpointVariantChoice] = {
    staticEndpointChoices ++ getTracksFromPlaylists(responseBody)
  }

  def staticEndpointChoices: Seq[ApiEndpointVariantChoice] = {
    val profileVariant = ApiEndpointVariant(
      ApiEndpoint("profile", "User's Spotify profile information", None),
      Some(""), Some(""),
      Some(SpotifyProfileInterface.defaultApiEndpoint))

    val recentlyPlayedVariant = ApiEndpointVariant(
      ApiEndpoint("feed", "A feed of Spotify tracks played", None),
      Some(""), Some(""),
      Some(SpotifyRecentlyPlayedInterface.defaultApiEndpoint))

    val userPlaylistsVariant = ApiEndpointVariant(
      ApiEndpoint("playlists/user", "User's Spotify playlists", None),
      Some(""), Some(""),
      Some(SpotifyUserPlaylistsInterface.defaultApiEndpoint))

    Seq(
      ApiEndpointVariantChoice("profile", "User's Spotify profile information", active = true, profileVariant),
      ApiEndpointVariantChoice("playlists/user", "User's Spotify playlists", active = true, userPlaylistsVariant),
      ApiEndpointVariantChoice("feed", "A feed of Spotify tracks played", active = true, recentlyPlayedVariant))
  }

  private def getTracksFromPlaylists(body: Option[JsValue]): Seq[ApiEndpointVariantChoice] = {
    body.flatMap { responseBody =>
      (responseBody \ "items").asOpt[Seq[SpotifyUsersPlaylist]].map { playlists =>
        playlists.map { playlist =>
          val pathParameters = SpotifyUserPlaylistTracksInterface.defaultApiEndpoint.pathParameters + ("playlistId" -> playlist.id)
          val variant = ApiEndpointVariant(
            ApiEndpoint("playlists/tracks", "Spotify Playlist Tracks", None),
            Some(playlist.id),
            Some(playlist.name),
            Some(SpotifyUserPlaylistTracksInterface.defaultApiEndpoint.copy(
              pathParameters = pathParameters,
              storageParameters = Some(Map("playlistName" -> playlist.name)))))

          ApiEndpointVariantChoice(playlist.id, playlist.name, active = false, variant)
        }
      }
    }.getOrElse(Seq())
  }
}
