package com.handybookshelf
package domain

import cats.effect.IO
import com.handybookshelf.util.{ISBN, Timestamp}

final case class BookAggregate(
    bookId: BookId,
    title: Option[NES] = None,
    identifier: Option[BookIdentifier] = None,
    location: Option[Location] = None,
    tags: Set[Tag] = Set.empty,
    devices: Set[Device] = Set.empty,
    version: EventVersion = EventVersion.initial,
    uncommittedEvents: List[BookEvent] = List.empty,
    isDeleted: Boolean = false
) extends AggregateRoot[BookAggregate, BookEvent]:

  override def id: String = bookId.toString

  /** 後方互換性: ISBNを取得（ISBN識別子の場合のみ） */
  def isbn: Option[ISBN] = identifier.collect { case BookIdentifier.ISBN(isbn) => isbn }

  /** 正規化された識別子キーを取得（重複チェック用） */
  def normalizedIdentifier: Option[NormalizedIdentifier] =
    identifier.map(_.normalizedKey)

  protected def applyEvent(event: BookEvent): BookAggregate = event match
    case BookRegistered(_, _, identifier, title, version, _) =>
      this.copy(
        title = Some(title),
        identifier = Some(identifier),
        version = version,
        uncommittedEvents = event :: uncommittedEvents
      )

    case BookLocationChanged(_, _, _, newLocation, version, _) =>
      this.copy(
        location = Some(newLocation),
        version = version,
        uncommittedEvents = event :: uncommittedEvents
      )

    case BookTagAdded(_, _, tag, version, _) =>
      this.copy(
        tags = tags + tag,
        version = version,
        uncommittedEvents = event :: uncommittedEvents
      )

    case BookTagRemoved(_, _, tag, version, _) =>
      this.copy(
        tags = tags - tag,
        version = version,
        uncommittedEvents = event :: uncommittedEvents
      )

    case BookDeviceAdded(_, _, device, version, _) =>
      this.copy(
        devices = devices + device,
        version = version,
        uncommittedEvents = event :: uncommittedEvents
      )

    case BookDeviceRemoved(_, _, device, version, _) =>
      this.copy(
        devices = devices - device,
        version = version,
        uncommittedEvents = event :: uncommittedEvents
      )

    case BookTitleUpdated(_, _, _, newTitle, version, _) =>
      this.copy(
        title = Some(newTitle),
        version = version,
        uncommittedEvents = event :: uncommittedEvents
      )

    case BookRemoved(_, _, version, _) =>
      this.copy(
        isDeleted = true,
        version = version,
        uncommittedEvents = event :: uncommittedEvents
      )

  def markEventsAsCommitted: BookAggregate =
    this.copy(uncommittedEvents = List.empty)

  // ビジネスロジック - コマンドハンドリング

  /**
   * 書籍を登録する（BookIdentifier版）
   *
   * @param identifier
   *   識別子（ISBN, arXiv ID, DOI, またはタイトル）
   * @param title
   *   書籍タイトル
   * @return
   *   更新されたBookAggregate
   */
  def register(identifier: BookIdentifier, title: NES): IO[BookAggregate] =
    IO(Timestamp.now).map(timestamp =>
      val event = BookRegistered(
        eventId = EventId.generate(),
        bookId = bookId,
        identifier = identifier,
        title = title,
        version = version.next,
        timestamp = timestamp
      )
      applyEvent(event)
    )

  /**
   * 書籍を登録する（後方互換性: ISBN版）
   *
   * @param isbn
   *   ISBN（オプション）
   * @param title
   *   書籍タイトル
   * @return
   *   更新されたBookAggregate
   */
  def registerWithISBN(isbn: Option[ISBN], title: NES): IO[BookAggregate] =
    val identifier = isbn match
      case Some(i) => BookIdentifier.ISBN(i)
      case None    => BookIdentifier.Title(title)
    register(identifier, title)

  def changeLocation(newLocation: Location): IO[BookAggregate] =
    if (isDeleted) IO.raiseError(new IllegalStateException("Cannot change location of deleted book"))
    else
      IO(Timestamp.now).map(timestamp =>
        val event = BookLocationChanged(
          eventId = EventId.generate(),
          bookId = bookId,
          oldLocation = location,
          newLocation = newLocation,
          version = version.next,
          timestamp = timestamp
        )
        applyEvent(event)
      )

  def addTag(tag: Tag): IO[BookAggregate] =
    if (isDeleted) IO.raiseError(new IllegalStateException("Cannot add tag to deleted book"))
    else if (tags.contains(tag)) IO.pure(this)
    else
      IO(Timestamp.now).map(timestamp =>
        val event = BookTagAdded(
          eventId = EventId.generate(),
          bookId = bookId,
          tag = tag,
          version = version.next,
          timestamp = timestamp
        )
        applyEvent(event)
      )

  def removeTag(tag: Tag): IO[BookAggregate] =
    if (isDeleted) IO.raiseError(new IllegalStateException("Cannot remove tag from deleted book"))
    else if (!tags.contains(tag)) IO.pure(this)
    else
      IO(Timestamp.now).map(timestamp =>
        val event = BookTagRemoved(
          eventId = EventId.generate(),
          bookId = bookId,
          tag = tag,
          version = version.next,
          timestamp = timestamp
        )
        applyEvent(event)
      )

  def addDevice(device: Device): IO[BookAggregate] =
    if (isDeleted) IO.raiseError(new IllegalStateException("Cannot add device to deleted book"))
    else if (devices.contains(device)) IO.pure(this)
    else
      IO(Timestamp.now).map(timestamp =>
        val event = BookDeviceAdded(
          eventId = EventId.generate(),
          bookId = bookId,
          device = device,
          version = version.next,
          timestamp = timestamp
        )
        applyEvent(event)
      )

  def removeDevice(device: Device): IO[BookAggregate] =
    if (isDeleted) IO.raiseError(new IllegalStateException("Cannot remove device from deleted book"))
    else if (!devices.contains(device)) IO.pure(this)
    else
      IO(Timestamp.now).map(timestamp =>
        val event = BookDeviceRemoved(
          eventId = EventId.generate(),
          bookId = bookId,
          device = device,
          version = version.next,
          timestamp = timestamp
        )
        applyEvent(event)
      )

  def updateTitle(newTitle: NES): IO[BookAggregate] =
    if (isDeleted)
      IO.raiseError(new IllegalStateException("Cannot update title of deleted book"))
    else
      title match
        case Some(oldTitle) if oldTitle == newTitle => IO.pure(this)
        case Some(oldTitle) =>
          IO(Timestamp.now).map(timestamp =>
            val event = BookTitleUpdated(
              eventId = EventId.generate(),
              bookId = bookId,
              oldTitle = oldTitle,
              newTitle = newTitle,
              version = version.next,
              timestamp = timestamp
            )
            applyEvent(event)
          )
        case None =>
          IO.raiseError(new IllegalStateException("Cannot update title of unregistered book"))

  def remove(): IO[BookAggregate] =
    if (isDeleted) IO.pure(this)
    else
      IO(Timestamp.now).map(timestamp =>
        val event = BookRemoved(
          eventId = EventId.generate(),
          bookId = bookId,
          version = version.next,
          timestamp = timestamp
        )
        applyEvent(event)
      )

object BookAggregate:
  def empty(bookId: BookId): BookAggregate = BookAggregate(bookId = bookId)

  def fromEvents(bookId: BookId, events: List[BookEvent]): BookAggregate =
    events.foldLeft(empty(bookId)) { (aggregate, event) =>
      aggregate.applyEvent(event).copy(uncommittedEvents = List.empty)
    }
