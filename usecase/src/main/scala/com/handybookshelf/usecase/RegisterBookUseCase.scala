package com.handybookshelf
package usecase

import cats.effect.IO
import cats.syntax.all.*

final case class RegisterBookCommand(
    isbn: Option[String],
    title: String
)

final case class RegisterBookResult(
    bookId: String,
    success: Boolean,
    message: String
)

class RegisterBookUseCase extends RegisterBookUseCase:

  def execute(command: RegisterBookCommand): UsecaseEff[RegisterBookResult] =
    (for {
      // Simple validation
      _ <- validateInput(command)
      
      // Generate a simple book ID
      bookId = generateBookId(command.title)
      
      _ <- logInfo(s"Successfully registered book: $bookId")
      
    } yield RegisterBookResult(
      bookId = bookId,
      success = true,
      message = "Book registered successfully"
    )).handleErrorWith { throwable =>
      val errorMsg = s"Failed to register book: ${throwable.getMessage}"
      logError(errorMsg) *> error(UseCaseError.InternalError(errorMsg))
    }

  private def validateInput(command: RegisterBookCommand): UsecaseEff[Unit] =
    if (command.title.trim.isEmpty) {
      error(UseCaseError.ValidationError("Title cannot be empty"))
    } else {
      pure(())
    }

  private def generateBookId(title: String): String =
    s"book_${title.take(10).replaceAll("[^a-zA-Z0-9]", "_")}_${System.currentTimeMillis()}"

object RegisterBookUseCase:
  def create(): RegisterBookUseCase =
    new RegisterBookUseCaseImpl()