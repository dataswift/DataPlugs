/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.apiInterfaces

import scala.reflect.ClassTag
import org.apache.commons.lang3.reflect.TypeUtils

case class DataPlugRegistry(interfaces: Seq[DataPlugEndpointInterface]) {
  /**
   * Gets a specific provider by its type.
   *
   * @tparam T The type of the provider.
   * @return Some specific provider type or None if no provider for the given type could be found.
   */
  def get[T <: DataPlugEndpointInterface: ClassTag]: Option[T] = {
    interfaces.find(p => TypeUtils.isInstance(p, implicitly[ClassTag[T]].runtimeClass)).map(_.asInstanceOf[T])
  }

  /**
   * Gets a specific provider by its ID.
   *
   * @param id The ID of the provider to return.
   * @return Some social provider or None if no provider for the given ID could be found.
   */
  def get[T <: DataPlugEndpointInterface: ClassTag](name: String): Option[T] = getSeq[T].find(_.endpointName == name)

  /**
   * Gets a list of providers that match a certain type.
   *
   * @tparam T The type of the provider.
   * @return A list of providers that match a certain type.
   */
  def getSeq[T <: DataPlugEndpointInterface: ClassTag]: Seq[T] = {
    interfaces.filter(p => TypeUtils.isInstance(p, implicitly[ClassTag[T]].runtimeClass)).map(_.asInstanceOf[T])
  }
}

