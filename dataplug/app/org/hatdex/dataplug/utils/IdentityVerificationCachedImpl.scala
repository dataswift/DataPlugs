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
import org.hatdex.dataplug.models.User
import org.hatdex.hat.api.services.HatClient
import play.api.Logger
import play.api.cache.{ CacheApi, NamedCache }
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.ws.WSClient

import scala.concurrent.Future

class IdentityVerificationCachedImpl @Inject() (
    wsClient: WSClient,
    @NamedCache("session-cache") cache: CacheApi) extends JwtIdentityVerification {

  val logger = Logger("JwtPhataAuthentication")

  def verifiedIdentity(identity: User, signedJWT: SignedJWT): Future[Option[User]] = {
    val maybeCachedPublicKey = cache.get(identity.userId).map { publicKey: RSAPublicKey =>
      Future.successful(Some(publicKey))
    }

    val eventuallySomePublicKey = maybeCachedPublicKey getOrElse {
      val hatClient = new HatClient(wsClient, identity.userId)
      hatClient.retrievePublicKey()
      val eventualHatPublicKey = for {
        publicKeyString <- hatClient.retrievePublicKey()
        publicKey <- readPublicKey(publicKeyString)
      } yield {
        cache.set(identity.userId, publicKey)
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
