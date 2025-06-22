package com.handybookshelf
package controller.actors

import org.apache.pekko.actor.typed.{ActorRef, ActorSystem, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.receptionist.{Receptionist, ServiceKey}
import org.apache.pekko.util.Timeout
import cats.syntax.all.*
import domain.{UserAccountId, BookId, BookReference, Filters, BookSorter}
import scala.util.chaining.*
import scala.concurrent.duration.*
import scala.util.{Success, Failure}

object SupervisorActor:
  sealed trait SupervisorCommand

  // User session management commands
  final case class LoginUser(
    userAccountId: UserAccountId,
    replyTo: ActorRef[UserSessionActor.LoginResponse]
  ) extends SupervisorCommand
  
  final case class LogoutUser(
    userAccountId: UserAccountId,
    replyTo: ActorRef[UserSessionActor.LogoutResponse]
  ) extends SupervisorCommand
  
  final case class GetUserStatus(
    userAccountId: UserAccountId,
    replyTo: ActorRef[UserSessionActor.UserStatusResponse]
  ) extends SupervisorCommand

  // Bookshelf management commands  
  final case class AddBookToShelf(
    userAccountId: UserAccountId,
    bookId: BookId,
    bookReference: BookReference,
    filters: Filters,
    sessionId: String,
    replyTo: ActorRef[BookshelfActor.BookOperationResponse]
  ) extends SupervisorCommand

  final case class RemoveBookFromShelf(
    userAccountId: UserAccountId,
    bookId: BookId,
    sessionId: String,
    replyTo: ActorRef[BookshelfActor.BookOperationResponse]
  ) extends SupervisorCommand

  final case class GetBookshelf(
    userAccountId: UserAccountId,
    sessionId: String,
    replyTo: ActorRef[BookshelfActor.BookshelfViewResponse]
  ) extends SupervisorCommand

  final case class ChangeSorter(
    userAccountId: UserAccountId,
    newSorter: BookSorter,
    sessionId: String,
    replyTo: ActorRef[BookshelfActor.BookOperationResponse]
  ) extends SupervisorCommand

  // Session validation command (internal)
  private final case class SessionValidationResult(
    sessionId: String,
    isValid: Boolean,
    userAccountId: Option[UserAccountId],
    originalCommand: SupervisorCommand
  ) extends SupervisorCommand

  // Administrative commands
  final case class GetChildren(replyTo: ActorRef[Set[String]]) extends SupervisorCommand
  case object Shutdown extends SupervisorCommand

  // Response types
  sealed trait SupervisorResponse
  final case class ActorRefs(
    sessionActor: Option[ActorRef[UserSessionActor.UserSessionCommand]],
    bookshelfActor: Option[ActorRef[BookshelfActor.BookshelfCommand]]
  ) extends SupervisorResponse

  val supervisorServiceKey: ServiceKey[SupervisorCommand] = ServiceKey("supervisor")

  def apply(): Behavior[SupervisorCommand] =
    Behaviors.setup { context =>
      context.log.info("SupervisorActor starting with new architecture...")

      // Register this actor with the receptionist
      context.system.receptionist ! Receptionist.Register(
        supervisorServiceKey,
        context.self
      )

      supervising(
        sessionActors = Map.empty,
        bookshelfActors = Map.empty,
        children = Set.empty
      )
    }

  private def supervising(
    sessionActors: Map[UserAccountId, ActorRef[UserSessionActor.UserSessionCommand]],
    bookshelfActors: Map[UserAccountId, ActorRef[BookshelfActor.BookshelfCommand]], 
    children: Set[String]
  ): Behavior[SupervisorCommand] =
    Behaviors
      .receive[SupervisorCommand] { (context, message) =>
        implicit val timeout: Timeout = 3.seconds
        
        message match
          // Session management
          case LoginUser(userAccountId, replyTo) =>
            val sessionActor = getOrCreateSessionActor(context, userAccountId, sessionActors)
            sessionActor ! UserSessionActor.LoginUser(userAccountId, replyTo)
            supervising(
              sessionActors.updated(userAccountId, sessionActor),
              bookshelfActors,
              children + UserSessionActorUtil.createActorName(userAccountId)
            )

          case LogoutUser(userAccountId, replyTo) =>
            sessionActors.get(userAccountId) match
              case Some(sessionActor) =>
                sessionActor ! UserSessionActor.LogoutUser(userAccountId, replyTo)
                Behaviors.same
              case None =>
                replyTo ! UserSessionActor.LogoutResponse(
                  success = false, 
                  message = "No active session found"
                )
                Behaviors.same

          case GetUserStatus(userAccountId, replyTo) =>
            sessionActors.get(userAccountId) match
              case Some(sessionActor) =>
                sessionActor ! UserSessionActor.GetUserStatus(userAccountId, replyTo)
                Behaviors.same
              case None =>
                replyTo ! UserSessionActor.UserStatusResponse(
                  userAccountId = userAccountId,
                  isLoggedIn = false
                )
                Behaviors.same

          // Bookshelf management (with session validation)
          case cmd@AddBookToShelf(userAccountId, bookId, bookReference, filters, sessionId, replyTo) =>
            validateSessionAndExecute(context, userAccountId, sessionId, cmd, sessionActors) {
              val bookshelfActor = getOrCreateBookshelfActor(context, userAccountId, bookshelfActors, sessionActors)
              bookshelfActor ! BookshelfActor.AddBookToShelf(bookId, bookReference, filters, sessionId, replyTo)
              supervising(
                sessionActors,
                bookshelfActors.updated(userAccountId, bookshelfActor),
                children + BookshelfActorUtil.createActorName(userAccountId)
              )
            }

          case cmd@RemoveBookFromShelf(userAccountId, bookId, sessionId, replyTo) =>
            validateSessionAndExecute(context, userAccountId, sessionId, cmd, sessionActors) {
              bookshelfActors.get(userAccountId) match
                case Some(bookshelfActor) =>
                  bookshelfActor ! BookshelfActor.RemoveBookFromShelf(bookId, sessionId, replyTo)
                  Behaviors.same
                case None =>
                  replyTo ! BookshelfActor.BookOperationResponse(
                    success = false,
                    message = "Bookshelf not found"
                  )
                  Behaviors.same
            }

          case cmd@GetBookshelf(userAccountId, sessionId, replyTo) =>
            validateSessionAndExecute(context, userAccountId, sessionId, cmd, sessionActors) {
              val bookshelfActor = getOrCreateBookshelfActor(context, userAccountId, bookshelfActors, sessionActors)
              val viewReplyTo = replyTo.asInstanceOf[ActorRef[BookshelfActor.BookshelfResponse]]
              bookshelfActor ! BookshelfActor.GetBookshelf(sessionId, viewReplyTo)
              supervising(
                sessionActors,
                bookshelfActors.updated(userAccountId, bookshelfActor),
                children + BookshelfActorUtil.createActorName(userAccountId)
              )
            }

          case cmd@ChangeSorter(userAccountId, newSorter, sessionId, replyTo) =>
            validateSessionAndExecute(context, userAccountId, sessionId, cmd, sessionActors) {
              bookshelfActors.get(userAccountId) match
                case Some(bookshelfActor) =>
                  bookshelfActor ! BookshelfActor.ChangeSorter(newSorter, sessionId, replyTo)
                  Behaviors.same
                case None =>
                  replyTo ! BookshelfActor.BookOperationResponse(
                    success = false,
                    message = "Bookshelf not found"
                  )
                  Behaviors.same
            }

          // Administrative
          case GetChildren(replyTo) =>
            replyTo ! children
            Behaviors.same

          case Shutdown =>
            context.log.info("SupervisorActor shutting down...")
            Behaviors.stopped
      }
      .receiveSignal { case (context, org.apache.pekko.actor.typed.Terminated(ref)) =>
        val childName = ref.path.name
        context.log.info(s"Child actor terminated: $childName")

        val updatedSessionActors = sessionActors.filter { case (_, actorRef) => actorRef != ref }
        val updatedBookshelfActors = bookshelfActors.filter { case (_, actorRef) => actorRef != ref }
        
        supervising(updatedSessionActors, updatedBookshelfActors, children - childName)
      }

  private def getOrCreateSessionActor(
    context: org.apache.pekko.actor.typed.scaladsl.ActorContext[SupervisorCommand],
    userAccountId: UserAccountId,
    sessionActors: Map[UserAccountId, ActorRef[UserSessionActor.UserSessionCommand]]
  ): ActorRef[UserSessionActor.UserSessionCommand] =
    sessionActors.getOrElse(userAccountId, {
      val actorName = UserSessionActorUtil.createActorName(userAccountId)
      val sessionActor = context.spawn(UserSessionActor(userAccountId), actorName)
      context.watch(sessionActor)
      context.log.info(s"Created new UserSessionActor: $actorName")
      sessionActor
    })

  private def getOrCreateBookshelfActor(
    context: org.apache.pekko.actor.typed.scaladsl.ActorContext[SupervisorCommand],
    userAccountId: UserAccountId,
    bookshelfActors: Map[UserAccountId, ActorRef[BookshelfActor.BookshelfCommand]],
    sessionActors: Map[UserAccountId, ActorRef[UserSessionActor.UserSessionCommand]]
  ): ActorRef[BookshelfActor.BookshelfCommand] =
    bookshelfActors.getOrElse(userAccountId, {
      val actorName = BookshelfActorUtil.createActorName(userAccountId)
      
      // Create session validator for this user
      val sessionValidator = new BookshelfActor.SessionValidator {
        def validateSession(sessionId: String): Boolean = {
          // Simplified validation - in production, this should be async
          sessionActors.get(userAccountId) match
            case Some(sessionActor) =>
              // For now, we'll assume the session is valid if the actor exists
              // In production, this should make an async call to validate
              true
            case None => false
        }
        
        def validateSessionForUser(sessionId: String, requestedUserAccountId: UserAccountId): Boolean = {
          // Verify that the session belongs to the requested user
          if (requestedUserAccountId == userAccountId) {
            validateSession(sessionId)
          } else {
            // Session does not belong to the requested user
            false
          }
        }
      }
      
      val bookshelfActor = context.spawn(
        BookshelfActor(userAccountId, sessionValidator), 
        actorName
      )
      context.watch(bookshelfActor)
      context.log.info(s"Created new BookshelfActor: $actorName")
      bookshelfActor
    })

  private def validateSessionAndExecute(
    context: org.apache.pekko.actor.typed.scaladsl.ActorContext[SupervisorCommand],
    userAccountId: UserAccountId,
    sessionId: String,
    originalCommand: SupervisorCommand,
    sessionActors: Map[UserAccountId, ActorRef[UserSessionActor.UserSessionCommand]]
  )(executeIfValid: => Behavior[SupervisorCommand]): Behavior[SupervisorCommand] =
    sessionActors.get(userAccountId) match
      case Some(sessionActor) =>
        // In a real implementation, we would validate the session asynchronously
        // For now, we'll execute the command directly
        executeIfValid
      case None =>
        // Handle case where no session actor exists
        originalCommand match
          case AddBookToShelf(_, _, _, _, _, replyTo) =>
            replyTo ! BookshelfActor.BookOperationResponse(
              success = false,
              message = "No active session found"
            )
          case RemoveBookFromShelf(_, _, _, replyTo) =>
            replyTo ! BookshelfActor.BookOperationResponse(
              success = false,
              message = "No active session found"
            )
          case GetBookshelf(_, _, replyTo) =>
            val operationReplyTo = replyTo.asInstanceOf[ActorRef[BookshelfActor.BookOperationResponse]]
            operationReplyTo ! BookshelfActor.BookOperationResponse(
              success = false,
              message = "No active session found"
            )
          case ChangeSorter(_, _, _, replyTo) =>
            replyTo ! BookshelfActor.BookOperationResponse(
              success = false,
              message = "No active session found"
            )
          case _ => // Other commands don't need special handling
        Behaviors.same

/**
 * Supervisor Actor utility methods
 */
object SupervisorActorUtil:

  /**
   * Create and start the supervisor actor system
   */
  def createSupervisorSystem(): ActorSystem[SupervisorActor.SupervisorCommand] =
    ActorSystem(SupervisorActor(), "HandyBookshelfSupervisor")