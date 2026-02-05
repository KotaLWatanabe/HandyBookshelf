package com.handybookshelf
package infrastructure

import cats.effect.IO
import cats.syntax.all.*
import com.handybookshelf.domain.*
import com.handybookshelf.domain.repositories.BookRepository
import com.handybookshelf.util.{ISBN, Timestamp}

/** BookRepositoryImpl - BookRepositoryの実装
  *
  * EventStoreを使用してBookAggregateのイベント履歴を永続化・復元する。 IdentifierIndexを使用して識別子からBookIdへの高速ルックアップを提供。
  */
class BookRepositoryImpl(
    eventStore: EventStore,
    identifierIndex: IdentifierIndex
) extends BookRepository:

  import BookRepositoryImpl.*

  // --- AggregateRepository メソッド ---

  override def save(aggregate: BookAggregate): IO[BookAggregate] =
    val streamId = StreamId(aggregate.bookId.toString)
    val events   = aggregate.uncommittedEvents.reverse // 時系列順に並べる

    if events.isEmpty then IO.pure(aggregate)
    else
      for {
        // イベントをStoredEventに変換
        storedEvents <- events.traverse(toStoredEvent)

        // EventStoreに保存
        _ <- eventStore.saveEvents(streamId, storedEvents)

        // 識別子インデックスを更新（BookRegisteredイベントがある場合）
        _ <- events.collectFirst { case e: BookRegistered => e }.traverse_ { registered =>
          identifierIndex.put(registered.identifier.normalizedKey, aggregate.bookId)
        }
      } yield aggregate.markEventsAsCommitted

  override def findById(aggregateId: String): IO[Option[BookAggregate]] =
    val streamId = StreamId(aggregateId)
    for {
      storedEvents <- eventStore.getEvents(streamId)
      bookEvents   <- storedEvents.traverse(fromStoredEvent)
    } yield
      if bookEvents.isEmpty then None
      else
        // aggregateIdからBookIdを復元
        bookEvents.headOption.map { firstEvent =>
          BookAggregate.fromEvents(firstEvent.bookId, bookEvents)
        }

  override def exists(aggregateId: String): IO[Boolean] =
    eventStore.streamExists(StreamId(aggregateId))

  override def getVersion(aggregateId: String): IO[Option[domain.EventVersion]] =
    eventStore.getStreamMetadata(StreamId(aggregateId)).map(_.map { meta =>
      domain.EventVersion(meta.version.value)
    })

  override def getEventsFromVersion(
      aggregateId: String,
      fromVersion: domain.EventVersion
  ): IO[List[BookEvent]] =
    val streamId = StreamId(aggregateId)
    // domain.EventVersionからinfrastructure.EventVersionへ変換
    val infraFromVersion = infrastructure.EventVersion(fromVersion.value)
    eventStore
      .getEventsFromVersion(streamId, infraFromVersion)
      .flatMap(_.traverse(fromStoredEvent))

  // --- BookRepository 固有メソッド ---

  override def existsByIdentifier(identifier: BookIdentifier): IO[Boolean] =
    identifierIndex.exists(identifier.normalizedKey)

  override def findByIdentifier(identifier: BookIdentifier): IO[Option[BookAggregate]] =
    findByNormalizedIdentifier(identifier.normalizedKey)

  override def findByNormalizedIdentifier(
      normalizedId: NormalizedIdentifier
  ): IO[Option[BookAggregate]] =
    for {
      maybeBookId <- identifierIndex.get(normalizedId)
      result      <- maybeBookId.flatTraverse(bookId => findById(bookId.toString))
    } yield result

  override def findByISBN(isbn: ISBN): IO[List[BookAggregate]] =
    val identifier = BookIdentifier.ISBN(isbn)
    findByIdentifier(identifier).map(_.toList)

  // 以下のメソッドは初期実装では未サポート（必要に応じて実装）

  override def findByTitleContaining(titlePattern: String): IO[List[BookAggregate]] =
    IO.pure(List.empty) // TODO: 全文検索はElasticsearchで実装

  override def findByLocation(location: Location): IO[List[BookAggregate]] =
    IO.pure(List.empty) // TODO: インデックス追加が必要

  override def findByTag(tag: Tag): IO[List[BookAggregate]] =
    IO.pure(List.empty) // TODO: インデックス追加が必要

  override def findByDevice(device: Device): IO[List[BookAggregate]] =
    IO.pure(List.empty) // TODO: インデックス追加が必要

  override def findAllActive(): IO[List[BookAggregate]] =
    IO.pure(List.empty) // TODO: 全ストリームスキャンが必要

  override def findAllDeleted(): IO[List[BookAggregate]] =
    IO.pure(List.empty) // TODO: 全ストリームスキャンが必要

  override def countActive(): IO[Long] =
    IO.pure(0L) // TODO: カウンタ実装が必要

  override def findUpdatedBetween(from: Timestamp, to: Timestamp): IO[List[BookAggregate]] =
    IO.pure(List.empty) // TODO: タイムスタンプインデックスが必要

object BookRepositoryImpl:

  /** BookEventをStoredEventに変換 */
  private def toStoredEvent(event: BookEvent): IO[StoredEvent] =
    IO {
      val metadata = StreamMetadata(
        streamId = StreamId(event.bookId.toString),
        // domain.EventVersionからinfrastructure.EventVersionへ変換
        version = infrastructure.EventVersion(event.version.value),
        timestamp = event.timestamp
      )
      BookStoredEvent(event, metadata)
    }

  /** StoredEventからBookEventを復元 */
  private def fromStoredEvent(stored: StoredEvent): IO[BookEvent] =
    stored match
      case BookStoredEvent(event, _) => IO.pure(event)
      case other =>
        IO.raiseError(new IllegalStateException(s"Unknown StoredEvent type: ${other.getClass}"))

/** BookEvent用のStoredEvent実装 */
final case class BookStoredEvent(
    event: BookEvent,
    metadata: StreamMetadata
) extends StoredEvent:
  type E = BookEvent
