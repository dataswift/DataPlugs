/*
 * Copyright (C) HAT Data Exchange Ltd - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.modules

import com.google.inject.AbstractModule
import net.codingwell.scalaguice.ScalaModule
import org.hatdex.dataplug.actors.DataPlugManagerActor
import org.hatdex.dataplug.controllers.{ DataPlugViewSet, DataPlugViewSetDefault }
import play.api.libs.concurrent.AkkaGuiceSupport

class DiActorsModule extends AbstractModule with ScalaModule with AkkaGuiceSupport {

  def configure = {
    bindActor[DataPlugManagerActor]("dataPlugManager")
    bind[org.hatdex.commonPlay.utils.Mailer].to[org.hatdex.dataplug.utils.Mailer]
  }
}
