package com.handybookshelf
package domain

import cats.effect.IO
import com.handybookshelf.util.{ISBN, Timestamp}

final case class BookAggregate(
    bookId: BookId,
    title: Option[NES] = None,
    isbn: Option[ISBN] = None,
    location: Option[Location] = None,
    tags: Set[Tag] = Set.empty,
    devices: Set[Device] = Set.empty,
    version: EventVersion = EventVersion.initial,
    uncommittedEvents: List[BookEvent] = List.empty,
    isDeleted: Boolean = false
) extends AggregateRoot[BookAggregate, BookEvent]:

  override def id: String = bookId.toString

  protected def applyEvent(event: BookEvent): BookAggregate = event match
    case BookRegistered(_, _, isbn, title, version, _) =>
      this.copy(
        title = Some(title),
        isbn = isbn,
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
  def register(isbn: Option[ISBN], title: NES): IO[BookAggregate] =
    Timestamp.now.map(timestamp =>
      val event = BookRegistered(
        eventId = EventId.generate(),
        bookId = bookId,
        isbn = isbn,
        title = title,
        version = version.next,
        timestamp = timestamp
      )
      applyEvent(event)
    )

  def changeLocation(newLocation: Location): IO[BookAggregate] =
    if (isDeleted) IO.raiseError(new IllegalStateException("Cannot change location of deleted book"))
    else
      Timestamp.now.map(timestamp =>
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
      Timestamp.now.map(timestamp =>
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
      Timestamp.now.map(timestamp =>
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
      Timestamp.now.map(timestamp =>
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
      Timestamp.now.map(timestamp =>
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
          Timestamp.now.map(timestamp =>
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
      Timestamp.now.map(timestamp =>
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
