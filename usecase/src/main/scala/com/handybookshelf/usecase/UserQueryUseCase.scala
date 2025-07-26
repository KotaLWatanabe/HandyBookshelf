package com.handybookshelf
package usecase

import cats.effect.IO
import cats.syntax.all.*
import com.handybookshelf.domain.UserAccountId
import com.handybookshelf.infrastructure.{UserStateRepository, UserState, UserQueryResult}

final case class GetUserStateRequest(
    userAccountId: String
)

final case class GetAllActiveUsersRequest()

final case class GetUsersLoggedInAfterRequest(
    timestamp: Long
)

final case class UserStateResponse(
    userAccountId: String,
    isLoggedIn: Boolean,
    currentSessionId: Option[String],
    lastActivity: Option[Long],
    loginCount: Int
)

final case class QueryResult[T](
    success: Boolean,
    data: Option[T],
    message: String
)

// Query Use Case - handles state queries through actor repository
class UserQueryUseCase(
    userStateRepository: UserStateRepository
) {

  def getUserState(request: GetUserStateRequest): UsecaseEff[QueryResult[UserStateResponse]] =
    for {
      userAccountId <- parseUserAccountId(request.userAccountId)
      queryResult <- fromIOUsecase(userStateRepository.getUserState(userAccountId))
      response = queryResult.state.map(stateToResponse)
      _ <- logInfo(s"User state query executed for: ${request.userAccountId}")
    } yield QueryResult(
      success = true,
      data = response,
      message = if (queryResult.found) "User state retrieved successfully" else "User not found"
    )

  def getAllActiveUsers(request: GetAllActiveUsersRequest): UsecaseEff[QueryResult[List[UserStateResponse]]] =
    for {
      activeUsers <- fromIOUsecase(userStateRepository.getAllActiveUsers())
      responses = activeUsers.map(stateToResponse)
      _ <- logInfo(s"Active users query executed, found ${responses.length} users")
    } yield QueryResult(
      success = true,
      data = Some(responses),
      message = s"Retrieved ${responses.length} active users"
    )

  def getUsersLoggedInAfter(request: GetUsersLoggedInAfterRequest): UsecaseEff[QueryResult[List[UserStateResponse]]] =
    for {
      filteredUsers <- fromIOUsecase(userStateRepository.getUsersLoggedInAfter(request.timestamp))
      responses = filteredUsers.map(stateToResponse)
      _ <- logInfo(s"Users logged in after ${request.timestamp} query executed, found ${responses.length} users")
    } yield QueryResult(
      success = true,
      data = Some(responses),
      message = s"Retrieved ${responses.length} users logged in after timestamp"
    )

  def getRepositoryHealth(): UsecaseEff[QueryResult[Boolean]] =
    for {
      isHealthy <- fromIOUsecase(userStateRepository.isHealthy())
      _ <- logInfo(s"Repository health check executed: $isHealthy")
    } yield QueryResult(
      success = true,
      data = Some(isHealthy),
      message = if (isHealthy) "Repository is healthy" else "Repository is unhealthy"
    )

  private def parseUserAccountId(userIdStr: String): UsecaseEff[UserAccountId] =
    try {
      val ulid = wvlet.airframe.ulid.ULID.fromString(userIdStr)
      pure(UserAccountId.create(ulid))
    } catch {
      case _: Exception =>
        error(UseCaseError.ValidationError(s"Invalid user account ID format: $userIdStr"))
    }

  private def stateToResponse(state: UserState): UserStateResponse =
    UserStateResponse(
      userAccountId = state.userAccountId.breachEncapsulationIdAsString,
      isLoggedIn = state.isLoggedIn,
      currentSessionId = state.currentSessionId,
      lastActivity = state.lastActivity,
      loginCount = state.loginCount
    )
}

object UserQueryUseCase {
  def create(userStateRepository: UserStateRepository): UserQueryUseCase =
    new UserQueryUseCase(userStateRepository)
}