package org.hatdex.dataplug.dal
// AUTO-GENERATED Slick data model
/** Stand-alone Slick data model for immediate use */
object Tables extends {
  val profile = org.hatdex.libs.dal.HATPostgresProfile
} with Tables
/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait Tables {
  val profile: org.hatdex.libs.dal.HATPostgresProfile
  import profile.api._
  import slick.model.ForeignKeyAction
  // NOTE: GetResult mappers for plain SQL are only generated for tables where Slick knows how to map the types of all columns.
  import slick.jdbc.{ GetResult => GR }

  /** DDL for all tables. Call .create to execute. */
  lazy val schema: profile.SchemaDescription = Array(DataplugEndpoint.schema, DataplugUser.schema, HatToken.schema, LogDataplugUser.schema, LogDataplugUserStatus.schema, SharedNotables.schema, UserLink.schema, UserLinkedUser.schema, UserOauth1Info.schema, UserOauth2Info.schema, UserUser.schema).reduceLeft(_ ++ _)
  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema

  /**
   * Entity class storing rows of table DataplugEndpoint
   *  @param name Database column name SqlType(varchar), PrimaryKey
   *  @param description Database column description SqlType(varchar)
   *  @param details Database column details SqlType(varchar), Default(None)
   */
  case class DataplugEndpointRow(name: String, description: String, details: Option[String] = None)
  /** GetResult implicit for fetching DataplugEndpointRow objects using plain SQL queries */
  implicit def GetResultDataplugEndpointRow(implicit e0: GR[String], e1: GR[Option[String]]): GR[DataplugEndpointRow] = GR {
    prs =>
      import prs._
      DataplugEndpointRow.tupled((<<[String], <<[String], <<?[String]))
  }
  /** Table description of table dataplug_endpoint. Objects of this class serve as prototypes for rows in queries. */
  class DataplugEndpoint(_tableTag: Tag) extends profile.api.Table[DataplugEndpointRow](_tableTag, "dataplug_endpoint") {
    def * = (name, description, details) <> (DataplugEndpointRow.tupled, DataplugEndpointRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(name), Rep.Some(description), details).shaped.<>({ r => import r._; _1.map(_ => DataplugEndpointRow.tupled((_1.get, _2.get, _3))) }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column name SqlType(varchar), PrimaryKey */
    val name: Rep[String] = column[String]("name", O.PrimaryKey)
    /** Database column description SqlType(varchar) */
    val description: Rep[String] = column[String]("description")
    /** Database column details SqlType(varchar), Default(None) */
    val details: Rep[Option[String]] = column[Option[String]]("details", O.Default(None))
  }
  /** Collection-like TableQuery object for table DataplugEndpoint */
  lazy val DataplugEndpoint = new TableQuery(tag => new DataplugEndpoint(tag))

  /**
   * Entity class storing rows of table DataplugUser
   *  @param id Database column id SqlType(bigserial), AutoInc, PrimaryKey
   *  @param phata Database column phata SqlType(varchar)
   *  @param dataplugEndpoint Database column dataplug_endpoint SqlType(varchar)
   *  @param endpointConfiguration Database column endpoint_configuration SqlType(jsonb), Default(None)
   *  @param endpointVariant Database column endpoint_variant SqlType(varchar), Default(None)
   *  @param endpointVariantDescription Database column endpoint_variant_description SqlType(varchar), Default(None)
   *  @param active Database column active SqlType(bool)
   *  @param created Database column created SqlType(timestamp)
   */
  case class DataplugUserRow(id: Long, phata: String, dataplugEndpoint: String, endpointConfiguration: Option[play.api.libs.json.JsValue] = None, endpointVariant: Option[String] = None, endpointVariantDescription: Option[String] = None, active: Boolean, created: org.joda.time.LocalDateTime)
  /** GetResult implicit for fetching DataplugUserRow objects using plain SQL queries */
  implicit def GetResultDataplugUserRow(implicit e0: GR[Long], e1: GR[String], e2: GR[Option[play.api.libs.json.JsValue]], e3: GR[Option[String]], e4: GR[Boolean], e5: GR[org.joda.time.LocalDateTime]): GR[DataplugUserRow] = GR {
    prs =>
      import prs._
      DataplugUserRow.tupled((<<[Long], <<[String], <<[String], <<?[play.api.libs.json.JsValue], <<?[String], <<?[String], <<[Boolean], <<[org.joda.time.LocalDateTime]))
  }
  /** Table description of table dataplug_user. Objects of this class serve as prototypes for rows in queries. */
  class DataplugUser(_tableTag: Tag) extends profile.api.Table[DataplugUserRow](_tableTag, "dataplug_user") {
    def * = (id, phata, dataplugEndpoint, endpointConfiguration, endpointVariant, endpointVariantDescription, active, created) <> (DataplugUserRow.tupled, DataplugUserRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(phata), Rep.Some(dataplugEndpoint), endpointConfiguration, endpointVariant, endpointVariantDescription, Rep.Some(active), Rep.Some(created)).shaped.<>({ r => import r._; _1.map(_ => DataplugUserRow.tupled((_1.get, _2.get, _3.get, _4, _5, _6, _7.get, _8.get))) }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(bigserial), AutoInc, PrimaryKey */
    val id: Rep[Long] = column[Long]("id", O.AutoInc, O.PrimaryKey)
    /** Database column phata SqlType(varchar) */
    val phata: Rep[String] = column[String]("phata")
    /** Database column dataplug_endpoint SqlType(varchar) */
    val dataplugEndpoint: Rep[String] = column[String]("dataplug_endpoint")
    /** Database column endpoint_configuration SqlType(jsonb), Default(None) */
    val endpointConfiguration: Rep[Option[play.api.libs.json.JsValue]] = column[Option[play.api.libs.json.JsValue]]("endpoint_configuration", O.Default(None))
    /** Database column endpoint_variant SqlType(varchar), Default(None) */
    val endpointVariant: Rep[Option[String]] = column[Option[String]]("endpoint_variant", O.Default(None))
    /** Database column endpoint_variant_description SqlType(varchar), Default(None) */
    val endpointVariantDescription: Rep[Option[String]] = column[Option[String]]("endpoint_variant_description", O.Default(None))
    /** Database column active SqlType(bool) */
    val active: Rep[Boolean] = column[Boolean]("active")
    /** Database column created SqlType(timestamp) */
    val created: Rep[org.joda.time.LocalDateTime] = column[org.joda.time.LocalDateTime]("created")

    /** Foreign key referencing DataplugEndpoint (database name dataplug_user_dataplug_endpoint_fkey) */
    lazy val dataplugEndpointFk = foreignKey("dataplug_user_dataplug_endpoint_fkey", dataplugEndpoint, DataplugEndpoint)(r => r.name, onUpdate = ForeignKeyAction.NoAction, onDelete = ForeignKeyAction.NoAction)

    /** Uniqueness Index over (phata,dataplugEndpoint,endpointVariant) (database name dataplug_user_link_unique) */
    val index1 = index("dataplug_user_link_unique", (phata, dataplugEndpoint, endpointVariant), unique = true)
  }
  /** Collection-like TableQuery object for table DataplugUser */
  lazy val DataplugUser = new TableQuery(tag => new DataplugUser(tag))

  /**
   * Entity class storing rows of table HatToken
   *  @param hat Database column hat SqlType(varchar), PrimaryKey
   *  @param accessToken Database column access_token SqlType(varchar)
   *  @param dateCreated Database column date_created SqlType(timestamp)
   */
  case class HatTokenRow(hat: String, accessToken: String, dateCreated: org.joda.time.LocalDateTime)
  /** GetResult implicit for fetching HatTokenRow objects using plain SQL queries */
  implicit def GetResultHatTokenRow(implicit e0: GR[String], e1: GR[org.joda.time.LocalDateTime]): GR[HatTokenRow] = GR {
    prs =>
      import prs._
      HatTokenRow.tupled((<<[String], <<[String], <<[org.joda.time.LocalDateTime]))
  }
  /** Table description of table hat_token. Objects of this class serve as prototypes for rows in queries. */
  class HatToken(_tableTag: Tag) extends profile.api.Table[HatTokenRow](_tableTag, "hat_token") {
    def * = (hat, accessToken, dateCreated) <> (HatTokenRow.tupled, HatTokenRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(hat), Rep.Some(accessToken), Rep.Some(dateCreated)).shaped.<>({ r => import r._; _1.map(_ => HatTokenRow.tupled((_1.get, _2.get, _3.get))) }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column hat SqlType(varchar), PrimaryKey */
    val hat: Rep[String] = column[String]("hat", O.PrimaryKey)
    /** Database column access_token SqlType(varchar) */
    val accessToken: Rep[String] = column[String]("access_token")
    /** Database column date_created SqlType(timestamp) */
    val dateCreated: Rep[org.joda.time.LocalDateTime] = column[org.joda.time.LocalDateTime]("date_created")
  }
  /** Collection-like TableQuery object for table HatToken */
  lazy val HatToken = new TableQuery(tag => new HatToken(tag))

  /**
   * Entity class storing rows of table LogDataplugUser
   *  @param id Database column id SqlType(bigserial), AutoInc, PrimaryKey
   *  @param phata Database column phata SqlType(varchar)
   *  @param dataplugEndpoint Database column dataplug_endpoint SqlType(varchar)
   *  @param endpointConfiguration Database column endpoint_configuration SqlType(jsonb)
   *  @param endpointVariant Database column endpoint_variant SqlType(varchar), Default(None)
   *  @param created Database column created SqlType(timestamp)
   *  @param successful Database column successful SqlType(bool)
   *  @param message Database column message SqlType(varchar), Default(None)
   */
  case class LogDataplugUserRow(id: Long, phata: String, dataplugEndpoint: String, endpointConfiguration: play.api.libs.json.JsValue, endpointVariant: Option[String] = None, created: org.joda.time.LocalDateTime, successful: Boolean, message: Option[String] = None)
  /** GetResult implicit for fetching LogDataplugUserRow objects using plain SQL queries */
  implicit def GetResultLogDataplugUserRow(implicit e0: GR[Long], e1: GR[String], e2: GR[play.api.libs.json.JsValue], e3: GR[Option[String]], e4: GR[org.joda.time.LocalDateTime], e5: GR[Boolean]): GR[LogDataplugUserRow] = GR {
    prs =>
      import prs._
      LogDataplugUserRow.tupled((<<[Long], <<[String], <<[String], <<[play.api.libs.json.JsValue], <<?[String], <<[org.joda.time.LocalDateTime], <<[Boolean], <<?[String]))
  }
  /** Table description of table log_dataplug_user. Objects of this class serve as prototypes for rows in queries. */
  class LogDataplugUser(_tableTag: Tag) extends profile.api.Table[LogDataplugUserRow](_tableTag, "log_dataplug_user") {
    def * = (id, phata, dataplugEndpoint, endpointConfiguration, endpointVariant, created, successful, message) <> (LogDataplugUserRow.tupled, LogDataplugUserRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(phata), Rep.Some(dataplugEndpoint), Rep.Some(endpointConfiguration), endpointVariant, Rep.Some(created), Rep.Some(successful), message).shaped.<>({ r => import r._; _1.map(_ => LogDataplugUserRow.tupled((_1.get, _2.get, _3.get, _4.get, _5, _6.get, _7.get, _8))) }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(bigserial), AutoInc, PrimaryKey */
    val id: Rep[Long] = column[Long]("id", O.AutoInc, O.PrimaryKey)
    /** Database column phata SqlType(varchar) */
    val phata: Rep[String] = column[String]("phata")
    /** Database column dataplug_endpoint SqlType(varchar) */
    val dataplugEndpoint: Rep[String] = column[String]("dataplug_endpoint")
    /** Database column endpoint_configuration SqlType(jsonb) */
    val endpointConfiguration: Rep[play.api.libs.json.JsValue] = column[play.api.libs.json.JsValue]("endpoint_configuration")
    /** Database column endpoint_variant SqlType(varchar), Default(None) */
    val endpointVariant: Rep[Option[String]] = column[Option[String]]("endpoint_variant", O.Default(None))
    /** Database column created SqlType(timestamp) */
    val created: Rep[org.joda.time.LocalDateTime] = column[org.joda.time.LocalDateTime]("created")
    /** Database column successful SqlType(bool) */
    val successful: Rep[Boolean] = column[Boolean]("successful")
    /** Database column message SqlType(varchar), Default(None) */
    val message: Rep[Option[String]] = column[Option[String]]("message", O.Default(None))

    /** Foreign key referencing DataplugEndpoint (database name log_dataplug_user_dataplug_endpoint_fkey) */
    lazy val dataplugEndpointFk = foreignKey("log_dataplug_user_dataplug_endpoint_fkey", dataplugEndpoint, DataplugEndpoint)(r => r.name, onUpdate = ForeignKeyAction.NoAction, onDelete = ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table LogDataplugUser */
  lazy val LogDataplugUser = new TableQuery(tag => new LogDataplugUser(tag))

  /**
   * Entity class storing rows of table LogDataplugUserStatus
   *  @param id Database column id SqlType(bigserial), AutoInc, PrimaryKey
   *  @param phata Database column phata SqlType(varchar)
   *  @param dataplugEndpoint Database column dataplug_endpoint SqlType(varchar)
   *  @param endpointConfiguration Database column endpoint_configuration SqlType(jsonb)
   *  @param endpointVariant Database column endpoint_variant SqlType(varchar), Default(None)
   *  @param created Database column created SqlType(timestamp)
   *  @param updated Database column updated SqlType(timestamp)
   *  @param successful Database column successful SqlType(bool)
   *  @param message Database column message SqlType(varchar), Default(None)
   */
  case class LogDataplugUserStatusRow(id: Long, phata: String, dataplugEndpoint: String, endpointConfiguration: play.api.libs.json.JsValue, endpointVariant: Option[String] = None, created: org.joda.time.LocalDateTime, updated: org.joda.time.LocalDateTime, successful: Boolean, message: Option[String] = None)
  /** GetResult implicit for fetching LogDataplugUserStatusRow objects using plain SQL queries */
  implicit def GetResultLogDataplugUserStatusRow(implicit e0: GR[Long], e1: GR[String], e2: GR[play.api.libs.json.JsValue], e3: GR[Option[String]], e4: GR[org.joda.time.LocalDateTime], e5: GR[Boolean]): GR[LogDataplugUserStatusRow] = GR {
    prs =>
      import prs._
      LogDataplugUserStatusRow.tupled((<<[Long], <<[String], <<[String], <<[play.api.libs.json.JsValue], <<?[String], <<[org.joda.time.LocalDateTime], <<[org.joda.time.LocalDateTime], <<[Boolean], <<?[String]))
  }
  /** Table description of table log_dataplug_user_status. Objects of this class serve as prototypes for rows in queries. */
  class LogDataplugUserStatus(_tableTag: Tag) extends profile.api.Table[LogDataplugUserStatusRow](_tableTag, "log_dataplug_user_status") {
    def * = (id, phata, dataplugEndpoint, endpointConfiguration, endpointVariant, created, updated, successful, message) <> (LogDataplugUserStatusRow.tupled, LogDataplugUserStatusRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(phata), Rep.Some(dataplugEndpoint), Rep.Some(endpointConfiguration), endpointVariant, Rep.Some(created), Rep.Some(updated), Rep.Some(successful), message).shaped.<>({ r => import r._; _1.map(_ => LogDataplugUserStatusRow.tupled((_1.get, _2.get, _3.get, _4.get, _5, _6.get, _7.get, _8.get, _9))) }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(bigserial), AutoInc, PrimaryKey */
    val id: Rep[Long] = column[Long]("id", O.AutoInc, O.PrimaryKey)
    /** Database column phata SqlType(varchar) */
    val phata: Rep[String] = column[String]("phata")
    /** Database column dataplug_endpoint SqlType(varchar) */
    val dataplugEndpoint: Rep[String] = column[String]("dataplug_endpoint")
    /** Database column endpoint_configuration SqlType(jsonb) */
    val endpointConfiguration: Rep[play.api.libs.json.JsValue] = column[play.api.libs.json.JsValue]("endpoint_configuration")
    /** Database column endpoint_variant SqlType(varchar), Default(None) */
    val endpointVariant: Rep[Option[String]] = column[Option[String]]("endpoint_variant", O.Default(None))
    /** Database column created SqlType(timestamp) */
    val created: Rep[org.joda.time.LocalDateTime] = column[org.joda.time.LocalDateTime]("created")
    /** Database column updated SqlType(timestamp) */
    val updated: Rep[org.joda.time.LocalDateTime] = column[org.joda.time.LocalDateTime]("updated")
    /** Database column successful SqlType(bool) */
    val successful: Rep[Boolean] = column[Boolean]("successful")
    /** Database column message SqlType(varchar), Default(None) */
    val message: Rep[Option[String]] = column[Option[String]]("message", O.Default(None))

    /** Foreign key referencing DataplugEndpoint (database name log_dataplug_user_status_dataplug_endpoint_fkey) */
    lazy val dataplugEndpointFk = foreignKey("log_dataplug_user_status_dataplug_endpoint_fkey", dataplugEndpoint, DataplugEndpoint)(r => r.name, onUpdate = ForeignKeyAction.NoAction, onDelete = ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table LogDataplugUserStatus */
  lazy val LogDataplugUserStatus = new TableQuery(tag => new LogDataplugUserStatus(tag))

  /**
   * Entity class storing rows of table SharedNotables
   *  @param id Database column id SqlType(varchar), PrimaryKey
   *  @param createdTime Database column created_time SqlType(timestamp)
   *  @param phata Database column phata SqlType(varchar)
   *  @param posted Database column posted SqlType(bool)
   *  @param postedTime Database column posted_time SqlType(timestamp), Default(None)
   *  @param providerId Database column provider_id SqlType(varchar), Default(None)
   *  @param deleted Database column deleted SqlType(bool)
   *  @param deletedTime Database column deleted_time SqlType(timestamp), Default(None)
   */
  case class SharedNotablesRow(id: String, createdTime: org.joda.time.LocalDateTime, phata: String, posted: Boolean, postedTime: Option[org.joda.time.LocalDateTime] = None, providerId: Option[String] = None, deleted: Boolean, deletedTime: Option[org.joda.time.LocalDateTime] = None)
  /** GetResult implicit for fetching SharedNotablesRow objects using plain SQL queries */
  implicit def GetResultSharedNotablesRow(implicit e0: GR[String], e1: GR[org.joda.time.LocalDateTime], e2: GR[Boolean], e3: GR[Option[org.joda.time.LocalDateTime]], e4: GR[Option[String]]): GR[SharedNotablesRow] = GR {
    prs =>
      import prs._
      SharedNotablesRow.tupled((<<[String], <<[org.joda.time.LocalDateTime], <<[String], <<[Boolean], <<?[org.joda.time.LocalDateTime], <<?[String], <<[Boolean], <<?[org.joda.time.LocalDateTime]))
  }
  /** Table description of table shared_notables. Objects of this class serve as prototypes for rows in queries. */
  class SharedNotables(_tableTag: Tag) extends profile.api.Table[SharedNotablesRow](_tableTag, "shared_notables") {
    def * = (id, createdTime, phata, posted, postedTime, providerId, deleted, deletedTime) <> (SharedNotablesRow.tupled, SharedNotablesRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(createdTime), Rep.Some(phata), Rep.Some(posted), postedTime, providerId, Rep.Some(deleted), deletedTime).shaped.<>({ r => import r._; _1.map(_ => SharedNotablesRow.tupled((_1.get, _2.get, _3.get, _4.get, _5, _6, _7.get, _8))) }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(varchar), PrimaryKey */
    val id: Rep[String] = column[String]("id", O.PrimaryKey)
    /** Database column created_time SqlType(timestamp) */
    val createdTime: Rep[org.joda.time.LocalDateTime] = column[org.joda.time.LocalDateTime]("created_time")
    /** Database column phata SqlType(varchar) */
    val phata: Rep[String] = column[String]("phata")
    /** Database column posted SqlType(bool) */
    val posted: Rep[Boolean] = column[Boolean]("posted")
    /** Database column posted_time SqlType(timestamp), Default(None) */
    val postedTime: Rep[Option[org.joda.time.LocalDateTime]] = column[Option[org.joda.time.LocalDateTime]]("posted_time", O.Default(None))
    /** Database column provider_id SqlType(varchar), Default(None) */
    val providerId: Rep[Option[String]] = column[Option[String]]("provider_id", O.Default(None))
    /** Database column deleted SqlType(bool) */
    val deleted: Rep[Boolean] = column[Boolean]("deleted")
    /** Database column deleted_time SqlType(timestamp), Default(None) */
    val deletedTime: Rep[Option[org.joda.time.LocalDateTime]] = column[Option[org.joda.time.LocalDateTime]]("deleted_time", O.Default(None))
  }
  /** Collection-like TableQuery object for table SharedNotables */
  lazy val SharedNotables = new TableQuery(tag => new SharedNotables(tag))

  /**
   * Entity class storing rows of table UserLink
   *  @param linkId Database column link_id SqlType(bigserial), AutoInc, PrimaryKey
   *  @param masterProviderId Database column master_provider_id SqlType(varchar)
   *  @param masterUserId Database column master_user_id SqlType(varchar)
   *  @param linkedProviderId Database column linked_provider_id SqlType(varchar)
   *  @param linkedUserId Database column linked_user_id SqlType(varchar)
   *  @param created Database column created SqlType(timestamp)
   */
  case class UserLinkRow(linkId: Long, masterProviderId: String, masterUserId: String, linkedProviderId: String, linkedUserId: String, created: org.joda.time.LocalDateTime)
  /** GetResult implicit for fetching UserLinkRow objects using plain SQL queries */
  implicit def GetResultUserLinkRow(implicit e0: GR[Long], e1: GR[String], e2: GR[org.joda.time.LocalDateTime]): GR[UserLinkRow] = GR {
    prs =>
      import prs._
      UserLinkRow.tupled((<<[Long], <<[String], <<[String], <<[String], <<[String], <<[org.joda.time.LocalDateTime]))
  }
  /** Table description of table user_link. Objects of this class serve as prototypes for rows in queries. */
  class UserLink(_tableTag: Tag) extends profile.api.Table[UserLinkRow](_tableTag, "user_link") {
    def * = (linkId, masterProviderId, masterUserId, linkedProviderId, linkedUserId, created) <> (UserLinkRow.tupled, UserLinkRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(linkId), Rep.Some(masterProviderId), Rep.Some(masterUserId), Rep.Some(linkedProviderId), Rep.Some(linkedUserId), Rep.Some(created)).shaped.<>({ r => import r._; _1.map(_ => UserLinkRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get))) }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column link_id SqlType(bigserial), AutoInc, PrimaryKey */
    val linkId: Rep[Long] = column[Long]("link_id", O.AutoInc, O.PrimaryKey)
    /** Database column master_provider_id SqlType(varchar) */
    val masterProviderId: Rep[String] = column[String]("master_provider_id")
    /** Database column master_user_id SqlType(varchar) */
    val masterUserId: Rep[String] = column[String]("master_user_id")
    /** Database column linked_provider_id SqlType(varchar) */
    val linkedProviderId: Rep[String] = column[String]("linked_provider_id")
    /** Database column linked_user_id SqlType(varchar) */
    val linkedUserId: Rep[String] = column[String]("linked_user_id")
    /** Database column created SqlType(timestamp) */
    val created: Rep[org.joda.time.LocalDateTime] = column[org.joda.time.LocalDateTime]("created")

    /** Foreign key referencing UserUser (database name user_link_linked_provider_id_fkey) */
    lazy val userUserFk1 = foreignKey("user_link_linked_provider_id_fkey", (linkedProviderId, linkedUserId), UserUser)(r => (r.providerId, r.userId), onUpdate = ForeignKeyAction.NoAction, onDelete = ForeignKeyAction.NoAction)
    /** Foreign key referencing UserUser (database name user_link_master_provider_id_fkey) */
    lazy val userUserFk2 = foreignKey("user_link_master_provider_id_fkey", (masterProviderId, masterUserId), UserUser)(r => (r.providerId, r.userId), onUpdate = ForeignKeyAction.NoAction, onDelete = ForeignKeyAction.NoAction)

    /** Uniqueness Index over (masterProviderId,masterUserId,linkedProviderId,linkedUserId) (database name user_link_unique) */
    val index1 = index("user_link_unique", (masterProviderId, masterUserId, linkedProviderId, linkedUserId), unique = true)
  }
  /** Collection-like TableQuery object for table UserLink */
  lazy val UserLink = new TableQuery(tag => new UserLink(tag))

  /**
   * Entity class storing rows of table UserLinkedUser
   *  @param providerId Database column provider_id SqlType(varchar), Default(None)
   *  @param userId Database column user_id SqlType(varchar), Default(None)
   *  @param created Database column created SqlType(timestamp), Default(None)
   */
  case class UserLinkedUserRow(providerId: Option[String] = None, userId: Option[String] = None, created: Option[org.joda.time.LocalDateTime] = None)
  /** GetResult implicit for fetching UserLinkedUserRow objects using plain SQL queries */
  implicit def GetResultUserLinkedUserRow(implicit e0: GR[Option[String]], e1: GR[Option[org.joda.time.LocalDateTime]]): GR[UserLinkedUserRow] = GR {
    prs =>
      import prs._
      UserLinkedUserRow.tupled((<<?[String], <<?[String], <<?[org.joda.time.LocalDateTime]))
  }
  /** Table description of table user_linked_user. Objects of this class serve as prototypes for rows in queries. */
  class UserLinkedUser(_tableTag: Tag) extends profile.api.Table[UserLinkedUserRow](_tableTag, "user_linked_user") {
    def * = (providerId, userId, created) <> (UserLinkedUserRow.tupled, UserLinkedUserRow.unapply)

    /** Database column provider_id SqlType(varchar), Default(None) */
    val providerId: Rep[Option[String]] = column[Option[String]]("provider_id", O.Default(None))
    /** Database column user_id SqlType(varchar), Default(None) */
    val userId: Rep[Option[String]] = column[Option[String]]("user_id", O.Default(None))
    /** Database column created SqlType(timestamp), Default(None) */
    val created: Rep[Option[org.joda.time.LocalDateTime]] = column[Option[org.joda.time.LocalDateTime]]("created", O.Default(None))
  }
  /** Collection-like TableQuery object for table UserLinkedUser */
  lazy val UserLinkedUser = new TableQuery(tag => new UserLinkedUser(tag))

  /**
   * Entity class storing rows of table UserOauth1Info
   *  @param providerId Database column provider_id SqlType(varchar)
   *  @param userId Database column user_id SqlType(varchar)
   *  @param token Database column token SqlType(varchar)
   *  @param secret Database column secret SqlType(varchar)
   *  @param created Database column created SqlType(timestamp)
   */
  case class UserOauth1InfoRow(providerId: String, userId: String, token: String, secret: String, created: org.joda.time.LocalDateTime)
  /** GetResult implicit for fetching UserOauth1InfoRow objects using plain SQL queries */
  implicit def GetResultUserOauth1InfoRow(implicit e0: GR[String], e1: GR[org.joda.time.LocalDateTime]): GR[UserOauth1InfoRow] = GR {
    prs =>
      import prs._
      UserOauth1InfoRow.tupled((<<[String], <<[String], <<[String], <<[String], <<[org.joda.time.LocalDateTime]))
  }
  /** Table description of table user_oauth1_info. Objects of this class serve as prototypes for rows in queries. */
  class UserOauth1Info(_tableTag: Tag) extends profile.api.Table[UserOauth1InfoRow](_tableTag, "user_oauth1_info") {
    def * = (providerId, userId, token, secret, created) <> (UserOauth1InfoRow.tupled, UserOauth1InfoRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(providerId), Rep.Some(userId), Rep.Some(token), Rep.Some(secret), Rep.Some(created)).shaped.<>({ r => import r._; _1.map(_ => UserOauth1InfoRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get))) }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column provider_id SqlType(varchar) */
    val providerId: Rep[String] = column[String]("provider_id")
    /** Database column user_id SqlType(varchar) */
    val userId: Rep[String] = column[String]("user_id")
    /** Database column token SqlType(varchar) */
    val token: Rep[String] = column[String]("token")
    /** Database column secret SqlType(varchar) */
    val secret: Rep[String] = column[String]("secret")
    /** Database column created SqlType(timestamp) */
    val created: Rep[org.joda.time.LocalDateTime] = column[org.joda.time.LocalDateTime]("created")

    /** Primary key of UserOauth1Info (database name user_oauth1_info_pkey) */
    val pk = primaryKey("user_oauth1_info_pkey", (providerId, userId))

    /** Foreign key referencing UserUser (database name user_oauth1_info_provider_id_fkey) */
    lazy val userUserFk = foreignKey("user_oauth1_info_provider_id_fkey", (providerId, userId), UserUser)(r => (r.providerId, r.userId), onUpdate = ForeignKeyAction.NoAction, onDelete = ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table UserOauth1Info */
  lazy val UserOauth1Info = new TableQuery(tag => new UserOauth1Info(tag))

  /**
   * Entity class storing rows of table UserOauth2Info
   *  @param providerId Database column provider_id SqlType(varchar)
   *  @param userId Database column user_id SqlType(varchar)
   *  @param accessToken Database column access_token SqlType(varchar)
   *  @param tokenType Database column token_type SqlType(varchar), Default(None)
   *  @param expiresIn Database column expires_in SqlType(int4), Default(None)
   *  @param refreshToken Database column refresh_token SqlType(varchar), Default(None)
   *  @param params Database column params SqlType(jsonb), Default(None)
   *  @param created Database column created SqlType(timestamp)
   */
  case class UserOauth2InfoRow(providerId: String, userId: String, accessToken: String, tokenType: Option[String] = None, expiresIn: Option[Int] = None, refreshToken: Option[String] = None, params: Option[play.api.libs.json.JsValue] = None, created: org.joda.time.LocalDateTime)
  /** GetResult implicit for fetching UserOauth2InfoRow objects using plain SQL queries */
  implicit def GetResultUserOauth2InfoRow(implicit e0: GR[String], e1: GR[Option[String]], e2: GR[Option[Int]], e3: GR[Option[play.api.libs.json.JsValue]], e4: GR[org.joda.time.LocalDateTime]): GR[UserOauth2InfoRow] = GR {
    prs =>
      import prs._
      UserOauth2InfoRow.tupled((<<[String], <<[String], <<[String], <<?[String], <<?[Int], <<?[String], <<?[play.api.libs.json.JsValue], <<[org.joda.time.LocalDateTime]))
  }
  /** Table description of table user_oauth2_info. Objects of this class serve as prototypes for rows in queries. */
  class UserOauth2Info(_tableTag: Tag) extends profile.api.Table[UserOauth2InfoRow](_tableTag, "user_oauth2_info") {
    def * = (providerId, userId, accessToken, tokenType, expiresIn, refreshToken, params, created) <> (UserOauth2InfoRow.tupled, UserOauth2InfoRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(providerId), Rep.Some(userId), Rep.Some(accessToken), tokenType, expiresIn, refreshToken, params, Rep.Some(created)).shaped.<>({ r => import r._; _1.map(_ => UserOauth2InfoRow.tupled((_1.get, _2.get, _3.get, _4, _5, _6, _7, _8.get))) }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column provider_id SqlType(varchar) */
    val providerId: Rep[String] = column[String]("provider_id")
    /** Database column user_id SqlType(varchar) */
    val userId: Rep[String] = column[String]("user_id")
    /** Database column access_token SqlType(varchar) */
    val accessToken: Rep[String] = column[String]("access_token")
    /** Database column token_type SqlType(varchar), Default(None) */
    val tokenType: Rep[Option[String]] = column[Option[String]]("token_type", O.Default(None))
    /** Database column expires_in SqlType(int4), Default(None) */
    val expiresIn: Rep[Option[Int]] = column[Option[Int]]("expires_in", O.Default(None))
    /** Database column refresh_token SqlType(varchar), Default(None) */
    val refreshToken: Rep[Option[String]] = column[Option[String]]("refresh_token", O.Default(None))
    /** Database column params SqlType(jsonb), Default(None) */
    val params: Rep[Option[play.api.libs.json.JsValue]] = column[Option[play.api.libs.json.JsValue]]("params", O.Default(None))
    /** Database column created SqlType(timestamp) */
    val created: Rep[org.joda.time.LocalDateTime] = column[org.joda.time.LocalDateTime]("created")

    /** Primary key of UserOauth2Info (database name user_oauth2_info_pkey) */
    val pk = primaryKey("user_oauth2_info_pkey", (providerId, userId))

    /** Foreign key referencing UserUser (database name user_oauth2_info_provider_id_fkey) */
    lazy val userUserFk = foreignKey("user_oauth2_info_provider_id_fkey", (providerId, userId), UserUser)(r => (r.providerId, r.userId), onUpdate = ForeignKeyAction.NoAction, onDelete = ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table UserOauth2Info */
  lazy val UserOauth2Info = new TableQuery(tag => new UserOauth2Info(tag))

  /**
   * Entity class storing rows of table UserUser
   *  @param providerId Database column provider_id SqlType(varchar)
   *  @param userId Database column user_id SqlType(varchar)
   *  @param created Database column created SqlType(timestamp)
   */
  case class UserUserRow(providerId: String, userId: String, created: org.joda.time.LocalDateTime)
  /** GetResult implicit for fetching UserUserRow objects using plain SQL queries */
  implicit def GetResultUserUserRow(implicit e0: GR[String], e1: GR[org.joda.time.LocalDateTime]): GR[UserUserRow] = GR {
    prs =>
      import prs._
      UserUserRow.tupled((<<[String], <<[String], <<[org.joda.time.LocalDateTime]))
  }
  /** Table description of table user_user. Objects of this class serve as prototypes for rows in queries. */
  class UserUser(_tableTag: Tag) extends profile.api.Table[UserUserRow](_tableTag, "user_user") {
    def * = (providerId, userId, created) <> (UserUserRow.tupled, UserUserRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(providerId), Rep.Some(userId), Rep.Some(created)).shaped.<>({ r => import r._; _1.map(_ => UserUserRow.tupled((_1.get, _2.get, _3.get))) }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column provider_id SqlType(varchar) */
    val providerId: Rep[String] = column[String]("provider_id")
    /** Database column user_id SqlType(varchar) */
    val userId: Rep[String] = column[String]("user_id")
    /** Database column created SqlType(timestamp) */
    val created: Rep[org.joda.time.LocalDateTime] = column[org.joda.time.LocalDateTime]("created")

    /** Primary key of UserUser (database name user_user_pkey) */
    val pk = primaryKey("user_user_pkey", (providerId, userId))
  }
  /** Collection-like TableQuery object for table UserUser */
  lazy val UserUser = new TableQuery(tag => new UserUser(tag))
}
