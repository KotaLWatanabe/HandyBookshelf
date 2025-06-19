package com.handybookshelf package domain

import cats.effect.IO
import com.handybookshelf.util.{ISBN, NES}
// Temporarily commented out for compilation
// import com.handybookshelf.infrastructure.{EventStore, StreamId}

sealed trait BookCommand:
  def bookId: BookId

final case class RegisterBook(
    bookId: BookId,
    isbn: Option[ISBN],
    title: NES
) extends BookCommand

final case class ChangeBookLocation(
    bookId: BookId,
    location: Location
) extends BookCommand

final case class AddBookTag(
    bookId: BookId,
    tag: Tag
) extends BookCommand

final case class RemoveBookTag(
    bookId: BookId,
    tag: Tag
) extends BookCommand

final case class AddBookDevice(
    bookId: BookId,
    device: Device
) extends BookCommand

final case class RemoveBookDevice(
    bookId: BookId,
    device: Device
) extends BookCommand

final case class UpdateBookTitle(
    bookId: BookId,
    newTitle: NES
) extends BookCommand

final case class RemoveBook(
    bookId: BookId
) extends BookCommand

trait BookCommandHandler:
  def handle(command: BookCommand): IO[List[BookEvent]]

class BookCommandHandlerImpl(
    // Temporarily commented out for compilation
    // eventStore: EventStore
) extends BookCommandHandler:

  def handle(command: BookCommand): IO[List[BookEvent]] =
    for {
      // Temporarily simplified for compilation
      // streamId         <- IO.pure(StreamId(command.bookId.toString))
      // events           <- eventStore.getEvents(streamId)
      // aggregate        <- IO.pure(BookAggregate.fromEvents(command.bookId, events.map(_.asInstanceOf[BookEvent])))
      updatedAggregate <- handleCommand(command, BookAggregate.empty(command.bookId))
      // Temporarily commented out for compilation
      // _                <- eventStore.saveEvents(streamId, updatedAggregate.uncommittedEvents)
    } yield updatedAggregate.uncommittedEvents

  private def handleCommand(command: BookCommand, aggregate: BookAggregate): IO[BookAggregate] =
    command match
      case RegisterBook(_, isbn, title) =>
        aggregate.register(isbn, title)

      case ChangeBookLocation(_, location) =>
        aggregate.changeLocation(location)

      case AddBookTag(_, tag) =>
        aggregate.addTag(tag)

      case RemoveBookTag(_, tag) =>
        aggregate.removeTag(tag)

      case AddBookDevice(_, device) =>
        aggregate.addDevice(device)

      case RemoveBookDevice(_, device) =>
        aggregate.removeDevice(device)

      case UpdateBookTitle(_, newTitle) =>
        aggregate.updateTitle(newTitle)

      case RemoveBook(_) =>
        aggregate.remove()
