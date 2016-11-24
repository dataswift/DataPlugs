package org.hatdex.dataplug.utils

import com.nimbusds.jwt.SignedJWT
import org.hatdex.dataplug.models.User

import scala.concurrent.Future

trait JwtIdentityVerification {
  def verifiedIdentity(identity: User, signedJWT: SignedJWT): Future[Option[User]]
}
