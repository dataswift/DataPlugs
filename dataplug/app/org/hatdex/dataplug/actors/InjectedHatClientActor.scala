///*
// * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
// * This Source Code Form is subject to the terms of the Mozilla Public
// * License, v. 2.0. If a copy of the MPL was not distributed with this
// * file, You can obtain one at http://mozilla.org/MPL/2.0/.
// * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
// */
//
//package org.hatdex.dataplug.actors
//
//import javax.inject.Inject
//
//import akka.actor.{ Actor, _ }
//import com.google.inject.assistedinject.Assisted
//import com.typesafe.config.Config
//import org.hatdex.dataplug.models.HatClientCredentials
//import play.api.libs.ws.WSClient
//
//class InjectedHatClientActor @Inject() (
//    wsClient: WSClient,
//    @Assisted hat: String,
//    @Assisted config: Config,
//    @Assisted credentials: HatClientCredentials) extends HatClientActor(wsClient, hat, config, credentials) with Stash {
//
//}
//
//object InjectedHatClientActor {
//
//  trait Factory {
//    def apply(hat: String, config: Config, credentials: HatClientCredentials): Actor
//  }
//
//}