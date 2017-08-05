/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.apiInterfaces

import com.mohiva.play.silhouette.api.Provider
import org.apache.commons.lang3.reflect.TypeUtils

import scala.reflect.ClassTag

case class DataPlugOptionsCollectorRegistry(collectors: Seq[(Provider, DataPlugOptionsCollector)]) {
  /**
   * Gets a specific provider by its type.
   *
   * @tparam T The type of the provider.
   * @return Some specific provider type or None if no provider for the given type could be found.
   */
  def get[T <: DataPlugOptionsCollector: ClassTag]: Option[T] = {
    collectors.find(p => TypeUtils.isInstance(p._2, implicitly[ClassTag[T]].runtimeClass)).map(_.asInstanceOf[T])
  }

  /**
   * Gets a specific provider by its ID.
   *
   * @param id The ID of the provider to return.
   * @return Some social provider or None if no provider for the given ID could be found.
   */
  def get[T <: DataPlugOptionsCollector: ClassTag](name: String): Option[T] = getSeq[T].find(_.endpoint == name)

  /**
   * Gets a list of providers that match a certain type.
   *
   * @tparam T The type of the provider.
   * @return A list of providers that match a certain type.
   */
  def getSeq[T <: DataPlugOptionsCollector: ClassTag]: Seq[T] = {
    collectors.filter(p => TypeUtils.isInstance(p._2, implicitly[ClassTag[T]].runtimeClass)).map(_._2.asInstanceOf[T])
  }

  /**
   * Gets a list of providers that match a certain type.
   *
   * @tparam T The type of the provider.
   * @return A list of providers that match a certain type.
   */
  def getSeqProvider[T <: Provider: ClassTag, U <: DataPlugOptionsCollector: ClassTag]: Seq[U] = {
    collectors.filter(p => TypeUtils.isInstance(p._1, implicitly[ClassTag[T]].runtimeClass)).map(_._2.asInstanceOf[U])
  }
}

