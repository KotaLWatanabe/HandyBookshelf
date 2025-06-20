package com.handybookshelf 
package domain

import com.handybookshelf.util.{ISBN, Timestamp}

// Temporarily commented out for compilation
// import com.handybookshelf.infrastructure.EventVersion

sealed trait BookEvent extends DomainEvent:
  def bookId: BookId
  def bookEventType: BookEventType
  override def aggregateId: String = bookId.toString
  override def eventType: String = bookEventType.eventType

enum BookEventType(val eventType: String):
  case Registered      extends BookEventType("BookRegistered")
  case LocationChanged extends BookEventType("BookLocationChanged")
  case TagAdded        extends BookEventType("BookTagAdded")
  case TagRemoved      extends BookEventType("BookTagRemoved")
  case DeviceAdded     extends BookEventType("BookDeviceAdded")
  case DeviceRemoved   extends BookEventType("BookDeviceRemoved")
  case TitleUpdated    extends BookEventType("BookTitleUpdated")
  case Removed         extends BookEventType("BookRemoved")

final case class BookRegistered(
    eventId: EventId,
    bookId: BookId,
    isbn: Option[ISBN],
    title: NES,
    version: EventVersion,
    timestamp: Timestamp
) extends BookEvent:
  val bookEventType: BookEventType = BookEventType.Registered

final case class BookLocationChanged(
    eventId: EventId,
    bookId: BookId,
    oldLocation: Option[Location],
    newLocation: Location,
    version: EventVersion,
    timestamp: Timestamp
) extends BookEvent:
    val bookEventType: BookEventType = BookEventType.LocationChanged

final case class BookTagAdded(
    eventId: EventId,
    bookId: BookId,
    tag: Tag,
    version: EventVersion,
    timestamp: Timestamp
) extends BookEvent:
    val bookEventType: BookEventType = BookEventType.TagAdded

final case class BookTagRemoved(
    eventId: EventId,
    bookId: BookId,
    tag: Tag,
    version: EventVersion,
    timestamp: Timestamp
) extends BookEvent:
  val bookEventType: BookEventType = BookEventType.TagRemoved

final case class BookDeviceAdded(
    eventId: EventId,
    bookId: BookId,
    device: Device,
    version: EventVersion,
    timestamp: Timestamp
) extends BookEvent:
  val bookEventType: BookEventType = BookEventType.DeviceAdded

final case class BookDeviceRemoved(
    eventId: EventId,
    bookId: BookId,
    device: Device,
    version: EventVersion,
    timestamp: Timestamp
) extends BookEvent:
  val bookEventType: BookEventType = BookEventType.DeviceRemoved

final case class BookTitleUpdated(
    eventId: EventId,
    bookId: BookId,
    oldTitle: NES,
    newTitle: NES,
    version: EventVersion,
    timestamp: Timestamp
) extends BookEvent:
  val bookEventType: BookEventType = BookEventType.TitleUpdated

final case class BookRemoved(
    eventId: EventId,
    bookId: BookId,
    version: EventVersion,
    timestamp: Timestamp
) extends BookEvent:
  val bookEventType: BookEventType = BookEventType.Removed

final case class Location(name: NES)
