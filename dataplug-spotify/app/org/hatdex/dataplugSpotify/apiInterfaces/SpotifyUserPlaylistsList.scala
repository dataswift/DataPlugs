package org.hatdex.dataplugSpotify.apiInterfaces

import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.oauth2.GoogleProvider
import org.hatdex.dataplug.apiInterfaces.DataPlugOptionsCollector
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models._
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplugSpotify.models.SpotifyUsersPlaylist
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
