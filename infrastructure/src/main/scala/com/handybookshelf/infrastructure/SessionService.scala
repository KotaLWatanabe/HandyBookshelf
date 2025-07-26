package com.handybookshelf
package infrastructure

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.syntax.traverse.*
import com.handybookshelf.domain.UserAccountId
import com.handybookshelf.util.Timestamp
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Decoder, Encoder}

import java.util.UUID

// Session domain models
final case class SessionId(value: String) extends AnyVal

final case class UserSession(
    userAccountId: UserAccountId,
    sessionId: SessionId,
    loginTime: Timestamp,
    lastActivity: Timestamp,
    expirationTime: Timestamp
):
  def isValid: IO[Boolean]   = IO(Timestamp.now).map(now => expirationTime.isAfter(now))
  def isExpired: IO[Boolean] = isValid.map(!_)

// Session events for event sourcing
sealed trait SessionEvent
final case class UserLoggedIn(
    userAccountId: UserAccountId,
    sessionId: SessionId,
    loginTime: Timestamp
) extends SessionEvent

final case class UserLoggedOut(
    userAccountId: UserAccountId,
    sessionId: SessionId,
    logoutTime: Timestamp
) extends SessionEvent

final case class SessionExtended(
    sessionId: SessionId,
    newExpirationTime: Timestamp
) extends SessionEvent

final case class SessionExpired(
    sessionId: SessionId,
    expiredTime: Timestamp
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
    loginTime: Option[Timestamp] = None
) extends SessionResult

final case class ExtensionResult(
    success: Boolean,
    message: String
) extends SessionResult

// JSON codecs for persistence
given sessionIdEncoder: Encoder[SessionId]         = Encoder.encodeString.contramap(_.value)
given sessionIdDecoder: Decoder[SessionId]         = Decoder.decodeString.map(SessionId.apply)
given userAccountIdEncoder: Encoder[UserAccountId] = Encoder.encodeString.contramap(_.breachEncapsulationIdAsString)
given userAccountIdDecoder: Decoder[UserAccountId] =
  Decoder.decodeString.map(s => UserAccountId.create(wvlet.airframe.ulid.ULID.fromString(s)))
given timestampEncoder: Encoder[Timestamp] = Encoder.encodeString.contramap(_.toString)
given timestampDecoder: Decoder[Timestamp] = Decoder.decodeString.emap { s =>
  try {
    Right(
      Timestamp.fromEpochMillis(java.time.LocalDateTime.parse(s).atZone(Timestamp.systemZoneId).toInstant.toEpochMilli)
    )
  } catch {
    case _: Exception => Left(s"Invalid timestamp format: $s")
  }
}
given userSessionCodec: Codec[UserSession]     = deriveCodec
given sessionEventEncoder: Codec[SessionEvent] = deriveCodec

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
      sessionIsValidOpt <- existingSession.traverse(session => session.isValid.map((session, _)))
      result <- sessionIsValidOpt match
        case Some((session, isValid)) if isValid =>
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
      sessionIsValidOpt <- existingSession.traverse(session => session.isValid.map((session, _)))
      result <- sessionIsValidOpt match
        case Some((session, isValid)) if isValid =>
          for {
            now <- IO(Timestamp.now)
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
      sessionIsValidOpt <- existingSession.traverse(session => session.isValid.map((session, _)))
    } yield sessionIsValidOpt match
      case Some((session, isValid)) if isValid =>
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
      currentSessions   <- sessionStore.get
      sessionIsValidOpt <- currentSessions.get(sessionId).traverse(session => session.isValid.map((session, _)))
      result <- sessionIsValidOpt match
        case Some((session, isValid)) if isValid =>
          for {
            now <- IO(Timestamp.now)
            newExpiration = now.plusHours(SessionConfig.EXTENSION_DURATION_HOURS)
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
      currentSessions   <- sessionStore.get
      sessionIsValidOpt <- currentSessions.get(sessionId).traverse(session => session.isValid.map((session, _)))
    } yield sessionIsValidOpt match
      case Some((session, isValid)) if isValid =>
        ValidationResult(
          isValid = true,
          userAccountId = Some(session.userAccountId)
        )
      case _ =>
        ValidationResult(isValid = false)

  def cleanupExpiredSessions: IO[Unit] =
    for {
      currentSessions <- sessionStore.get
      now             <- IO(Timestamp.now)
      expiredSessions <- currentSessions.values.toList.traverse(session => session.isExpired.map((session, _)))
      _ <- expiredSessions.traverse { case (session, _) =>
        for {
          expiredEvent <- IO.pure(SessionExpired(session.sessionId, now))
          _            <- persistEvent(session.userAccountId, expiredEvent)
        } yield ()
      }
      _ <- sessionStore.update(sessions => sessions.removedAll(expiredSessions.map(_._1.sessionId)))
    } yield ()

  private def createNewSession(userAccountId: UserAccountId): IO[LoginResult] =
    for {
      sessionId <- IO(SessionId(UUID.randomUUID().toString))
      now       <- IO(Timestamp.now)
      expirationTime = now.plusHours(SessionConfig.SESSION_DURATION_HOURS)
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

  private def persistEvent(userAccountId: UserAccountId, sessionEvent: SessionEvent): IO[Unit] =
    val streamId = StreamId(s"UserSession-${userAccountId.breachEncapsulationIdAsString}")
    for {
      timestamp <- IO(Timestamp.now)
      storedEvent = new StoredEvent {
        type E = SessionEvent
        def event: SessionEvent = sessionEvent
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
      _ <- loadExistingSessions()
    } yield service

  private def loadExistingSessions(): IO[Unit] =
    // This would need to be implemented to replay events from the event store
    // For now, we start with an empty session store
    IO.unit
