package com.handybookshelf
package infrastructure

import cats.effect.IO
import com.handybookshelf.domain.DomainEvent
import com.handybookshelf.util.Timestamp
import io.circe.*
import io.circe.parser.decode
import io.circe.syntax.*
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*

import java.time.Instant
import scala.jdk.CollectionConverters.*

/**
 * DynamoDB implementation of EventStore for event sourcing 
 * Provides persistent storage for domain events with optimized read/write patterns
 */
class DynamoDBEventStore(client: DynamoDbClient, tableName: String = "event_store")(using Encoder[DomainEvent], Codec[StreamMetadata]) extends EventStore:

  override def getEvents(streamId: StreamId): IO[List[StoredEvent]] =
    IO {
      val request = QueryRequest.builder()
        .tableName(tableName)
        .keyConditionExpression("persistence_id = :streamId")
        .expressionAttributeValues(Map(
          ":streamId" -> AttributeValue.builder().s(streamId.value).build()
        ).asJava)
        .build()

      val response = client.query(request)
      response.items().asScala.toList.map(itemToStoredEvent)
    }

  override def getEventsFromVersion(
      streamId: StreamId,
      fromVersion: EventVersion
  ): IO[List[StoredEvent]] =
    IO {
      val request = QueryRequest.builder()
        .tableName(tableName)
        .keyConditionExpression("persistence_id = :streamId AND sequence_nr >= :fromVersion")
        .expressionAttributeValues(Map(
          ":streamId" -> AttributeValue.builder().s(streamId.value).build(),
          ":fromVersion" -> AttributeValue.builder().n(fromVersion.value.toString).build()
        ).asJava)
        .build()

      val response = client.query(request)
      response.items().asScala.toList.map(itemToStoredEvent)
    }

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
    IO {
      val request = QueryRequest.builder()
        .tableName(tableName)
        .keyConditionExpression("persistence_id = :streamId")
        .expressionAttributeValues(Map(
          ":streamId" -> AttributeValue.builder().s(streamId.value).build()
        ).asJava)
        .scanIndexForward(false) // Descending order
        .limit(1)
        .build()

      val response = client.query(request)
      response.items().asScala.headOption.map { item =>
        item.get("sequence_nr").n().toLong
      }
    }

  private def insertEvents(
      streamId: StreamId,
      events: List[StoredEvent],
      currentVersion: Option[Long]
  )(using Encoder[DomainEvent]): IO[Unit] =
    IO {
      val startSeq = currentVersion.getOrElse(0L) + 1
      
      events.zipWithIndex.foreach { case (event, index) =>
        val sequenceNr   = startSeq + index
        val eventJson    = event.event.asInstanceOf[DomainEvent].asJson.noSpaces
        val metadataJson = event.metadata.asJson.noSpaces
        val timestamp    = Instant.now()

        val item = Map(
          "persistence_id" -> AttributeValue.builder().s(streamId.value).build(),
          "sequence_nr" -> AttributeValue.builder().n(sequenceNr.toString).build(),
          "event_timestamp" -> AttributeValue.builder().s(timestamp.toString).build(),
          "event_type" -> AttributeValue.builder().s(event.event.getClass.getSimpleName).build(),
          "event_data" -> AttributeValue.builder().s(eventJson).build(),
          "metadata" -> AttributeValue.builder().s(metadataJson).build()
        ).asJava

        val request = PutItemRequest.builder()
          .tableName(tableName)
          .item(item)
          .build()

        client.putItem(request)
      }
    }

  private def itemToStoredEvent(item: java.util.Map[String, AttributeValue]): StoredEvent =
    val persistenceId = item.get("persistence_id").s()
    val sequenceNr    = item.get("sequence_nr").n().toLong
    val eventData     = item.get("event_data").s()
    val metadataJson  = item.get("metadata").s()

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
            Timestamp.fromEpochMillis(Instant.now().toEpochMilli)
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
 * Repository implementations using DynamoDB
 */
class DynamoDBUserSessionRepository(client: DynamoDbClient, tableName: String = "user_sessions"):

  def createSession(
      userAccountId: String,
      sessionId: String,
      expiresAt: Instant,
      metadata: String = "{}"
  ): IO[Unit] =
    IO {
      val now = Instant.now()
      val item = Map(
        "user_account_id" -> AttributeValue.builder().s(userAccountId).build(),
        "session_id" -> AttributeValue.builder().s(sessionId).build(),
        "created_at" -> AttributeValue.builder().s(now.toString).build(),
        "last_activity" -> AttributeValue.builder().s(now.toString).build(),
        "expires_at" -> AttributeValue.builder().s(expiresAt.toString).build(),
        "is_active" -> AttributeValue.builder().bool(true).build(),
        "metadata" -> AttributeValue.builder().s(metadata).build()
      ).asJava

      val request = PutItemRequest.builder()
        .tableName(tableName)
        .item(item)
        .build()

      client.putItem(request)
    }.void

  def validateSession(userAccountId: String, sessionId: String): IO[Boolean] =
    IO {
      val key = Map(
        "user_account_id" -> AttributeValue.builder().s(userAccountId).build(),
        "session_id" -> AttributeValue.builder().s(sessionId).build()
      ).asJava

      val request = GetItemRequest.builder()
        .tableName(tableName)
        .key(key)
        .build()

      val response = client.getItem(request)
      
      if response.hasItem then
        val item = response.item()
        val isActive = item.get("is_active").bool()
        val expiresAt = Instant.parse(item.get("expires_at").s())
        isActive && expiresAt.isAfter(Instant.now())
      else false
    }

  def updateActivity(userAccountId: String, sessionId: String, newExpiresAt: Instant): IO[Unit] =
    IO {
      val now = Instant.now()
      val key = Map(
        "user_account_id" -> AttributeValue.builder().s(userAccountId).build(),
        "session_id" -> AttributeValue.builder().s(sessionId).build()
      ).asJava

      val updates = Map(
        "last_activity" -> AttributeValueUpdate.builder()
          .value(AttributeValue.builder().s(now.toString).build())
          .action(AttributeAction.PUT)
          .build(),
        "expires_at" -> AttributeValueUpdate.builder()
          .value(AttributeValue.builder().s(newExpiresAt.toString).build())
          .action(AttributeAction.PUT)
          .build()
      ).asJava

      val request = UpdateItemRequest.builder()
        .tableName(tableName)
        .key(key)
        .attributeUpdates(updates)
        .build()

      client.updateItem(request)
    }.void

  def deactivateSession(userAccountId: String, sessionId: String): IO[Unit] =
    IO {
      val key = Map(
        "user_account_id" -> AttributeValue.builder().s(userAccountId).build(),
        "session_id" -> AttributeValue.builder().s(sessionId).build()
      ).asJava

      val updates = Map(
        "is_active" -> AttributeValueUpdate.builder()
          .value(AttributeValue.builder().bool(false).build())
          .action(AttributeAction.PUT)
          .build()
      ).asJava

      val request = UpdateItemRequest.builder()
        .tableName(tableName)
        .key(key)
        .attributeUpdates(updates)
        .build()

      client.updateItem(request)
    }.void