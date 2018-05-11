package org.hatdex.dataplug.services

import akka.{ Done, NotUsed }
import akka.stream.scaladsl.Source
import javax.inject.Inject
import org.hatdex.dataplug.dal.Tables
import org.hatdex.dataplug.actors.IoExecutionContext
import org.hatdex.dataplug.models.HatAccessCredentials
import org.hatdex.libs.dal.SlickPostgresDriver
import org.hatdex.libs.dal.SlickPostgresDriver.api._
import org.joda.time.DateTime
import play.api.Logger
import play.api.db.slick.{ DatabaseConfigProvider, HasDatabaseConfigProvider }

import scala.concurrent.Future

class HatTokenService @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[SlickPostgresDriver] {
  implicit val ec = IoExecutionContext.ioThreadPool

  protected val logger = Logger(this.getClass)

  def forUser(hat: String, issuedSince: Option[DateTime] = None): Future[Option[HatAccessCredentials]] = {
    val newerThan = issuedSince.getOrElse(DateTime.now().minusDays(3)).toLocalDateTime
    val q = Tables.HatToken.filter(h => h.hat === hat && h.dateCreated > newerThan)

    db.run(q.result).map(_.headOption.map(h => HatAccessCredentials(h.hat, h.accessToken)))
  }

  def allValid(issuedSince: Option[DateTime] = None): Future[Seq[HatAccessCredentials]] = {
    val newerThan = issuedSince.getOrElse(DateTime.now().minusDays(3)).toLocalDateTime
    val q = Tables.HatToken.filter(_.dateCreated > newerThan)

    db.run(q.result).map(_.map(t => HatAccessCredentials(t.hat, t.accessToken)))
  }

  def allValidStream(issuedSince: Option[DateTime] = None): Source[HatAccessCredentials, NotUsed] = {
    val newerThan = issuedSince.getOrElse(DateTime.now().minusDays(3)).toLocalDateTime
    val q = Tables.HatToken.filter(_.dateCreated > newerThan)

    Source.fromPublisher(db.stream(q.result.transactionally.withStatementParameters(fetchSize = 1000)))
      .map(t => HatAccessCredentials(t.hat, t.accessToken))
  }

  def save(hat: String, token: String, issued: DateTime): Future[Done] = {
    val row = Tables.HatTokenRow(hat, token, issued.toLocalDateTime)
    val action = Tables.HatToken.insertOrUpdate(row)

    db.run(action).map(_ => Done)
  }
}
