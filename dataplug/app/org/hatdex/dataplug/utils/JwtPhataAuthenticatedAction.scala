/*
 * Copyright (C) HAT Data Exchange Ltd - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.utils

import java.io.StringReader
import java.security.Security
import java.security.interfaces.RSAPublicKey
import javax.inject.Inject

import com.nimbusds.jose.JWSVerifier
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.SignedJWT
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.hatdex.hat.api.services.HatClient
import org.hatdex.dataplug.models.Identity
import org.joda.time.DateTime
import play.api.Logger
import play.api.cache.{ CacheApi, NamedCache }
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.Future
import scala.util.Try

class JwtPhataAuthenticatedRequest[A](val identity: Identity, val request: Request[A])
  extends WrappedRequest[A](request)

class JwtPhataAuthenticatedAction @Inject() (wSClient: WSClient, @NamedCache("session-cache") cache: CacheApi, configuration: play.api.Configuration) extends ActionBuilder[JwtPhataAuthenticatedRequest] {
  val logger = Logger("JwtPhataAuthenticatedAction")
  def invokeBlock[A](request: Request[A], block: (JwtPhataAuthenticatedRequest[A]) => Future[Result]): Future[Result] = {
    request.headers.get("X-Auth-Token")
      .orElse(request.getQueryString("X-Auth-Token"))
      .orElse(request.getQueryString("token"))
      .map(validateJwtToken)
      .map { eventualMaybeUser =>
        eventualMaybeUser.flatMap { maybeIdentity =>
          maybeIdentity map { identity =>
            block(new JwtPhataAuthenticatedRequest(identity, request))
          } getOrElse {
            Future.successful(Results.Unauthorized)
          }

        } recover {
          case e =>
            logger.error(s"Error while authenticating: ${e.getMessage}")
            Results.Unauthorized(Json.obj("status" -> "unauthorized", "message" -> s"No valid login information"))
        }
      }
      .getOrElse(Future.successful(Results.Unauthorized))
  }

  def validateJwtToken(token: String): Future[Option[Identity]] = {
    val expectedSubject = "hat"
    val expectedResources = configuration.getStringSeq("auth.allowedResources").get
    val expectedAccessCope = "validate"
    val maybeSignedJWT = Try(SignedJWT.parse(token))

    maybeSignedJWT.map { signedJWT =>
      val claimSet = signedJWT.getJWTClaimsSet
      val fresh = claimSet.getExpirationTime.after(DateTime.now().toDate)
      val subjectMatches = claimSet.getSubject == expectedSubject
      val resourceMatches = Option(claimSet.getClaim("resource").asInstanceOf[String]).map { resource =>
        expectedResources.exists(er => er.startsWith(resource))
      } getOrElse {
        false
      }
      val accessScopeMatches = Option(claimSet.getClaim("accessScope")).contains(expectedAccessCope)

      if (fresh && subjectMatches && resourceMatches && accessScopeMatches) {
        val identity = Identity(claimSet.getIssuer)
        verifiedIdentityCached(identity, signedJWT)
      }
      else {
        logger.debug("JWT token validation failed")
        Future(None)
      }
    } getOrElse {
      // JWT parse error
      Future(None)
    }
  }

  private def verifiedIdentityCached(identity: Identity, signedJWT: SignedJWT): Future[Option[Identity]] = {
    val maybeCachedPublicKey = cache.get(identity.phata).map { publicKey: RSAPublicKey =>
      Future.successful(Some(publicKey))
    }

    val eventuallySomePublicKey = maybeCachedPublicKey getOrElse {
      val hatClient = new HatClient(wSClient, identity.phata)
      hatClient.retrievePublicKey()
      val eventualHatPublicKey = for {
        publicKeyString <- hatClient.retrievePublicKey()
        publicKey <- readPublicKey(publicKeyString)
      } yield {
        cache.set(identity.phata, publicKey)
        Some(publicKey)
      }
      eventualHatPublicKey
    }

    val eventualPublicKey = eventuallySomePublicKey recover {
      case e =>
        logger.error(s"Error retrieving public key for $identity: ${e.getMessage}")
        None
    }

    val maybeIdentity = for {
      publicKey <- eventualPublicKey.map(_.get)
    } yield {
      val verifier: JWSVerifier = new RSASSAVerifier(publicKey)
      val verified = signedJWT.verify(verifier)
      if (verified) {
        logger.debug("JWT token signature verified")
        Some(identity)
      }
      else {
        logger.debug(s"JWT token signature failed for ${publicKey.toString}")
        None
      }
    }

    maybeIdentity recover {
      case e =>
        logger.error(s"Error while finding identity: ${e.getMessage}")
        throw e
    }

  }

  Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())
  def readPublicKey(publicKey: String): Future[RSAPublicKey] = {
    Future {
      val reader = new PEMParser(new StringReader(publicKey))
      val temp: SubjectPublicKeyInfo = reader.readObject().asInstanceOf[SubjectPublicKeyInfo]
      val converter = new JcaPEMKeyConverter()
      converter.getPublicKey(temp).asInstanceOf[RSAPublicKey]
    }
  }
}

class JwtPhataAwareRequest[A](val maybeUser: Option[Identity], val request: Request[A])
  extends WrappedRequest[A](request)

class JwtPhataAwareAction @Inject() (
    wSClient: WSClient,
    @NamedCache("session-cache") cache: CacheApi,
    configuration: play.api.Configuration,
    jwtAuthenticatedAction: JwtPhataAuthenticatedAction) extends ActionBuilder[JwtPhataAwareRequest] {

  val logger = Logger("JwtPhataAwareAction")

  def invokeBlock[A](request: Request[A], block: (JwtPhataAwareRequest[A]) => Future[Result]): Future[Result] = {
    request.headers.get("X-Auth-Token")
      .orElse(request.getQueryString("X-Auth-Token"))
      .orElse(request.getQueryString("token"))
      .map(jwtAuthenticatedAction.validateJwtToken)
      .map { eventualMaybeUser =>
        eventualMaybeUser.flatMap { maybe =>
          logger.debug(s"User auth checked, got back user ${maybe.map(_.phata)}")
          block(new JwtPhataAwareRequest(maybe, request))
        } recoverWith {
          case e =>
            logger.error(s"Error while authenticating: ${e.getMessage}")
            block(new JwtPhataAwareRequest(None, request))
        }
      }
      .getOrElse(block(new JwtPhataAwareRequest(None, request)))
  }
}
