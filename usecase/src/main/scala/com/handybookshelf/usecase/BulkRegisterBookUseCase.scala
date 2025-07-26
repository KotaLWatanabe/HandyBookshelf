package com.handybookshelf
package usecase

import cats.effect.{IO, Ref}
import cats.syntax.all.*
import fs2.{Pipe, Stream}
import fs2.concurrent.{SignallingRef, Topic}
import com.handybookshelf.controller.api.endpoints.*
import com.handybookshelf.adopter.{BookSearchService, BookSearchResult}
import com.handybookshelf.domain.{Book, BookId}
import com.handybookshelf.util.{ISBN, NES, TimestampGenerator}
import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.*

final case class BulkRegisterBookCommand(
    books: List[BookData],
    enableExternalSearch: Boolean = true,
    maxConcurrency: Int = 5
)

final case class BulkRegisterBookResult(
    requestId: String,
    message: String,
    totalBooks: Int
)

trait BulkRegisterBookUseCase:
  def execute(command: BulkRegisterBookCommand): IO[Either[UseCaseError, BulkRegisterBookResult]]
  def getProgressUpdates(requestId: String): Stream[IO, BulkRegistrationProgressUpdate]
  def getFinalResult(requestId: String): IO[Option[BulkRegistrationResult]]

class BulkRegisterBookUseCaseImpl(
    searchService: BookSearchService
) extends BulkRegisterBookUseCase:

  // 進行状況を管理するためのメモリ内ストレージ（本来はRedisやデータベースを使用）
  private val progressStore: Ref[IO, Map[String, BulkRegistrationResult]] = 
    Ref.unsafe(Map.empty[String, BulkRegistrationResult])
  
  private val progressTopics: Ref[IO, Map[String, Topic[IO, BulkRegistrationProgressUpdate]]] = 
    Ref.unsafe(Map.empty[String, Topic[IO, BulkRegistrationProgressUpdate]])

  override def execute(command: BulkRegisterBookCommand): IO[Either[UseCaseError, BulkRegisterBookResult]] =
    for {
      requestId <- IO.pure(UUID.randomUUID().toString)
      startTime = Instant.now()
      
      // 進行状況を通知するためのTopicを作成
      topic <- Topic[IO, BulkRegistrationProgressUpdate]
      _ <- progressTopics.update(_.updated(requestId, topic))
      
      // 初期進行状況を設定
      initialResult = BulkRegistrationResult(
        requestId = requestId,
        status = "started",
        totalBooks = command.books.length,
        successfulBooks = 0,
        failedBooks = 0,
        results = List.empty,
        startTime = startTime
      )
      _ <- progressStore.update(_.updated(requestId, initialResult))
      
      // バックグラウンドで大量登録処理を開始
      _ <- processBooksInBackground(requestId, command, startTime).start
      
    } yield Right(BulkRegisterBookResult(
      requestId = requestId,
      message = "Bulk registration started",
      totalBooks = command.books.length
    ))

  override def getProgressUpdates(requestId: String): Stream[IO, BulkRegistrationProgressUpdate] =
    Stream.eval(progressTopics.get.map(_.get(requestId))).flatMap {
      case Some(topic) => topic.subscribe(1000)
      case None => Stream.raiseError[IO](new IllegalArgumentException(s"Request ID not found: $requestId"))
    }

  override def getFinalResult(requestId: String): IO[Option[BulkRegistrationResult]] =
    progressStore.get.map(_.get(requestId))

  private def processBooksInBackground(
      requestId: String,
      command: BulkRegisterBookCommand,
      startTime: Instant
  ): IO[Unit] =
    for {
      topic <- progressTopics.get.map(_.apply(requestId))
      
      // 初期進行状況を通知
      initialUpdate = BulkRegistrationProgressUpdate(
        requestId = requestId,
        overallStatus = "started",
        totalBooks = command.books.length,
        processedBooks = 0,
        successfulBooks = 0,
        failedBooks = 0
      )
      _ <- topic.publish1(initialUpdate)
      
      // FS2 Streamを使用して書籍を並列処理
      results <- Stream.emits(command.books)
        .zipWithIndex
        .through(processBooksPipe(command, requestId, topic))
        .compile
        .toList
      
      endTime = Instant.now()
      successfulCount = results.count(_.status == "completed")
      failedCount = results.count(_.status == "failed")
      
      // 最終結果を保存
      finalResult = BulkRegistrationResult(
        requestId = requestId,
        status = if (failedCount == 0) "completed" else "partial_success",
        totalBooks = command.books.length,
        successfulBooks = successfulCount,
        failedBooks = failedCount,
        results = results,
        startTime = startTime,
        endTime = Some(endTime)
      )
      _ <- progressStore.update(_.updated(requestId, finalResult))
      
      // 最終進行状況を通知
      finalUpdate = BulkRegistrationProgressUpdate(
        requestId = requestId,
        overallStatus = finalResult.status,
        totalBooks = command.books.length,
        processedBooks = results.length,
        successfulBooks = successfulCount,
        failedBooks = failedCount
      )
      _ <- topic.publish1(finalUpdate)
      
    } yield ()

  private def processBooksPipe(
      command: BulkRegisterBookCommand,
      requestId: String,
      topic: Topic[IO, BulkRegistrationProgressUpdate]
  ): Pipe[IO, (BookData, Long), BookRegistrationProgress] =
    _.parEvalMap(command.maxConcurrency) { case (bookData, index) =>
      processBookWithProgress(bookData, index.toInt, command.enableExternalSearch, requestId, topic)
    }

  private def processBookWithProgress(
      bookData: BookData,
      index: Int,
      enableExternalSearch: Boolean,
      requestId: String,
      topic: Topic[IO, BulkRegistrationProgressUpdate]
  ): IO[BookRegistrationProgress] =
    for {
      // 処理開始を通知
      startProgress = BookRegistrationProgress(
        bookIndex = index,
        bookData = bookData,
        status = "processing"
      )
      _ <- publishProgress(requestId, topic, startProgress, index)
      
      // 外部検索サービスでデータをエンリッチ（オプション）
      searchResult <- if (enableExternalSearch) {
        searchService.searchAndEnrich(bookData).handleErrorWith { throwable =>
          IO.pure(BookSearchResult(
            originalData = bookData,
            searchSuccess = false,
            error = Some(throwable.getMessage)
          ))
        }
      } else {
        IO.pure(BookSearchResult(originalData = bookData, searchSuccess = false))
      }
      
      // エンリッチ完了を通知（検索が有効な場合のみ）
      enrichProgress = if (enableExternalSearch && searchResult.searchSuccess) {
        startProgress.copy(
          status = "enriched",
          enrichedData = searchResult.enrichedData
        )
      } else startProgress
      _ <- if (enableExternalSearch) publishProgress(requestId, topic, enrichProgress, index) else IO.unit
      
      // 書籍登録処理
      result <- registerBook(searchResult.enrichedData.getOrElse(bookData))
        .map { bookId =>
          BookRegistrationProgress(
            bookIndex = index,
            bookData = bookData,
            status = "completed",
            bookId = Some(bookId),
            enrichedData = searchResult.enrichedData
          )
        }
        .handleErrorWith { throwable =>
          IO.pure(BookRegistrationProgress(
            bookIndex = index,
            bookData = bookData,
            status = "failed",
            error = Some(throwable.getMessage),
            enrichedData = searchResult.enrichedData
          ))
        }
      
      // 完了を通知
      _ <- publishProgress(requestId, topic, result, index)
      
    } yield result

  private def publishProgress(
      requestId: String,
      topic: Topic[IO, BulkRegistrationProgressUpdate],
      bookProgress: BookRegistrationProgress,
      processedCount: Int
  ): IO[Unit] =
    for {
      currentState <- progressStore.get.map(_.get(requestId))
      update = currentState match {
        case Some(state) =>
          val newProcessedCount = processedCount + 1
          val newSuccessfulCount = if (bookProgress.status == "completed") state.successfulBooks + 1 else state.successfulBooks
          val newFailedCount = if (bookProgress.status == "failed") state.failedBooks + 1 else state.failedBooks
          
          BulkRegistrationProgressUpdate(
            requestId = requestId,
            overallStatus = "in_progress",
            totalBooks = state.totalBooks,
            processedBooks = newProcessedCount,
            successfulBooks = newSuccessfulCount,
            failedBooks = newFailedCount,
            currentBookProgress = Some(bookProgress)
          )
        case None =>
          BulkRegistrationProgressUpdate(
            requestId = requestId,
            overallStatus = "in_progress",
            totalBooks = 0,
            processedBooks = processedCount + 1,
            successfulBooks = if (bookProgress.status == "completed") 1 else 0,
            failedBooks = if (bookProgress.status == "failed") 1 else 0,
            currentBookProgress = Some(bookProgress)
          )
      }
      _ <- topic.publish1(update)
    } yield ()

  private def registerBook(bookData: BookData): IO[String] =
    for {
      // 簡単な書籍ID生成（実際にはドメインサービスを使用）
      bookId <- IO.pure(generateBookId(bookData.title))
      
      // バリデーション
      _ <- validateBookData(bookData)
      
      // イベント生成・保存処理をシミュレート
      _ <- IO.sleep(100.millis) // 実際のデータベース操作をシミュレート
      
      // ログ出力
      _ <- IO.println(s"Registered book: $bookId - ${bookData.title}")
      
    } yield bookId

  private def validateBookData(bookData: BookData): IO[Unit] =
    if (bookData.title.trim.isEmpty) {
      IO.raiseError(new IllegalArgumentException("Title cannot be empty"))
    } else {
      IO.unit
    }

  private def generateBookId(title: String): String =
    s"book_${title.take(10).replaceAll("[^a-zA-Z0-9]", "_")}_${System.currentTimeMillis()}"

object BulkRegisterBookUseCase:
  def create(searchService: BookSearchService): BulkRegisterBookUseCase =
    new BulkRegisterBookUseCaseImpl(searchService)