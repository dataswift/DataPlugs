package com.hubofallthings.dataplug.modules

import com.amazonaws.auth.EC2ContainerCredentialsProviderWrapper
import com.amazonaws.services.simpleemail.{ AmazonSimpleEmailService, AmazonSimpleEmailServiceClientBuilder }
import com.google.inject.Provides
import net.codingwell.scalaguice.ScalaModule
import play.api.Configuration

import javax.inject.{ Singleton => JSingleton }

class AwsSesModule extends ScalaModule {

  override def configure(): Unit = ()

  @Provides
  @JSingleton
  def provideAwsEmailService(config: Configuration): AmazonSimpleEmailService =
    AmazonSimpleEmailServiceClientBuilder
      .standard()
      .withRegion(config.get[String]("mailer.awsRegion"))
      .withCredentials(new EC2ContainerCredentialsProviderWrapper)
      .build()

}
