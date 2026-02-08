package com.handybookshelf
package controller
package api
package routes

import cats.effect.Async
import cats.syntax.all.*
import api.endpoints.*
import usecase.{RegisterBookCommand, RegisterBookUseCase, UseCaseError}
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.Http4sServerInterpreter
import api.ApiResponseCodecs.given
import api.endpoints.BookEndpoints.*

class BookRoutes[F[_]: Async](
    registerBookUseCase: RegisterBookUseCase
):

  private def handleRegisterBook(request: RegisterBookRequest): F[Either[BookRegistrationError, RegisterBookResponse]] =
    val command = RegisterBookCommand(
      isbn = request.isbn,
      title = request.title
    )

    registerBookUseCase.execute(command).flatMap {
      case Left(error) =>
        val errorResponse = error match {
          case UseCaseError.ValidationError(msg) =>
            BookRegistrationError(error = "VALIDATION_ERROR", details = Some(msg))
          case UseCaseError.NotFoundError(msg) =>
            BookRegistrationError(error = "NOT_FOUND", details = Some(msg))
          case UseCaseError.ExternalServiceError(msg) =>
            BookRegistrationError(error = "EXTERNAL_SERVICE_ERROR", details = Some(msg))
          case UseCaseError.InternalError(msg) =>
            BookRegistrationError(error = "INTERNAL_ERROR", details = Some(msg))
          case UseCaseError.TimeoutError(msg) =>
            BookRegistrationError(error = "TIMEOUT_ERROR", details = Some(msg))
        }
        Async[F].pure(Left(errorResponse))

      case Right(success) =>
        val response = RegisterBookResponse(
          bookId = success.bookId,
          message = success.message
        )
        Async[F].pure(Right(response))
    }

  private def handleGetBook(bookId: String): F[Either[BookRegistrationError, RegisterBookResponse]] =
    // For now, return a simple response - in a real implementation this would query the read model
    val response = RegisterBookResponse(
      bookId = bookId,
      message = s"Book with ID $bookId (placeholder implementation)"
    )
    Async[F].pure(Right(response))

  val routes: HttpRoutes[F] = {
    val interpreter = Http4sServerInterpreter[F]()
    interpreter.toRoutes(registerBook.serverLogic(handleRegisterBook)) <+>
      interpreter.toRoutes(getBook.serverLogic(handleGetBook))
  }

object BookRoutes:
  def apply[F[_]: Async](registerBookUseCase: RegisterBookUseCase): BookRoutes[F] =
    new BookRoutes[F](registerBookUseCase)
