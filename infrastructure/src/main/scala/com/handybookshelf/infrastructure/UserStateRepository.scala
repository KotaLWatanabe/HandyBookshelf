package com.handybookshelf
package infrastructure

import cats.effect.IO
import com.handybookshelf.domain.UserAccountId

// Commands for user state changes
sealed trait UserCommand

final case class LoginUserCommand(
    userAccountId: UserAccountId,
    sessionId: String
) extends UserCommand

final case class LogoutUserCommand(
    userAccountId: UserAccountId,
    sessionId: String
) extends UserCommand

final case class UpdateUserActivityCommand(
    userAccountId: UserAccountId,
    sessionId: String,
    activityTimestamp: Long
) extends UserCommand

// Query models for user state
final case class UserState(
    userAccountId: UserAccountId,
    isLoggedIn: Boolean,
    currentSessionId: Option[String],
    lastActivity: Option[Long],
    loginCount: Int
)

final case class UserQueryResult(
    found: Boolean,
    state: Option[UserState]
)

// Repository abstraction for user state management
trait UserStateRepository {

  // Command operations (state changes)
  def executeCommand(command: UserCommand): IO[Unit]

  // Query operations (state retrieval)
  def getUserState(userAccountId: UserAccountId): IO[UserQueryResult]

  def getAllActiveUsers(): IO[List[UserState]]

  def getUsersLoggedInAfter(timestamp: Long): IO[List[UserState]]

  // Health check
  def isHealthy(): IO[Boolean]
}

// Simple in-memory implementation for testing
class InMemoryUserStateRepository extends UserStateRepository {

  private var userStates: Map[UserAccountId, UserState] = Map.empty

  def executeCommand(command: UserCommand): IO[Unit] = IO.delay {
    command match {
      case LoginUserCommand(userId, sessionId) =>
        val currentState = userStates.getOrElse(
          userId,
          UserState(
            userAccountId = userId,
            isLoggedIn = false,
            currentSessionId = None,
            lastActivity = None,
            loginCount = 0
          )
        )

        val newState = currentState.copy(
          isLoggedIn = true,
          currentSessionId = Some(sessionId),
          lastActivity = Some(System.currentTimeMillis()),
          loginCount = currentState.loginCount + 1
        )

        userStates = userStates.updated(userId, newState)

      case LogoutUserCommand(userId, _) =>
        userStates.get(userId).foreach { currentState =>
          val newState = currentState.copy(
            isLoggedIn = false,
            currentSessionId = None,
            lastActivity = Some(System.currentTimeMillis())
          )
          userStates = userStates.updated(userId, newState)
        }

      case UpdateUserActivityCommand(userId, sessionId, timestamp) =>
        userStates.get(userId).foreach { currentState =>
          if (currentState.currentSessionId.contains(sessionId)) {
            val newState = currentState.copy(
              lastActivity = Some(timestamp)
            )
            userStates = userStates.updated(userId, newState)
          }
        }
    }
  }

  def getUserState(userAccountId: UserAccountId): IO[UserQueryResult] = IO.delay {
    userStates.get(userAccountId) match {
      case Some(state) => UserQueryResult(found = true, state = Some(state))
      case None        => UserQueryResult(found = false, state = None)
    }
  }

  def getAllActiveUsers(): IO[List[UserState]] = IO.delay {
    userStates.values.filter(_.isLoggedIn).toList
  }

  def getUsersLoggedInAfter(timestamp: Long): IO[List[UserState]] = IO.delay {
    userStates.values.filter { state =>
      state.isLoggedIn && state.lastActivity.exists(_ > timestamp)
    }.toList
  }

  def isHealthy(): IO[Boolean] = IO.pure(true)
}
