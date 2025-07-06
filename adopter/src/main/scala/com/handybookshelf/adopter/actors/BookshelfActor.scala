package com.handybookshelf
package adopter.actors

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
      userAccountId: UserAccountId,
      bookId: BookId,
      bookReference: BookReference,
      filters: Filters
  ) extends BookshelfEvent

  final case class BookRemovedFromShelf(
      userAccountId: UserAccountId,
      bookId: BookId
  ) extends BookshelfEvent

  final case class SorterChanged(
      userAccountId: UserAccountId,
      newSorter: BookSorter
  ) extends BookshelfEvent

  final case class FilterAddedToBook(
      userAccountId: UserAccountId,
      bookId: BookId,
      filter: domain.Filter
  ) extends BookshelfEvent

  final case class FilterRemovedFromBook(
      userAccountId: UserAccountId,
      bookId: BookId,
      filter: domain.Filter
  ) extends BookshelfEvent

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
    def validateSessionForUser(sessionId: String, userAccountId: UserAccountId): Boolean

  // JSON codecs for persistence
  given userAccountIdEncoder: Encoder[UserAccountId] = Encoder.encodeString.contramap(_.breachEncapsulationIdAsString)
  given userAccountIdDecoder: Decoder[UserAccountId] = Decoder.decodeString.map(s => UserAccountId.create(wvlet.airframe.ulid.ULID.fromString(s)))
  // TODO: Add codecs for BookReference, Bookshelf, etc.
  // given Encoder[BookshelfEvent] = deriveEncoder
  // given Decoder[BookshelfEvent] = deriveDecoder
  // given Encoder[BookshelfState] = deriveEncoder
  // given Decoder[BookshelfState] = deriveDecoder

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
        if !sessionValidator.validateSessionForUser(sessionId, state.userAccountId) then
          Effect.reply(replyTo)(
            BookOperationResponse(
              success = false,
              message = "Invalid session - user must be logged in or session does not belong to this user"
            )
          )
        else
          Effect
            .persist(BookAddedToShelf(state.userAccountId, bookId, bookReference, filters))
            .thenReply(replyTo)(_ =>
              BookOperationResponse(
                success = true,
                message =
                  s"Book ${bookId.toString} added to shelf for user ${state.userAccountId.toString} successfully"
              )
            )

      case RemoveBookFromShelf(bookId, sessionId, replyTo) =>
        if !sessionValidator.validateSessionForUser(sessionId, state.userAccountId) then
          Effect.reply(replyTo)(
            BookOperationResponse(
              success = false,
              message = "Invalid session - user must be logged in or session does not belong to this user"
            )
          )
        else if !state.bookshelf.books.contains(bookId) then
          Effect.reply(replyTo)(
            BookOperationResponse(
              success = false,
              message = s"Book ${bookId.toString} not found in shelf for user ${state.userAccountId.toString}"
            )
          )
        else
          Effect
            .persist(BookRemovedFromShelf(state.userAccountId, bookId))
            .thenReply(replyTo)(_ =>
              BookOperationResponse(
                success = true,
                message =
                  s"Book ${bookId.toString} removed from shelf for user ${state.userAccountId.toString} successfully"
              )
            )

      case GetBookshelf(sessionId, replyTo) =>
        if !sessionValidator.validateSessionForUser(sessionId, state.userAccountId) then
          Effect.reply(replyTo)(
            BookOperationResponse(
              success = false,
              message = "Invalid session - user must be logged in or session does not belong to this user"
            )
          )
        else Effect.reply(replyTo)(BookshelfViewResponse(state.bookshelf))

      case ChangeSorter(newSorter, sessionId, replyTo) =>
        if !sessionValidator.validateSessionForUser(sessionId, state.userAccountId) then
          Effect.reply(replyTo)(
            BookOperationResponse(
              success = false,
              message = "Invalid session - user must be logged in or session does not belong to this user"
            )
          )
        else
          Effect
            .persist(SorterChanged(state.userAccountId, newSorter))
            .thenReply(replyTo)(_ =>
              BookOperationResponse(
                success = true,
                message = s"Sorter changed successfully for user ${state.userAccountId.toString}"
              )
            )

      case AddFilterToBook(bookId, filter, sessionId, replyTo) =>
        if !sessionValidator.validateSessionForUser(sessionId, state.userAccountId) then
          Effect.reply(replyTo)(
            BookOperationResponse(
              success = false,
              message = "Invalid session - user must be logged in or session does not belong to this user"
            )
          )
        else if !state.bookshelf.books.contains(bookId) then
          Effect.reply(replyTo)(
            BookOperationResponse(
              success = false,
              message = s"Book ${bookId.toString} not found in shelf for user ${state.userAccountId.toString}"
            )
          )
        else
          Effect
            .persist(FilterAddedToBook(state.userAccountId, bookId, filter))
            .thenReply(replyTo)(_ =>
              BookOperationResponse(
                success = true,
                message =
                  s"Filter added to book ${bookId.toString} for user ${state.userAccountId.toString} successfully"
              )
            )

      case RemoveFilterFromBook(bookId, filter, sessionId, replyTo) =>
        if !sessionValidator.validateSessionForUser(sessionId, state.userAccountId) then
          Effect.reply(replyTo)(
            BookOperationResponse(
              success = false,
              message = "Invalid session - user must be logged in or session does not belong to this user"
            )
          )
        else if !state.bookshelf.books.contains(bookId) then
          Effect.reply(replyTo)(
            BookOperationResponse(
              success = false,
              message = s"Book ${bookId.toString} not found in shelf for user ${state.userAccountId.toString}"
            )
          )
        else
          Effect
            .persist(FilterRemovedFromBook(state.userAccountId, bookId, filter))
            .thenReply(replyTo)(_ =>
              BookOperationResponse(
                success = true,
                message =
                  s"Filter removed from book ${bookId.toString} for user ${state.userAccountId.toString} successfully"
              )
            )

      case Shutdown =>
        Effect.stop[BookshelfEvent, BookshelfState]().thenNoReply()

  private def eventHandler(state: BookshelfState, event: BookshelfEvent): BookshelfState =
    event match
      case BookAddedToShelf(userAccountId, bookId, bookReference, filters) =>
        // Verify event belongs to this user
        if (userAccountId == state.userAccountId) {
          state.copy(bookshelf = state.bookshelf.addBook(bookReference, filters, bookId))
        } else {
          // Log error but don't change state for security
          println(
            s"WARNING: BookAddedToShelf event for wrong user. Expected: ${state.userAccountId}, Got: $userAccountId"
          )
          state
        }

      case BookRemovedFromShelf(userAccountId, bookId) =>
        if (userAccountId == state.userAccountId) {
          val updatedBooks = state.bookshelf.books - bookId
          state.copy(bookshelf = Bookshelf(updatedBooks, state.bookshelf.sorter))
        } else {
          println(
            s"WARNING: BookRemovedFromShelf event for wrong user. Expected: ${state.userAccountId}, Got: $userAccountId"
          )
          state
        }

      case SorterChanged(userAccountId, newSorter) =>
        if (userAccountId == state.userAccountId) {
          state.copy(bookshelf = state.bookshelf.changeSorter(newSorter))
        } else {
          println(s"WARNING: SorterChanged event for wrong user. Expected: ${state.userAccountId}, Got: $userAccountId")
          state
        }

      case FilterAddedToBook(userAccountId, bookId, filter) =>
        if (userAccountId == state.userAccountId) {
          state.copy(bookshelf = state.bookshelf.addFilter(filter, bookId))
        } else {
          println(
            s"WARNING: FilterAddedToBook event for wrong user. Expected: ${state.userAccountId}, Got: $userAccountId"
          )
          state
        }

      case FilterRemovedFromBook(userAccountId, bookId, filter) =>
        if (userAccountId == state.userAccountId) {
          state.copy(bookshelf = state.bookshelf.removeFilter(filter, bookId))
        } else {
          println(
            s"WARNING: FilterRemovedFromBook event for wrong user. Expected: ${state.userAccountId}, Got: $userAccountId"
          )
          state
        }

/**
 * Bookshelf Actor utility methods
 */
object BookshelfActorUtil:

  /**
   * create a unique actor name for a user bookshelf
   */
  def createActorName(userAccountId: UserAccountId): String =
    s"user-bookshelf-${userAccountId.breachEncapsulationIdAsString}"
