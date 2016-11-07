/*
 * Copyright (C) HAT Data Exchange Ltd - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.models

import java.io.StringReader
import java.security.Security
import java.security.interfaces.RSAPublicKey

import com.nimbusds.jose.JWSVerifier
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.SignedJWT
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.joda.time.DateTime

import scala.collection.JavaConverters._
import scala.util.{ Failure, Success, Try }

case class JwtToken(subject: String, issuer: String, expirationTime: DateTime, claimset: Map[String, AnyRef], publicKey: Option[RSAPublicKey]) {
  def valid = expirationTime.isAfter(DateTime.now)
}

object JwtToken {
  def parseSigned(token: String, maybePublicKey: Option[String] = None): Try[JwtToken] = {
    val triedSignedJWT = Try(SignedJWT.parse(token))
    triedSignedJWT flatMap { tokenParsed =>
      val tokenClaimSet = tokenParsed.getJWTClaimsSet
      val expiry = new DateTime(tokenClaimSet.getExpirationTime)
      val claims = tokenClaimSet.getClaims.asScala.toMap
      val maybeRsaPublicKey = maybePublicKey.map(readPublicKey)
      maybeRsaPublicKey map { rsaPublicKey =>
        if (verifyPublicKey(rsaPublicKey, tokenParsed)) {
          Success(JwtToken(tokenClaimSet.getSubject, tokenClaimSet.getIssuer, expiry, claims, Some(rsaPublicKey)))
        }
        else {
          Failure(new RuntimeException("Token public key does not match signature"))
        }
      } getOrElse {
        Success(JwtToken(tokenClaimSet.getSubject, tokenClaimSet.getIssuer, expiry, claims, None))
      }
    }
  }

  def verifyPublicKey(publicKey: RSAPublicKey, jwtClaimsSet: SignedJWT): Boolean = {
    val verifier: JWSVerifier = new RSASSAVerifier(publicKey)
    jwtClaimsSet.verify(verifier)
  }

  Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())

  def readPublicKey(publicKey: String): RSAPublicKey = {
    val reader = new PEMParser(new StringReader(publicKey))
    val subjectPublicKeyInfo: SubjectPublicKeyInfo = reader.readObject().asInstanceOf[SubjectPublicKeyInfo]
    val converter = new JcaPEMKeyConverter()
    converter.getPublicKey(subjectPublicKeyInfo).asInstanceOf[RSAPublicKey]
  }
}
