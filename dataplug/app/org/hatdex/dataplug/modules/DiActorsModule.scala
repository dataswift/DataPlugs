/*
 * Copyright (C) HAT Data Exchange Ltd - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.modules

import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport

class DiActorsModule extends AbstractModule with AkkaGuiceSupport {

  def configure = {
    bind(classOf[org.hatdex.commonPlay.utils.Mailer]).to(classOf[org.hatdex.dataplug.utils.Mailer])
  }
}
