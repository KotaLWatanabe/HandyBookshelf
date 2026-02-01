package com.handybookshelf
package infrastructure

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.{PreparedStatement, Row}
import com.handybookshelf.domain.DomainEvent
import com.handybookshelf.util.Timestamp
import io.circe.*
import io.circe.parser.decode
import io.circe.syntax.*

import java.net.InetSocketAddress
import java.time.Instant
import scala.jdk.CollectionConverters.*

/**
 * ScyllaDB implementation of EventStore for event sourcing Provides persistent storage for domain events with optimized
 * read/write patterns
 */
class ScyllaEventStore(session: CqlSession)(using Encoder[DomainEvent], Codec[StreamMetadata]) extends EventStore:

  // Prepared statements for optimized queries
  private lazy val insertEventStmt: IO[PreparedStatement] = IO.fromCompletionStage(
    IO.pure(
      session.prepareAsync(
        """INSERT INTO event_store 
         (persistence_id, sequence_nr, event_timestamp, event_type, event_data, metadata)
         VALUES (?, ?, ?, ?, ?, ?)"""
      )
    )
  )

  private lazy val selectEventsStmt: IO[PreparedStatement] = IO.fromCompletionStage(
    IO.pure(
      session.prepareAsync(
        """SELECT persistence_id, sequence_nr, event_timestamp, event_type, event_data, metadata
         FROM event_store 
         WHERE persistence_id = ? 
         ORDER BY sequence_nr ASC"""
      )
    )
  )

  private lazy val selectEventsFromVersionStmt: IO[PreparedStatement] = IO.fromCompletionStage(
    IO.pure(
      session.prepareAsync(
        """SELECT persistence_id, sequence_nr, event_timestamp, event_type, event_data, metadata
         FROM event_store 
         WHERE persistence_id = ? AND sequence_nr >= ?
         ORDER BY sequence_nr ASC"""
      )
    )
  )

  private lazy val selectLatestSequenceStmt: IO[PreparedStatement] = IO.fromCompletionStage(
    IO.pure(
      session.prepareAsync(
        """SELECT MAX(sequence_nr) as max_seq 
         FROM event_store 
         WHERE persistence_id = ?"""
      )
    )
  )

  override def getEvents(streamId: StreamId): IO[List[StoredEvent]] =
    for {
      stmt <- selectEventsStmt
      resultSet <- IO.fromCompletionStage(
        IO.pure(
          session.executeAsync(stmt.bind(streamId.value))
        )
      )
      events <- IO.pure(resultSet.map(rowToStoredEvent).currentPage().asScala.toList)
    } yield events

  override def getEventsFromVersion(
      streamId: StreamId,
      fromVersion: EventVersion
  ): IO[List[StoredEvent]] =
    for {
      stmt <- selectEventsFromVersionStmt
      resultSet <- IO.fromCompletionStage(
        IO.pure(
          session.executeAsync(stmt.bind(streamId.value, Long.box(fromVersion.value)))
        )
      )
      events <- IO.pure(resultSet.map(rowToStoredEvent).currentPage().asScala.toList)
    } yield events

  override def saveEvents(
      streamId: StreamId,
      events: List[StoredEvent],
      expectedVersion: EventVersion
  ): IO[Unit] =
    if events.isEmpty then IO.unit
    else
      for {
        currentVersion <- getLatestSequenceNumber(streamId)
        _              <- validateVersion(currentVersion, expectedVersion)
        _              <- insertEvents(streamId, events, currentVersion)
      } yield ()

  override def getStreamMetadata(streamId: StreamId): IO[Option[StreamMetadata]] =
    for {
      latestSeq <- getLatestSequenceNumber(streamId)
      timestamp <- IO(Timestamp.now)
    } yield latestSeq.map(seq => StreamMetadata(streamId, EventVersion(seq), timestamp))

  override def streamExists(streamId: StreamId): IO[Boolean] =
    getLatestSequenceNumber(streamId).map(_.isDefined)

  private def getLatestSequenceNumber(streamId: StreamId): IO[Option[Long]] =
    for {
      stmt <- selectLatestSequenceStmt
      resultSet <- IO.fromCompletionStage(
        IO.pure(
          session.executeAsync(stmt.bind(streamId.value))
        )
      )
      maxSeq <- IO.pure {
        val row = resultSet.one()
        if row != null && !row.isNull("max_seq") then Some(row.getLong("max_seq"))
        else None
      }
    } yield maxSeq

  private def insertEvents(
      streamId: StreamId,
      events: List[StoredEvent],
      currentVersion: Option[Long]
  )(using Encoder[DomainEvent]): IO[Unit] =
    for {
      stmt <- insertEventStmt
      startSeq = currentVersion.getOrElse(0L) + 1
      _ <- events.zipWithIndex.traverse { case (event, index) =>
        val sequenceNr   = startSeq + index
        val eventJson    = event.event.asInstanceOf[DomainEvent].asJson.noSpaces
        val metadataJson = event.metadata.asJson.noSpaces
        val timestamp    = Instant.now()

        IO.fromCompletionStage(
          IO.pure(
            session.executeAsync(
              stmt.bind(
                streamId.value,
                Long.box(sequenceNr),
                timestamp,
                event.event.getClass.getSimpleName,
                eventJson,
                metadataJson
              )
            )
          )
        ).void
      }
    } yield ()

  private def rowToStoredEvent(row: Row): StoredEvent =
    val persistenceId = row.getString("persistence_id")
    val sequenceNr    = row.getLong("sequence_nr")
//    val timestamp     = row.getInstant("event_timestamp")
//    val eventType     = row.getString("event_type")
    val eventData    = row.getString("event_data")
    val metadataJson = row.getString("metadata")

    // Simple implementation - in production, you'd want proper event deserialization
    new StoredEvent {
      type E = String // Simplified for now
      def event: E = eventData
      def metadata: StreamMetadata = decode[StreamMetadata](metadataJson) match {
        case Right(meta) => meta
        case Left(_)     =>
          // Fallback metadata
          StreamMetadata(
            StreamId(persistenceId),
            EventVersion(sequenceNr),
            ???
//            Timestamp(timestamp.toEpochMilli)
          )
      }
    }

  private def validateVersion(
      currentVersion: Option[Long],
      expectedVersion: EventVersion
  ): IO[Unit] =
    if expectedVersion.isAnyVersion then IO.unit
    else
      currentVersion match
        case None if expectedVersion == EventVersion.noStream => IO.unit
        case None =>
          IO.raiseError(
            new IllegalStateException(s"Stream does not exist but expected version ${expectedVersion.value}")
          )
        case Some(current) if current == expectedVersion.value => IO.unit
        case Some(current) =>
          IO.raiseError(
            new IllegalStateException(
              s"Version mismatch. Expected: ${expectedVersion.value}, Actual: $current"
            )
          )

/**
 * ScyllaDB Session Management
 */
object ScyllaSession:

  def createSession(
      hosts: List[String] = List("localhost"),
      port: Int = 9042,
      keyspace: String = "handybookshelf",
      username: String = "cassandra",
      password: String = "cassandra"
  ): Resource[IO, CqlSession] =
    Resource.make(
      IO.blocking {
        val contactPoints = hosts.map(host => InetSocketAddress.createUnresolved(host, port)).asJava

        CqlSession
          .builder()
          .addContactPoints(contactPoints)
          .withAuthCredentials(username, password)
          .withKeyspace(keyspace)
          .build()
      }
    )(session => IO.blocking(session.close()))

  def createSessionFromEnv: Resource[IO, CqlSession] =
    Resource
      .eval(IO.blocking {
        val hosts = sys.env
          .get("SCYLLA_HOSTS")
          .map(_.split(",").toList)
          .getOrElse(List("localhost"))
        val port     = sys.env.get("SCYLLA_PORT").map(_.toInt).getOrElse(9042)
        val keyspace = sys.env.getOrElse("SCYLLA_KEYSPACE", "handybookshelf")
        val username = sys.env.getOrElse("SCYLLA_USERNAME", "cassandra")
        val password = sys.env.getOrElse("SCYLLA_PASSWORD", "cassandra")

        (hosts, port, keyspace, username, password)
      })
      .flatMap { case (hosts, port, keyspace, username, password) =>
        createSession(hosts, port, keyspace, username, password)
      }

/**
 * Repository implementations using ScyllaDB
 */
class ScyllaUserSessionRepository(session: CqlSession):

  private lazy val insertSessionStmt: IO[PreparedStatement] = IO.fromCompletionStage(
    IO.pure(
      session.prepareAsync(
        """INSERT INTO user_sessions 
         (user_account_id, session_id, created_at, last_activity, expires_at, is_active, metadata)
         VALUES (?, ?, ?, ?, ?, ?, ?)"""
      )
    )
  )

  private lazy val selectSessionStmt: IO[PreparedStatement] = IO.fromCompletionStage(
    IO.pure(
      session.prepareAsync(
        """SELECT user_account_id, session_id, created_at, last_activity, expires_at, is_active, metadata
         FROM user_sessions 
         WHERE user_account_id = ? AND session_id = ?"""
      )
    )
  )

  private lazy val updateSessionActivityStmt: IO[PreparedStatement] = IO.fromCompletionStage(
    IO.pure(
      session.prepareAsync(
        """UPDATE user_sessions 
         SET last_activity = ?, expires_at = ?
         WHERE user_account_id = ? AND session_id = ?"""
      )
    )
  )

  private lazy val deactivateSessionStmt: IO[PreparedStatement] = IO.fromCompletionStage(
    IO.pure(
      session.prepareAsync(
        """UPDATE user_sessions 
         SET is_active = false 
         WHERE user_account_id = ? AND session_id = ?"""
      )
    )
  )

  def createSession(
      userAccountId: String,
      sessionId: String,
      expiresAt: Instant,
      metadata: String = "{}"
  ): IO[Unit] =
    for {
      stmt <- insertSessionStmt
      now = Instant.now()
      _ <- IO
        .fromCompletionStage(
          IO.pure(
            session.executeAsync(
              stmt.bind(
                java.util.UUID.fromString(userAccountId),
                sessionId,
                now,
                now,
                expiresAt,
                Boolean.box(true),
                metadata
              )
            )
          )
        )
        .void
    } yield ()

  def validateSession(userAccountId: String, sessionId: String): IO[Boolean] =
    for {
      stmt <- selectSessionStmt
      resultSet <- IO.fromCompletionStage(
        IO.pure(
          session.executeAsync(
            stmt.bind(
              java.util.UUID.fromString(userAccountId),
              sessionId
            )
          )
        )
      )
      isValid <- IO.pure {
        val row = resultSet.one()
        if row != null then
          val isActive  = row.getBoolean("is_active")
          val expiresAt = row.getInstant("expires_at")
          isActive && expiresAt.isAfter(Instant.now())
        else false
      }
    } yield isValid

  def updateActivity(userAccountId: String, sessionId: String, newExpiresAt: Instant): IO[Unit] =
    for {
      stmt <- updateSessionActivityStmt
      now = Instant.now()
      _ <- IO
        .fromCompletionStage(
          IO.pure(
            session.executeAsync(
              stmt.bind(
                now,
                newExpiresAt,
                java.util.UUID.fromString(userAccountId),
                sessionId
              )
            )
          )
        )
        .void
    } yield ()

  def deactivateSession(userAccountId: String, sessionId: String): IO[Unit] =
    for {
      stmt <- deactivateSessionStmt
      _ <- IO
        .fromCompletionStage(
          IO.pure(
            session.executeAsync(
              stmt.bind(
                java.util.UUID.fromString(userAccountId),
                sessionId
              )
            )
          )
        )
        .void
    } yield ()

//  /**
//   * Given instances for JSON serialization
//   */
//  given Codec[StreamMetadata] = io.circe.generic.semiauto.deriveCodec[StreamMetadata]
