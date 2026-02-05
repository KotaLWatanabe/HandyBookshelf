package com.handybookshelf
package usecase

import cats.data.Writer
import cats.effect.IO
import cats.syntax.all.*
import org.atnos.eff.*
import org.atnos.eff.all.*
import org.atnos.eff.syntax.all.*
import org.atnos.eff.addon.cats.effect.IOEffect.{_Io, _io}

final case class RegisterBookCommand(
    isbn: Option[String],
    title: String
)

final case class RegisterBookResult(
    bookId: String,
    success: Boolean,
    message: String
)

class RegisterBookUseCase:
  def execute[R : {_Io, _usecaseError, _writer}](command: RegisterBookCommand): Eff[R, RegisterBookResult] =
    for {
      // Simple validation
      _ <- fromEither(validateInput(command))

      // Generate a simple book ID
      bookId = generateBookId(command.title)

      _ <- logInfo(s"Successfully registered book: $bookId")

    } yield RegisterBookResult(
      bookId = bookId,
      success = true,
      message = "Book registered successfully"
    )

  private def validateInput(command: RegisterBookCommand): Either[UseCaseError, Unit] =
    Either.cond(command.title.trim.nonEmpty, (), UseCaseError.ValidationError("Title cannot be empty"))

  private def generateBookId(title: String): String =
    s"book_${title.take(10).replaceAll("[^a-zA-Z0-9]", "_")}_${System.currentTimeMillis()}"

  private def logInfo[R : _writer](message: String): Eff[R, Unit] =
    tell(message)

object RegisterBookUseCase:
  def create(): RegisterBookUseCase = new RegisterBookUseCase()
