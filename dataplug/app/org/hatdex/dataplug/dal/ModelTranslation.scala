package org.hatdex.dataplug.dal

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.OAuth1Info
import org.joda.time.DateTime
import play.api.Logger

trait ModelTranslation {
  import scala.language.implicitConversions

  protected val logger: Logger

  implicit def fromDbModel(auth: Tables.UserOauth1InfoRow): OAuth1Info = {
    OAuth1Info(auth.token, auth.secret)
  }

  implicit def toDbModel(login: LoginInfo, auth: OAuth1Info): Tables.UserOauth1InfoRow = {
    Tables.UserOauth1InfoRow(login.providerID, login.providerKey, auth.token, auth.secret, DateTime.now().toLocalDateTime)
  }
}

object ModelTranslation extends ModelTranslation {
  protected val logger = Logger(this.getClass)
}
