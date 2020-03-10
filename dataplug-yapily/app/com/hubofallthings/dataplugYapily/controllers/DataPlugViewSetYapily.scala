/*
 * Copyright (C) 2020 Dataswift Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Marios Tsekis <marios.tsekis@dataswift.io>, 3 2020
 */

package com.hubofallthings.dataplugYapily.controllers

import com.hubofallthings.dataplug.controllers.DataPlugViewSetDefault
import play.api.mvc.Call

class DataPlugViewSetYapily extends DataPlugViewSetDefault {

  override def indexRedirect: Call = com.hubofallthings.dataplugYapily.controllers.routes.YapilyApplication.index()
}
