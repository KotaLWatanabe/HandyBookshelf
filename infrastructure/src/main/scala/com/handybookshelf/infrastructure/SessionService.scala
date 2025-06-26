package com.handybookshelf
package infrastructure

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.syntax.traverse.*
import domain.UserAccountId
import util.Timestamp
import java.time.Instant
import java.util.UUID
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

// Session domain models
final case class SessionId(value: String) extends AnyVal

final case class UserSession(
    userAccountId: UserAccountId,
    sessionId: SessionId,
    loginTime: Instant,
    lastActivity: Instant,
    expirationTime: Instant
):
  def isValid: Boolean   = expirationTime.isAfter(Instant.now())
  def isExpired: Boolean = !isValid

// Session events for event sourcing
sealed trait SessionEvent
final case class UserLoggedIn(
    userAccountId: UserAccountId,
    sessionId: SessionId,
    loginTime: Instant
) extends SessionEvent

final case class UserLoggedOut(
    userAccountId: UserAccountId,
    sessionId: SessionId,
    logoutTime: Instant
) extends SessionEvent

final case class SessionExtended(
    sessionId: SessionId,
    newExpirationTime: Instant
) extends SessionEvent

final case class SessionExpired(
    sessionId: SessionId,
    expiredTime: Instant
) extends SessionEvent

// Session configuration
object SessionConfig:
  val SESSION_DURATION_HOURS   = 24L
  val EXTENSION_DURATION_HOURS = 2L

// Session service responses
sealed trait SessionResult
final case class LoginResult(
    success: Boolean,
    message: String,
    sessionId: Option[SessionId] = None
) extends SessionResult

final case class LogoutResult(
    success: Boolean,
    message: String
) extends SessionResult

final case class ValidationResult(
    isValid: Boolean,
    userAccountId: Option[UserAccountId] = None
) extends SessionResult

final case class UserStatusResult(
    userAccountId: UserAccountId,
    isLoggedIn: Boolean,
    sessionId: Option[SessionId] = None,
    loginTime: Option[Instant] = None
) extends SessionResult

final case class ExtensionResult(
    success: Boolean,
    message: String
) extends SessionResult

// JSON codecs for persistence
given sessionIdEncoder: Encoder[SessionId]       = Encoder.encodeString.contramap(_.value)
given sessionIdDecoder: Decoder[SessionId]       = Decoder.decodeString.map(SessionId.apply)
given userSessionEncoder: Encoder[UserSession]   = deriveEncoder
given userSessionDecoder: Decoder[UserSession]   = deriveDecoder
given sessionEventEncoder: Encoder[SessionEvent] = deriveEncoder
given sessionEventDecoder: Decoder[SessionEvent] = deriveDecoder

// Session service trait
trait SessionService:
  def login(userAccountId: UserAccountId): IO[LoginResult]
  def logout(userAccountId: UserAccountId): IO[LogoutResult]
  def getUserStatus(userAccountId: UserAccountId): IO[UserStatusResult]
  def extendSession(sessionId: SessionId): IO[ExtensionResult]
  def validateSession(sessionId: SessionId): IO[ValidationResult]
  def cleanupExpiredSessions: IO[Unit]

// Cats-effect based session service implementation
class CatsEffectSessionService(
    eventStore: EventStore,
    sessionStore: Ref[IO, Map[SessionId, UserSession]]
) extends SessionService:

  def login(userAccountId: UserAccountId): IO[LoginResult] =
    for {
      currentSessions <- sessionStore.get
      existingSession = currentSessions.values.find(_.userAccountId == userAccountId)
      result <- existingSession match
        case Some(session) if session.isValid =>
          IO.pure(
            LoginResult(
              success = true,
              message = s"User ${userAccountId.toString} was already logged in",
              sessionId = Some(session.sessionId)
            )
          )
        case _ =>
          createNewSession(userAccountId)
    } yield result

  def logout(userAccountId: UserAccountId): IO[LogoutResult] =
    for {
      currentSessions <- sessionStore.get
      existingSession = currentSessions.values.find(_.userAccountId == userAccountId)
      result <- existingSession match
        case Some(session) if session.isValid =>
          for {
            now <- IO(Instant.now())
            logoutEvent = UserLoggedOut(userAccountId, session.sessionId, now)
            _ <- persistEvent(userAccountId, logoutEvent)
            _ <- sessionStore.update(_.removed(session.sessionId))
          } yield LogoutResult(
            success = true,
            message = s"User ${userAccountId.toString} logged out successfully"
          )
        case _ =>
          IO.pure(
            LogoutResult(
              success = true,
              message = s"User ${userAccountId.toString} was already logged out"
            )
          )
    } yield result

  def getUserStatus(userAccountId: UserAccountId): IO[UserStatusResult] =
    for {
      currentSessions <- sessionStore.get
      existingSession = currentSessions.values.find(_.userAccountId == userAccountId)
    } yield existingSession match
      case Some(session) if session.isValid =>
        UserStatusResult(
          userAccountId = userAccountId,
          isLoggedIn = true,
          sessionId = Some(session.sessionId),
          loginTime = Some(session.loginTime)
        )
      case _ =>
        UserStatusResult(
          userAccountId = userAccountId,
          isLoggedIn = false
        )

  def extendSession(sessionId: SessionId): IO[ExtensionResult] =
    for {
      currentSessions <- sessionStore.get
      result <- currentSessions.get(sessionId) match
        case Some(session) if session.isValid =>
          for {
            now <- IO(Instant.now())
            newExpiration = now.plusSeconds(SessionConfig.EXTENSION_DURATION_HOURS * 3600)
            extendEvent   = SessionExtended(sessionId, newExpiration)
            _ <- persistEvent(session.userAccountId, extendEvent)
            updatedSession = session.copy(
              lastActivity = now,
              expirationTime = newExpiration
            )
            _ <- sessionStore.update(_.updated(sessionId, updatedSession))
          } yield ExtensionResult(
            success = true,
            message = "Session extended successfully"
          )
        case _ =>
          IO.pure(
            ExtensionResult(
              success = false,
              message = "Invalid session or session expired"
            )
          )
    } yield result

  def validateSession(sessionId: SessionId): IO[ValidationResult] =
    for {
      currentSessions <- sessionStore.get
    } yield currentSessions.get(sessionId) match
      case Some(session) if session.isValid =>
        ValidationResult(
          isValid = true,
          userAccountId = Some(session.userAccountId)
        )
      case _ =>
        ValidationResult(isValid = false)

  def cleanupExpiredSessions: IO[Unit] =
    for {
      currentSessions <- sessionStore.get
      now             <- IO(Instant.now())
      expiredSessions = currentSessions.filter { case (_, session) => session.isExpired }
      _ <- expiredSessions.toList.traverse { case (sessionId, session) =>
        for {
          expiredEvent <- IO.pure(SessionExpired(sessionId, now))
          _            <- persistEvent(session.userAccountId, expiredEvent)
        } yield ()
      }
      _ <- sessionStore.update(sessions => sessions -- expiredSessions.keys)
    } yield ()

  private def createNewSession(userAccountId: UserAccountId): IO[LoginResult] =
    for {
      sessionId <- IO(SessionId(UUID.randomUUID().toString))
      now       <- IO(Instant.now())
      expirationTime = now.plusSeconds(SessionConfig.SESSION_DURATION_HOURS * 3600)
      loginEvent     = UserLoggedIn(userAccountId, sessionId, now)
      _ <- persistEvent(userAccountId, loginEvent)
      session = UserSession(
        userAccountId = userAccountId,
        sessionId = sessionId,
        loginTime = now,
        lastActivity = now,
        expirationTime = expirationTime
      )
      _ <- sessionStore.update(_.updated(sessionId, session))
    } yield LoginResult(
      success = true,
      message = s"User ${userAccountId.toString} logged in successfully",
      sessionId = Some(sessionId)
    )

  private def persistEvent(userAccountId: UserAccountId, event: SessionEvent): IO[Unit] =
    val streamId = StreamId(s"UserSession-${userAccountId.breachEncapsulationIdAsString}")
    for {
      timestamp <- Timestamp.now
      storedEvent = new StoredEvent {
        type E = SessionEvent
        def event: SessionEvent = event
        def metadata: StreamMetadata = StreamMetadata(
          streamId = streamId,
          version = EventVersion.any, // Let the event store handle versioning
          timestamp = timestamp
        )
      }
      _ <- eventStore.saveEvents(streamId, List(storedEvent))
    } yield ()

// Session service factory
object SessionService:
  def create(eventStore: EventStore): IO[SessionService] =
    for {
      sessionStore <- Ref.of[IO, Map[SessionId, UserSession]](Map.empty)
      service = new CatsEffectSessionService(eventStore, sessionStore)
      // Load existing sessions from event store on startup
      _ <- loadExistingSessions(eventStore, sessionStore)
    } yield service

  private def loadExistingSessions(
      eventStore: EventStore,
      sessionStore: Ref[IO, Map[SessionId, UserSession]]
  ): IO[Unit] =
    // This would need to be implemented to replay events from the event store
    // For now, we start with an empty session store
    IO.unit
