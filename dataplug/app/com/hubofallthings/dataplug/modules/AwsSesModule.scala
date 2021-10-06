package com.hubofallthings.dataplug.modules

import com.amazonaws.services.simpleemail.{ AmazonSimpleEmailService, AmazonSimpleEmailServiceClientBuilder }
import com.google.inject.Provides
import net.codingwell.scalaguice.ScalaModule

import javax.inject.{ Singleton => JSingleton }

class AwsSesModule extends ScalaModule {

  override def configure(): Unit = ()

  @Provides
  @JSingleton
  def provideAwsEmailService: AmazonSimpleEmailService = AmazonSimpleEmailServiceClientBuilder.defaultClient()

}
