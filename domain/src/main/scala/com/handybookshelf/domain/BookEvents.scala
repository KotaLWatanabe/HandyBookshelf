package com.handybookshelf
package domain

import com.handybookshelf.util.{ISBN, Timestamp}

sealed trait BookEvent extends DomainEvent:
  def bookId: BookId
  def bookEventType: BookEventType
  override def aggregateId: String = bookId.toString
  override def eventType: String   = bookEventType.eventType

enum BookEventType(val eventType: String):
  case Registered      extends BookEventType("BookRegistered")
  case LocationChanged extends BookEventType("BookLocationChanged")
  case TagAdded        extends BookEventType("BookTagAdded")
  case TagRemoved      extends BookEventType("BookTagRemoved")
  case DeviceAdded     extends BookEventType("BookDeviceAdded")
  case DeviceRemoved   extends BookEventType("BookDeviceRemoved")
  case TitleUpdated    extends BookEventType("BookTitleUpdated")
  case Removed         extends BookEventType("BookRemoved")

/** 書籍登録イベント
  *
  * @param eventId イベントID
  * @param bookId 書籍ID
  * @param identifier 書籍識別子（ISBN, arXiv ID, DOI, またはタイトル）
  * @param title 書籍タイトル
  * @param version イベントバージョン
  * @param timestamp タイムスタンプ
  */
final case class BookRegistered(
    eventId: EventId,
    bookId: BookId,
    identifier: BookIdentifier,
    title: NES,
    version: EventVersion,
    timestamp: Timestamp
) extends BookEvent:
  val bookEventType: BookEventType = BookEventType.Registered

  /** 後方互換性: ISBNを取得（ISBN識別子の場合のみ） */
  def isbn: Option[ISBN] = identifier match
    case BookIdentifier.ISBN(isbn) => Some(isbn)
    case _                         => None

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
