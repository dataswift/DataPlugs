package org.hatdex.dataplug.testkit

import com.nimbusds.jwt.SignedJWT
import org.hatdex.dataplug.models.User
import org.hatdex.dataplug.utils.JwtIdentityVerification
import play.api.Logger

import scala.concurrent.Future

class IdentityVerificationTestImpl extends JwtIdentityVerification {

  val logger = Logger("JwtPhataAuthentication")

  def verifiedIdentity(identity: User, signedJWT: SignedJWT): Future[Option[User]] = {
    Future.successful(Some(identity))
  }
}
