package com.handybookshelf
package controller
package api
package routes

import cats.effect.Async
import cats.syntax.all.*
import com.handybookshelf.usecase.{
  UserCommandUseCase,
  UserQueryUseCase,
  LoginCommandRequest,
  LogoutCommandRequest,
  UpdateActivityCommandRequest,
  GetUserStateRequest,
  GetAllActiveUsersRequest,
  GetUsersLoggedInAfterRequest,
  UseCaseError
}
import org.http4s.{HttpRoutes, Status}
import org.http4s.dsl.Http4sDsl
import org.http4s.circe.CirceEntityCodec.*
import io.circe.generic.auto.*

class UserStateRoutes[F[_]: Async](
    commandUseCase: UserCommandUseCase,
    queryUseCase: UserQueryUseCase
) extends Http4sDsl[F] {

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    // Command routes (state changes)
    case req @ POST -> Root / "api" / "v1" / "users" / "commands" / "login" =>
      for {
        loginRequest <- req.as[LoginCommandRequest]
        result       <- commandUseCase.executeLogin(loginRequest)
        response <- result match {
          case Left(error) =>
            val status = error match {
              case UseCaseError.ValidationError(_) => Status.BadRequest
              case _                               => Status.InternalServerError
            }
            status(Map("error" -> error.message))
          case Right(success) =>
            Ok(success)
        }
      } yield response

    case req @ POST -> Root / "api" / "v1" / "users" / "commands" / "logout" =>
      for {
        logoutRequest <- req.as[LogoutCommandRequest]
        result        <- commandUseCase.executeLogout(logoutRequest)
        response <- result match {
          case Left(error) =>
            val status = error match {
              case UseCaseError.ValidationError(_) => Status.BadRequest
              case _                               => Status.InternalServerError
            }
            status(Map("error" -> error.message))
          case Right(success) =>
            Ok(success)
        }
      } yield response

    case req @ POST -> Root / "api" / "v1" / "users" / "commands" / "activity" =>
      for {
        activityRequest <- req.as[UpdateActivityCommandRequest]
        result          <- commandUseCase.executeUpdateActivity(activityRequest)
        response <- result match {
          case Left(error) =>
            val status = error match {
              case UseCaseError.ValidationError(_) => Status.BadRequest
              case _                               => Status.InternalServerError
            }
            status(Map("error" -> error.message))
          case Right(success) =>
            Ok(success)
        }
      } yield response

    // Query routes (state retrieval)
    case GET -> Root / "api" / "v1" / "users" / "state" / userAccountId =>
      for {
        request = GetUserStateRequest(userAccountId)
        result <- queryUseCase.getUserState(request)
        response <- result match {
          case Left(error) =>
            val status = error match {
              case UseCaseError.ValidationError(_) => Status.BadRequest
              case UseCaseError.NotFoundError(_)   => Status.NotFound
              case _                               => Status.InternalServerError
            }
            status(Map("error" -> error.message))
          case Right(success) =>
            if (success.data.isDefined) Ok(success) else NotFound(success)
        }
      } yield response

    case GET -> Root / "api" / "v1" / "users" / "active" =>
      for {
        request = GetAllActiveUsersRequest()
        result <- queryUseCase.getAllActiveUsers(request)
        response <- result match {
          case Left(error) =>
            InternalServerError(Map("error" -> error.message))
          case Right(success) =>
            Ok(success)
        }
      } yield response

    case GET -> Root / "api" / "v1" / "users" / "logged-in-after" / LongVar(timestamp) =>
      for {
        request = GetUsersLoggedInAfterRequest(timestamp)
        result <- queryUseCase.getUsersLoggedInAfter(request)
        response <- result match {
          case Left(error) =>
            InternalServerError(Map("error" -> error.message))
          case Right(success) =>
            Ok(success)
        }
      } yield response

    case GET -> Root / "api" / "v1" / "users" / "health" =>
      for {
        result <- queryUseCase.getRepositoryHealth()
        response <- result match {
          case Left(error) =>
            InternalServerError(Map("error" -> error.message))
          case Right(success) =>
            Ok(success)
        }
      } yield response
  }
}

object UserStateRoutes {
  def apply[F[_]: Async](
      commandUseCase: UserCommandUseCase,
      queryUseCase: UserQueryUseCase
  ): UserStateRoutes[F] =
    new UserStateRoutes[F](commandUseCase, queryUseCase)
}
