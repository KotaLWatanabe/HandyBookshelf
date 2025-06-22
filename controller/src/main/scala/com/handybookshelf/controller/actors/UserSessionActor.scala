package com.handybookshelf
package controller.actors

import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import domain.UserAccountId
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import java.time.Instant
import java.util.UUID

object UserSessionActor:
  
  // Commands
  sealed trait UserSessionCommand
  
  final case class LoginUser(
    userAccountId: UserAccountId,
    replyTo: ActorRef[LoginResponse]
  ) extends UserSessionCommand
  
  final case class LogoutUser(
    userAccountId: UserAccountId,
    replyTo: ActorRef[LogoutResponse]
  ) extends UserSessionCommand
  
  final case class GetUserStatus(
    userAccountId: UserAccountId,
    replyTo: ActorRef[UserStatusResponse]
  ) extends UserSessionCommand
  
  final case class ExtendSession(
    sessionId: String,
    replyTo: ActorRef[SessionResponse]
  ) extends UserSessionCommand
  
  final case class ValidateSession(
    sessionId: String,
    replyTo: ActorRef[SessionValidationResponse]
  ) extends UserSessionCommand
  
  case object Shutdown extends UserSessionCommand
  
  // Events
  sealed trait UserSessionEvent
  final case class UserLoggedIn(
    userAccountId: UserAccountId, 
    sessionId: String, 
    loginTime: Instant
  ) extends UserSessionEvent
  
  final case class UserLoggedOut(
    userAccountId: UserAccountId, 
    sessionId: String, 
    logoutTime: Instant
  ) extends UserSessionEvent
  
  final case class SessionExtended(
    sessionId: String, 
    newExpirationTime: Instant
  ) extends UserSessionEvent
  
  final case class SessionExpired(
    sessionId: String, 
    expiredTime: Instant
  ) extends UserSessionEvent
  
  // Responses
  sealed trait UserSessionResponse
  final case class LoginResponse(
    success: Boolean, 
    message: String, 
    sessionId: Option[String] = None
  ) extends UserSessionResponse
  
  final case class LogoutResponse(
    success: Boolean, 
    message: String
  ) extends UserSessionResponse
  
  final case class UserStatusResponse(
    userAccountId: UserAccountId, 
    isLoggedIn: Boolean,
    sessionId: Option[String] = None,
    loginTime: Option[Instant] = None
  ) extends UserSessionResponse
  
  final case class SessionResponse(
    success: Boolean, 
    message: String
  ) extends UserSessionResponse
  
  final case class SessionValidationResponse(
    isValid: Boolean,
    userAccountId: Option[UserAccountId] = None
  ) extends UserSessionResponse
  
  // State
  final case class UserSessionState(
    userAccountId: UserAccountId,
    sessionId: Option[String] = None,
    isLoggedIn: Boolean = false,
    loginTime: Option[Instant] = None,
    lastActivity: Option[Instant] = None,
    expirationTime: Option[Instant] = None
  ):
    def isSessionValid: Boolean = 
      isLoggedIn && sessionId.isDefined && 
      expirationTime.exists(_.isAfter(Instant.now()))
  
  // Session configuration
  val SESSION_DURATION_HOURS = 24L
  val EXTENSION_DURATION_HOURS = 2L
  
  // JSON codecs for persistence
  given Encoder[UserSessionEvent] = deriveEncoder
  given Decoder[UserSessionEvent] = deriveDecoder
  given Encoder[UserSessionState] = deriveEncoder
  given Decoder[UserSessionState] = deriveDecoder
  
  def apply(userAccountId: UserAccountId): Behavior[UserSessionCommand] =
    EventSourcedBehavior[UserSessionCommand, UserSessionEvent, UserSessionState](
      persistenceId = PersistenceId.of("UserSession", userAccountId.breachEncapsulationIdAsString),
      emptyState = UserSessionState(userAccountId),
      commandHandler = commandHandler,
      eventHandler = eventHandler
    )
  
  private def commandHandler(
    state: UserSessionState,
    command: UserSessionCommand
  ): ReplyEffect[UserSessionEvent, UserSessionState] =
    command match
      case LoginUser(userId, replyTo) if userId == state.userAccountId =>
        if state.isSessionValid then
          Effect.reply(replyTo)(LoginResponse(
            success = true, 
            message = s"User ${userId.toString} was already logged in",
            sessionId = state.sessionId
          ))
        else
          val sessionId = UUID.randomUUID().toString
          val loginTime = Instant.now()
          Effect
            .persist(UserLoggedIn(userId, sessionId, loginTime))
            .thenReply(replyTo)(_ => LoginResponse(
              success = true, 
              message = s"User ${userId.toString} logged in successfully",
              sessionId = Some(sessionId)
            ))
      
      case LoginUser(userId, replyTo) =>
        Effect.reply(replyTo)(LoginResponse(success = false, message = "Invalid user ID"))
      
      case LogoutUser(userId, replyTo) if userId == state.userAccountId =>
        if !state.isLoggedIn then
          Effect.reply(replyTo)(LogoutResponse(
            success = true, 
            message = s"User ${userId.toString} was already logged out"
          ))
        else
          state.sessionId match
            case Some(sessionId) =>
              Effect
                .persist(UserLoggedOut(userId, sessionId, Instant.now()))
                .thenReply(replyTo)(_ => LogoutResponse(
                  success = true, 
                  message = s"User ${userId.toString} logged out successfully"
                ))
            case None =>
              Effect.reply(replyTo)(LogoutResponse(
                success = false, 
                message = "No active session found"
              ))
      
      case LogoutUser(userId, replyTo) =>
        Effect.reply(replyTo)(LogoutResponse(success = false, message = "Invalid user ID"))
      
      case GetUserStatus(_, replyTo) =>
        Effect.reply(replyTo)(UserStatusResponse(
          userAccountId = state.userAccountId,
          isLoggedIn = state.isSessionValid,
          sessionId = state.sessionId,
          loginTime = state.loginTime
        ))
      
      case ExtendSession(sessionId, replyTo) =>
        if state.sessionId.contains(sessionId) && state.isLoggedIn then
          val newExpiration = Instant.now().plusSeconds(EXTENSION_DURATION_HOURS * 3600)
          Effect
            .persist(SessionExtended(sessionId, newExpiration))
            .thenReply(replyTo)(_ => SessionResponse(
              success = true, 
              message = "Session extended successfully"
            ))
        else
          Effect.reply(replyTo)(SessionResponse(
            success = false, 
            message = "Invalid session or not logged in"
          ))
      
      case ValidateSession(sessionId, replyTo) =>
        val isValid = state.sessionId.contains(sessionId) && state.isSessionValid
        Effect.reply(replyTo)(SessionValidationResponse(
          isValid = isValid,
          userAccountId = if isValid then Some(state.userAccountId) else None
        ))
      
      case Shutdown =>
        Effect.stop[UserSessionEvent, UserSessionState]().thenNoReply()
  
  private def eventHandler(state: UserSessionState, event: UserSessionEvent): UserSessionState =
    event match
      case UserLoggedIn(_, sessionId, loginTime) =>
        val expirationTime = loginTime.plusSeconds(SESSION_DURATION_HOURS * 3600)
        state.copy(
          sessionId = Some(sessionId),
          isLoggedIn = true,
          loginTime = Some(loginTime),
          lastActivity = Some(loginTime),
          expirationTime = Some(expirationTime)
        )
      
      case UserLoggedOut(_, _, _) =>
        state.copy(
          sessionId = None,
          isLoggedIn = false,
          loginTime = None,
          lastActivity = None,
          expirationTime = None
        )
      
      case SessionExtended(_, newExpirationTime) =>
        state.copy(
          lastActivity = Some(Instant.now()),
          expirationTime = Some(newExpirationTime)
        )
      
      case SessionExpired(_, _) =>
        state.copy(
          sessionId = None,
          isLoggedIn = false,
          lastActivity = None,
          expirationTime = None
        )

/**
 * UserSession Actor utility methods
 */
object UserSessionActorUtil:
  
  /**
   * create a unique actor name for a user session
   */
  def createActorName(userAccountId: UserAccountId): String =
    s"user-session-${userAccountId.breachEncapsulationIdAsString}"