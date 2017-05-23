/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.modules

import com.google.inject.AbstractModule
import net.codingwell.scalaguice.ScalaModule
import org.hatdex.dataplug.actors.{ DataPlugManagerActor, DataPlugSyncDispatcherActor }
import org.hatdex.dataplug.controllers.{ DataPlugViewSet, DataPlugViewSetDefault }
import play.api.libs.concurrent.AkkaGuiceSupport

class DiActorsModule extends AbstractModule with ScalaModule with AkkaGuiceSupport {

  def configure = {
    bindActor[DataPlugManagerActor]("dataPlugManager")
    bindActor[DataPlugSyncDispatcherActor]("syncDispatcher")
    bind[org.hatdex.commonPlay.utils.Mailer].to[org.hatdex.dataplug.utils.Mailer]
  }
}
