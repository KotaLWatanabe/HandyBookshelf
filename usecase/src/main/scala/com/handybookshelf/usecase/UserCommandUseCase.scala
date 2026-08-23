package com.handybookshelf
package usecase

import cats.effect.IO
import cats.syntax.all.*
import com.handybookshelf.domain.UserAccountId
import com.handybookshelf.infrastructure.{
  UserStateRepository,
  LoginUserCommand,
  LogoutUserCommand,
  UpdateUserActivityCommand
}

final case class LoginCommandRequest(
    userAccountId: String,
    sessionId: String
)

final case class LogoutCommandRequest(
    userAccountId: String,
    sessionId: String
)

final case class UpdateActivityCommandRequest(
    userAccountId: String,
    sessionId: String,
    activityTimestamp: Long
)

final case class CommandResult(
    success: Boolean,
    message: String
)

// Command Use Case - handles state changes through actor repository
class UserCommandUseCase(
    userStateRepository: UserStateRepository
) {

  def executeLogin(request: LoginCommandRequest): UsecaseEff[CommandResult] =
    for {
      userAccountId <- parseUserAccountId(request.userAccountId)
      command = LoginUserCommand(userAccountId, request.sessionId)
      _ <- fromIOUsecase(userStateRepository.executeCommand(command))
      _ <- logInfo(s"User login command executed for: ${request.userAccountId}")
    } yield CommandResult(
      success = true,
      message = "Login command executed successfully"
    )

  def executeLogout(request: LogoutCommandRequest): UsecaseEff[CommandResult] =
    for {
      userAccountId <- parseUserAccountId(request.userAccountId)
      command = LogoutUserCommand(userAccountId, request.sessionId)
      _ <- fromIOUsecase(userStateRepository.executeCommand(command))
      _ <- logInfo(s"User logout command executed for: ${request.userAccountId}")
    } yield CommandResult(
      success = true,
      message = "Logout command executed successfully"
    )

  def executeUpdateActivity(request: UpdateActivityCommandRequest): UsecaseEff[CommandResult] =
    for {
      userAccountId <- parseUserAccountId(request.userAccountId)
      command = UpdateUserActivityCommand(userAccountId, request.sessionId, request.activityTimestamp)
      _ <- fromIOUsecase(userStateRepository.executeCommand(command))
      _ <- logInfo(s"User activity update command executed for: ${request.userAccountId}")
    } yield CommandResult(
      success = true,
      message = "Activity update command executed successfully"
    )

  private def parseUserAccountId(userIdStr: String): UsecaseEff[UserAccountId] =
    try {
      val ulid = wvlet.airframe.ulid.ULID.fromString(userIdStr)
      pure(UserAccountId.create(ulid))
    } catch {
      case _: Exception =>
        error(UseCaseError.ValidationError(s"Invalid user account ID format: $userIdStr"))
    }
}

object UserCommandUseCase {
  def create(userStateRepository: UserStateRepository): UserCommandUseCase =
    new UserCommandUseCase(userStateRepository)
}
