/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplug.services

import javax.inject.Singleton
import akka.actor.ActorRef
import akka.event.{ EventBus, SubchannelClassification }
import akka.util.Subclassification
import com.hubofallthings.dataplug.apiInterfaces.models.ApiEndpointVariantChoice
import com.hubofallthings.dataplug.models.User
import org.joda.time.DateTime

/**
 * Publishes the payload of the MsgEnvelope when the topic of the
 * MsgEnvelope equals the String specified when subscribing.
 */
@Singleton
class SubscriptionEventBus extends EventBus with SubchannelClassification {
  import SubscriptionEventBus._

  type Event = SubscriptionEvent
  type Classifier = Class[_ <: SubscriptionEvent]
  type Subscriber = ActorRef

  protected def compareSubscribers(a: Subscriber, b: Subscriber) = a compareTo b

  /**
   * The logic to form sub-class hierarchy
   */
  override protected implicit val subclassification = new Subclassification[Classifier] {
    def isEqual(x: Classifier, y: Classifier): Boolean = x == y
    def isSubclass(x: Classifier, y: Classifier): Boolean = y.isAssignableFrom(x)
  }

  /**
   * Publishes the given Event to the given Subscriber.
   *
   * @param event The Event to publish.
   * @param subscriber The Subscriber to which the Event should be published.
   */
  override protected def publish(event: Event, subscriber: Subscriber): Unit = subscriber ! event

  /**
   * Returns the Classifier associated with the given Event.
   *
   * @param event The event for which the Classifier should be returned.
   * @return The Classifier for the given Event.
   */
  override protected def classify(event: Event): Classifier = event.getClass
}

object SubscriptionEventBus {

  sealed trait SubscriptionEvent {
    val user: User
    val time: DateTime
  }

  case class UserSubscribedEvent(user: User, time: DateTime, variantChoice: ApiEndpointVariantChoice) extends SubscriptionEvent

  case class UserUnsubscribedEvent(user: User, time: DateTime, variantChoice: ApiEndpointVariantChoice) extends SubscriptionEvent

}
