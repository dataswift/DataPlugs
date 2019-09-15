/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 2, 2017
 */

package com.hubofallthings.dataplugInstagram.controllers

import com.google.inject.Inject
import com.hubofallthings.dataplug.actors.IoExecutionContext
import com.hubofallthings.dataplug.services.{ DataPlugEndpointService, DataPlugNotablesService, DataplugSyncerActorManager }
import com.hubofallthings.dataplug.utils.{ JwtPhataAuthenticatedAction, JwtPhataAwareAction }
import play.api.mvc._
import play.api.{ Configuration, Logger }

class Api @Inject() (
    components: ControllerComponents,
    configuration: Configuration,
    tokenUserAwareAction: JwtPhataAwareAction,
    tokenUserAuthenticatedAction: JwtPhataAuthenticatedAction,
    dataPlugEndpointService: DataPlugEndpointService,
    dataPlugNotablesService: DataPlugNotablesService,
    syncerActorManager: DataplugSyncerActorManager) extends AbstractController(components) {

  val logger: Logger = Logger(this.getClass)

  val ioEC = IoExecutionContext.ioThreadPool
}