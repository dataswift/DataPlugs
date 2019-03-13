package org.hatdex.dataplugSpotify.apiInterfaces

import akka.actor.Scheduler
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import org.hatdex.dataplug.apiInterfaces.DataPlugOptionsCollector
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpoint, _ }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplugSpotify.apiInterfaces.authProviders.SpotifyProvider
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
    body.map { responseBody =>
      (responseBody \ "items").as[Seq[JsValue]] map { playlist =>
        val playlistId = (playlist \ "id").as[String]
        val name = (playlist \ "name").as[String]
        val pathParameters = SpotifyUserPlaylistTracksInterface.defaultApiEndpoint.pathParameters + ("playlistId" -> playlistId)
        val variant = ApiEndpointVariant(
          ApiEndpoint("playlists/tracks", "Spotify Playlist Tracks", None),
          Some(playlistId),
          Some(name),
          Some(SpotifyUserPlaylistTracksInterface.defaultApiEndpoint.copy(
            pathParameters = pathParameters,
            storageParameters = Some(Map("playlistName" -> name)))))

        ApiEndpointVariantChoice(playlistId, name, active = false, variant)
      }
    }.getOrElse(Seq())
  }

}
