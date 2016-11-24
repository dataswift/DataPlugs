/*
 * Copyright (C) HAT Data Exchange Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplugCalendar

import com.google.inject.{ AbstractModule, Provides }
import net.codingwell.scalaguice.ScalaModule
import org.hatdex.dataplug.actors.{ DataPlugManagerActor, InjectedHatClientActor }
import org.hatdex.dataplug.apiInterfaces.DataPlugRegistry
import org.hatdex.dataplug.controllers.DataPlugViewSet
import org.hatdex.dataplug.dao.{ DataPlugEndpointDAO, DataPlugEndpointDAOImpl }
import org.hatdex.dataplug.services.{ DataPlugEndpointService, DataPlugEndpointServiceImpl }
import org.hatdex.dataplugCalendar.apiInterfaces.GoogleCalendarInterface
import org.hatdex.dataplugCalendar.controllers.DataPlugViewSetCalendar
import play.api.libs.concurrent.AkkaGuiceSupport

/**
 * The Guice module which wires all Silhouette dependencies.
 */
class Module extends AbstractModule with ScalaModule with AkkaGuiceSupport {

  /**
   * Configures the module.
   */
  def configure() {
    bind[DataPlugEndpointDAO].to[DataPlugEndpointDAOImpl]
    bind[DataPlugEndpointService].to[DataPlugEndpointServiceImpl]

    bind[DataPlugViewSet].to[DataPlugViewSetCalendar]

    bindActorFactory[InjectedHatClientActor, InjectedHatClientActor.Factory]
    bindActor[DataPlugManagerActor]("dataplug-manager")
  }

  /**
   * Provides the social provider registry.
   *
   * @param googleCalendarEndpoint The google calendar api endpoint implementation, injected
   * @return The DataPlugRegistry.
   */
  @Provides
  def provideDataPlugCollection(
    googleCalendarEndpoint: GoogleCalendarInterface): DataPlugRegistry = {

    DataPlugRegistry(Seq(
      googleCalendarEndpoint
    ))
  }
}