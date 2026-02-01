package com.handybookshelf
package domain
package services

import cats.effect.Sync
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import com.handybookshelf.domain.repositories.BookRepository
import com.handybookshelf.util.ISBN

/** 書籍登録ドメインサービス
  *
  * 書籍の一意性を保証する責務を持つ。 システムの最優先要件「本を一意に登録できること（重複は許さない）」を ドメイン層で明示的に実装。
  *
  * 設計原則:
  *   - 識別子（ISBN, arXiv ID, DOI, タイトル）による重複チェックはドメインサービスで実施
  *   - BookIdは純粋なサロゲートキー（完全ランダムULID）
  *   - 副作用は型パラメータFで明示的に管理
  */
trait BookRegistrationService[F[_]]:

  /** 新しい書籍を登録する（識別子版）
    *
    * @param identifier
    *   識別子（ISBN, arXiv ID, DOI, またはタイトル）
    * @param title
    *   書籍タイトル
    * @return
    *   成功時は登録されたBookAggregate、重複時はDuplicateBookError
    */
  def register(identifier: BookIdentifier, title: NES): F[Either[DuplicateBookError, BookAggregate]]

  /** 新しい書籍を登録する（後方互換性: ISBN版）
    *
    * @param isbn
    *   ISBN（オプション）。指定された場合は重複チェックを実施
    * @param title
    *   書籍タイトル
    * @return
    *   成功時は登録されたBookAggregate、重複時はDuplicateBookError
    */
  def registerWithISBN(isbn: Option[ISBN], title: NES): F[Either[DuplicateBookError, BookAggregate]]

  /** 識別子による重複チェック
    *
    * @param identifier
    *   チェックする識別子
    * @return
    *   重複がある場合はSome(existingBook)、なければNone
    */
  def checkDuplicate(identifier: BookIdentifier): F[Option[BookAggregate]]

  /** ISBNによる重複チェック（後方互換性）
    *
    * @param isbn
    *   チェックするISBN
    * @return
    *   重複がある場合はSome(existingBook)、なければNone
    */
  def checkDuplicateByISBN(isbn: ISBN): F[Option[BookAggregate]]

object BookRegistrationService:

  /** BookRegistrationServiceの標準実装
    *
    * @param repository
    *   書籍リポジトリ
    * @tparam F
    *   エフェクト型（IO, EitherT[IO, *, *]など）
    */
  def apply[F[_]](
      repository: BookRepository
  )(using F: Sync[F]): BookRegistrationService[F] =
    new BookRegistrationServiceImpl[F](repository)

  private class BookRegistrationServiceImpl[F[_]: Sync](
      repository: BookRepository
  ) extends BookRegistrationService[F]:

    override def register(
        identifier: BookIdentifier,
        title: NES
    ): F[Either[DuplicateBookError, BookAggregate]] =
      for {
        // Step 1: 重複チェック
        duplicateCheck <- checkDuplicate(identifier)

        result <- duplicateCheck match
          case Some(existing) =>
            // 重複あり: エラーを返す
            Sync[F].pure(Left(DuplicateBookError(identifier, existing.bookId)))

          case None =>
            // 重複なし: 新しいBookIdを生成して登録
            for {
              timestamp <- Sync[F].delay(com.handybookshelf.util.Timestamp.now)
              newBookId = BookId.generate(timestamp)
              newAggregate = BookAggregate.empty(newBookId)
              registered <- Sync[F].delay {
                newAggregate.register(identifier, title).unsafeRunSync()
              }
            } yield Right(registered)
      } yield result

    override def registerWithISBN(
        isbn: Option[ISBN],
        title: NES
    ): F[Either[DuplicateBookError, BookAggregate]] =
      val identifier = isbn match
        case Some(i) => BookIdentifier.ISBN(i)
        case None    => BookIdentifier.Title(title)
      register(identifier, title)

    override def checkDuplicate(identifier: BookIdentifier): F[Option[BookAggregate]] =
      Sync[F].delay {
        repository.findByIdentifier(identifier).unsafeRunSync()
      }

    override def checkDuplicateByISBN(isbn: ISBN): F[Option[BookAggregate]] =
      checkDuplicate(BookIdentifier.ISBN(isbn))
