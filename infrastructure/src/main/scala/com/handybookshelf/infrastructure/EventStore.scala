package com.handybookshelf package infrastructure

import cats.effect.IO
import util.Timestamp

final case class StreamId(value: String) extends AnyVal

final case class EventVersion(value: Long) extends AnyVal:
  def isAnyVersion: Boolean = value == -1L
  def next: EventVersion    = EventVersion(value + 1)
object EventVersion:
  val any: EventVersion      = EventVersion(-1L)
  val noStream: EventVersion = EventVersion(0L)
  val init: EventVersion = EventVersion(1L)

final case class StreamMetadata(
    streamId: StreamId,
    version: EventVersion,
    timestamp: Timestamp
)

trait StoredEvent:
  type E
  def event: E
  def metadata: StreamMetadata

trait EventStore:
  def getEvents(streamId: StreamId): IO[List[StoredEvent]]

  def getEventsFromVersion(
      streamId: StreamId,
      fromVersion: EventVersion
  ): IO[List[StoredEvent]]

  def saveEvents(
      streamId: StreamId,
      events: List[StoredEvent],
      expectedVersion: EventVersion
  ): IO[Unit]

  def saveEvents(streamId: StreamId, events: List[StoredEvent]): IO[Unit] =
    saveEvents(streamId, events, EventVersion.any)

  def getStreamMetadata(streamId: StreamId): IO[Option[StreamMetadata]]

  def streamExists(streamId: StreamId): IO[Boolean]

trait EventBus:
  def publish[E](events: List[E]): IO[Unit]
  def subscribe[E](handler: E => IO[Unit]): IO[Unit]

class InMemoryEventStore extends EventStore:
  private var store: Map[StreamId, List[StoredEvent]] = Map.empty
  private var metadata: Map[StreamId, StreamMetadata] = Map.empty

  def getEvents(streamId: StreamId): IO[List[StoredEvent]] =
    IO.pure(store.getOrElse(streamId, List.empty).reverse)

  def getEventsFromVersion(
      streamId: StreamId,
      fromVersion: EventVersion
  ): IO[List[StoredEvent]] =
    IO.pure(
      store
        .getOrElse(streamId, List.empty)
        .filter(_.metadata.version.value >= fromVersion.value)
        .reverse
    )

  def saveEvents(
      streamId: StreamId,
      events: List[StoredEvent],
      expectedVersion: EventVersion
  ): IO[Unit] =
    if (events.isEmpty)
      IO.unit
    else
      for {
        currentMetadata <- getStreamMetadata(streamId)
        _               <- validateVersion(currentMetadata, expectedVersion)
        newVersion  = currentMetadata.map(_.version.next).getOrElse(EventVersion.init)
        newMetadata = StreamMetadata(streamId, newVersion, Timestamp.now())
        _ <- IO {
          store = store.updated(streamId, events ++ store.getOrElse(streamId, List.empty))
          metadata = metadata.updated(streamId, newMetadata)
        }
      } yield ()

  def getStreamMetadata(streamId: StreamId): IO[Option[StreamMetadata]] =
    IO.pure(metadata.get(streamId))

  def streamExists(streamId: StreamId): IO[Boolean] =
    IO.pure(store.contains(streamId))

  private def validateVersion(
      currentMetadata: Option[StreamMetadata],
      expectedVersion: EventVersion
  ): IO[Unit] =
    if (expectedVersion.isAnyVersion)
      IO.unit
    else
      currentMetadata match
        case None if expectedVersion == EventVersion.noStream => IO.unit
        case None =>
          IO.raiseError(
            new IllegalStateException(s"Stream does not exist but expected version ${expectedVersion.value}")
          )
        case Some(metadata) if metadata.version.value == expectedVersion.value => IO.unit
        case Some(metadata) =>
          IO.raiseError(
            new IllegalStateException(
              s"Version mismatch. Expected: ${expectedVersion.value}, Actual: ${metadata.version.value}"
            )
          )