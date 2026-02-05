package com.handybookshelf
package usecase

import cats.effect.IO
import cats.syntax.all.*
import com.handybookshelf.domain.*
import com.handybookshelf.domain.repositories.BookRepository
import com.handybookshelf.domain.services.BookRegistrationService
import com.handybookshelf.util.ISBN.isbnOpt

final case class RegisterBookCommand(
    isbn: Option[String],
    title: String
)

final case class RegisterBookResult(
    bookId: String,
    success: Boolean,
    message: String
)

trait RegisterBookUseCase:
  def execute(command: RegisterBookCommand): UsecaseEff[RegisterBookResult]

/** RegisterBookUseCaseの実装
  *
  * BookRegistrationService（ドメインサービス）とBookRepository（リポジトリ）を使用して 書籍の登録処理を行う。
  *
  * 処理フロー:
  *   1. タイトルのバリデーション
  *   2. ISBNの解析とBookIdentifierの決定
  *   3. BookRegistrationServiceで重複チェック + 集約生成
  *   4. BookRepositoryでイベント永続化
  *   5. 結果の返却
  */
class RegisterBookUseCaseImpl(
    bookRegistrationService: BookRegistrationService[IO],
    bookRepository: BookRepository
) extends RegisterBookUseCase:

  override def execute(command: RegisterBookCommand): UsecaseEff[RegisterBookResult] =
    (for {
      // 1. タイトルのバリデーション
      title <- validateTitle(command.title)

      // 2. ISBNの解析とBookIdentifierの決定
      identifier <- parseIdentifier(command.isbn, title)

      // 3. BookRegistrationServiceで重複チェック + 集約生成
      registrationResult <- fromIOUsecase(
        bookRegistrationService.register(identifier, title)
      )

      // 4. ドメインエラーをUseCaseエラーにマッピング
      aggregate <- registrationResult match
        case Right(agg) => pure(agg)
        case Left(duplicateError) =>
          error[BookAggregate](
            UseCaseError.ValidationError(
              s"Book with ${duplicateError.identifier.description} already exists (ID: ${duplicateError.existingBookId})"
            )
          )

      // 5. BookRepositoryでイベント永続化
      savedAggregate <- fromIOUsecase(bookRepository.save(aggregate))

      _ <- logInfo(s"Successfully registered book: ${savedAggregate.bookId}")

    } yield RegisterBookResult(
      bookId = savedAggregate.bookId.toString,
      success = true,
      message = "Book registered successfully"
    )).handleErrorWith { throwable =>
      val errorMsg = s"Failed to register book: ${throwable.getMessage}"
      logError(errorMsg) *> error(UseCaseError.InternalError(errorMsg))
    }

  /** タイトルのバリデーション */
  private def validateTitle(title: String): UsecaseEff[NES] =
    title.nesOpt match
      case Some(nes) => pure(nes)
      case None      => error(UseCaseError.ValidationError("Title cannot be empty"))

  /** ISBNの解析とBookIdentifierの決定 */
  private def parseIdentifier(isbn: Option[String], title: NES): UsecaseEff[BookIdentifier] =
    isbn match
      case Some(isbnStr) =>
        isbnStr.nesOpt.flatMap(_.isbnOpt) match
          case Some(validIsbn) => pure(BookIdentifier.ISBN(validIsbn))
          case None            => error(UseCaseError.ValidationError(s"Invalid ISBN format: $isbnStr"))
      case None =>
        // ISBNがない場合はタイトルで識別
        pure(BookIdentifier.Title(title))

object RegisterBookUseCase:

  /** 従来の引数なしファクトリ（後方互換性のため維持） */
  @deprecated("Use create(bookRegistrationService, bookRepository) instead", "2.0.0")
  def create(): RegisterBookUseCase =
    // 簡略化実装（テスト用）
    new SimplifiedRegisterBookUseCase()

  /** 本格実装のファクトリ */
  def create(
      bookRegistrationService: BookRegistrationService[IO],
      bookRepository: BookRepository
  ): RegisterBookUseCase =
    new RegisterBookUseCaseImpl(bookRegistrationService, bookRepository)

/** 簡略化実装（後方互換性のため維持） */
private class SimplifiedRegisterBookUseCase extends RegisterBookUseCase:

  override def execute(command: RegisterBookCommand): UsecaseEff[RegisterBookResult] =
    (for {
      _ <- validateInput(command)
      bookId = generateBookId(command.title)
      _ <- logInfo(s"Successfully registered book (simplified): $bookId")
    } yield RegisterBookResult(
      bookId = bookId,
      success = true,
      message = "Book registered successfully (simplified implementation)"
    )).handleErrorWith { throwable =>
      val errorMsg = s"Failed to register book: ${throwable.getMessage}"
      logError(errorMsg) *> error(UseCaseError.InternalError(errorMsg))
    }

  private def validateInput(command: RegisterBookCommand): UsecaseEff[Unit] =
    if command.title.trim.isEmpty then error(UseCaseError.ValidationError("Title cannot be empty"))
    else pure(())

  private def generateBookId(title: String): String =
    s"book_${title.take(10).replaceAll("[^a-zA-Z0-9]", "_")}_${System.currentTimeMillis()}"
