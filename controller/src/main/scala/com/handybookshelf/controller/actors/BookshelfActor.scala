package com.handybookshelf
package controller.actors

import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import domain.{UserAccountId, Bookshelf, BookId, BookReference, Filters, BookSorter, TitleSorter}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

object BookshelfActor:
  
  // Commands
  sealed trait BookshelfCommand
  
  final case class AddBookToShelf(
    bookId: BookId,
    bookReference: BookReference,
    filters: Filters,
    sessionId: String,
    replyTo: ActorRef[BookOperationResponse]
  ) extends BookshelfCommand

  final case class RemoveBookFromShelf(
    bookId: BookId,
    sessionId: String,
    replyTo: ActorRef[BookOperationResponse]
  ) extends BookshelfCommand

  final case class GetBookshelf(
    sessionId: String,
    replyTo: ActorRef[BookshelfResponse]
  ) extends BookshelfCommand

  final case class ChangeSorter(
    newSorter: BookSorter,
    sessionId: String,
    replyTo: ActorRef[BookOperationResponse]
  ) extends BookshelfCommand
  
  final case class AddFilterToBook(
    bookId: BookId,
    filter: domain.Filter,
    sessionId: String,
    replyTo: ActorRef[BookOperationResponse]
  ) extends BookshelfCommand
  
  final case class RemoveFilterFromBook(
    bookId: BookId,
    filter: domain.Filter,
    sessionId: String,
    replyTo: ActorRef[BookOperationResponse]
  ) extends BookshelfCommand
  
  case object Shutdown extends BookshelfCommand
  
  // Events
  sealed trait BookshelfEvent
  final case class BookAddedToShelf(
    bookId: BookId, 
    bookReference: BookReference, 
    filters: Filters
  ) extends BookshelfEvent
  
  final case class BookRemovedFromShelf(bookId: BookId) extends BookshelfEvent
  final case class SorterChanged(newSorter: BookSorter) extends BookshelfEvent
  final case class FilterAddedToBook(bookId: BookId, filter: domain.Filter) extends BookshelfEvent
  final case class FilterRemovedFromBook(bookId: BookId, filter: domain.Filter) extends BookshelfEvent
  
  // Responses
  sealed trait BookshelfResponse
  final case class BookOperationResponse(
    success: Boolean, 
    message: String
  ) extends BookshelfResponse
  
  final case class BookshelfViewResponse(
    bookshelf: Bookshelf,
    success: Boolean = true
  ) extends BookshelfResponse
  
  // State
  final case class BookshelfState(
    userAccountId: UserAccountId,
    bookshelf: Bookshelf = Bookshelf(Map.empty, TitleSorter)
  )
  
  // Session validation interface
  trait SessionValidator:
    def validateSession(sessionId: String): Boolean
  
  // JSON codecs for persistence
  given Encoder[BookshelfEvent] = deriveEncoder
  given Decoder[BookshelfEvent] = deriveDecoder
  given Encoder[BookshelfState] = deriveEncoder
  given Decoder[BookshelfState] = deriveDecoder
  
  def apply(userAccountId: UserAccountId, sessionValidator: SessionValidator): Behavior[BookshelfCommand] =
    EventSourcedBehavior[BookshelfCommand, BookshelfEvent, BookshelfState](
      persistenceId = PersistenceId.of("Bookshelf", userAccountId.breachEncapsulationIdAsString),
      emptyState = BookshelfState(userAccountId),
      commandHandler = commandHandler(sessionValidator),
      eventHandler = eventHandler
    )
  
  private def commandHandler(sessionValidator: SessionValidator)(
    state: BookshelfState,
    command: BookshelfCommand
  ): ReplyEffect[BookshelfEvent, BookshelfState] =
    command match
      case AddBookToShelf(bookId, bookReference, filters, sessionId, replyTo) =>
        if !sessionValidator.validateSession(sessionId) then
          Effect.reply(replyTo)(BookOperationResponse(
            success = false, 
            message = "Invalid session - user must be logged in"
          ))
        else
          Effect
            .persist(BookAddedToShelf(bookId, bookReference, filters))
            .thenReply(replyTo)(_ => BookOperationResponse(
              success = true, 
              message = s"Book ${bookId.toString} added to shelf successfully"
            ))
      
      case RemoveBookFromShelf(bookId, sessionId, replyTo) =>
        if !sessionValidator.validateSession(sessionId) then
          Effect.reply(replyTo)(BookOperationResponse(
            success = false, 
            message = "Invalid session - user must be logged in"
          ))
        else if !state.bookshelf.books.contains(bookId) then
          Effect.reply(replyTo)(BookOperationResponse(
            success = false, 
            message = s"Book ${bookId.toString} not found in shelf"
          ))
        else
          Effect
            .persist(BookRemovedFromShelf(bookId))
            .thenReply(replyTo)(_ => BookOperationResponse(
              success = true, 
              message = s"Book ${bookId.toString} removed from shelf successfully"
            ))
      
      case GetBookshelf(sessionId, replyTo) =>
        if !sessionValidator.validateSession(sessionId) then
          Effect.reply(replyTo)(BookOperationResponse(
            success = false, 
            message = "Invalid session - user must be logged in"
          ))
        else
          Effect.reply(replyTo)(BookshelfViewResponse(state.bookshelf))
      
      case ChangeSorter(newSorter, sessionId, replyTo) =>
        if !sessionValidator.validateSession(sessionId) then
          Effect.reply(replyTo)(BookOperationResponse(
            success = false, 
            message = "Invalid session - user must be logged in"
          ))
        else
          Effect
            .persist(SorterChanged(newSorter))
            .thenReply(replyTo)(_ => BookOperationResponse(
              success = true, 
              message = "Sorter changed successfully"
            ))
      
      case AddFilterToBook(bookId, filter, sessionId, replyTo) =>
        if !sessionValidator.validateSession(sessionId) then
          Effect.reply(replyTo)(BookOperationResponse(
            success = false, 
            message = "Invalid session - user must be logged in"
          ))
        else if !state.bookshelf.books.contains(bookId) then
          Effect.reply(replyTo)(BookOperationResponse(
            success = false, 
            message = s"Book ${bookId.toString} not found in shelf"
          ))
        else
          Effect
            .persist(FilterAddedToBook(bookId, filter))
            .thenReply(replyTo)(_ => BookOperationResponse(
              success = true, 
              message = s"Filter added to book ${bookId.toString} successfully"
            ))
      
      case RemoveFilterFromBook(bookId, filter, sessionId, replyTo) =>
        if !sessionValidator.validateSession(sessionId) then
          Effect.reply(replyTo)(BookOperationResponse(
            success = false, 
            message = "Invalid session - user must be logged in"
          ))
        else if !state.bookshelf.books.contains(bookId) then
          Effect.reply(replyTo)(BookOperationResponse(
            success = false, 
            message = s"Book ${bookId.toString} not found in shelf"
          ))
        else
          Effect
            .persist(FilterRemovedFromBook(bookId, filter))
            .thenReply(replyTo)(_ => BookOperationResponse(
              success = true, 
              message = s"Filter removed from book ${bookId.toString} successfully"
            ))
      
      case Shutdown =>
        Effect.stop[BookshelfEvent, BookshelfState]().thenNoReply()
  
  private def eventHandler(state: BookshelfState, event: BookshelfEvent): BookshelfState =
    event match
      case BookAddedToShelf(bookId, bookReference, filters) =>
        state.copy(bookshelf = state.bookshelf.addBook(bookReference, filters, bookId))
      
      case BookRemovedFromShelf(bookId) =>
        val updatedBooks = state.bookshelf.books - bookId
        state.copy(bookshelf = Bookshelf(updatedBooks, state.bookshelf.sorter))
      
      case SorterChanged(newSorter) =>
        state.copy(bookshelf = state.bookshelf.changeSorter(newSorter))
      
      case FilterAddedToBook(bookId, filter) =>
        state.copy(bookshelf = state.bookshelf.addFilter(filter, bookId))
      
      case FilterRemovedFromBook(bookId, filter) =>
        state.copy(bookshelf = state.bookshelf.removeFilter(filter, bookId))

/**
 * Bookshelf Actor utility methods
 */
object BookshelfActorUtil:
  
  /**
   * create a unique actor name for a user bookshelf
   */
  def createActorName(userAccountId: UserAccountId): String =
    s"user-bookshelf-${userAccountId.breachEncapsulationIdAsString}"