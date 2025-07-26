package com.handybookshelf
package usecase

import cats.effect.{IO, Resource}
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.http4s.ember.client.EmberClientBuilder
import com.handybookshelf.controller.api.endpoints.BookData
import com.handybookshelf.adopter.{BookSearchService, BookSearchResult}
import scala.concurrent.duration.*

class BulkRegisterBookUseCaseSpec extends AsyncWordSpec with AsyncIOSpec with Matchers {

  // テスト用のモックSearchService
  class MockBookSearchService extends BookSearchService {
    override def searchAndEnrich(bookData: BookData): IO[BookSearchResult] =
      IO.pure(BookSearchResult(
        originalData = bookData,
        enrichedData = Some(bookData.copy(
          author = Some("Test Author"),
          publisher = Some("Test Publisher"),
          publishedYear = Some(2023)
        )),
        searchSuccess = true
      ))

    override def searchByTitle(title: String): IO[Either[adopter.SearchError, List[BookData]]] =
      IO.pure(Right(List(BookData(
        title = title,
        author = Some("Test Author"),
        publisher = Some("Test Publisher"),
        publishedYear = Some(2023)
      ))))

    override def searchByISBN(isbn: String): IO[Either[adopter.SearchError, Option[BookData]]] =
      IO.pure(Right(Some(BookData(
        isbn = Some(isbn),
        title = "Test Book",
        author = Some("Test Author")
      ))))
  }

  // 失敗するSearchService
  class FailingBookSearchService extends BookSearchService {
    override def searchAndEnrich(bookData: BookData): IO[BookSearchResult] =
      IO.pure(BookSearchResult(
        originalData = bookData,
        searchSuccess = false,
        error = Some("Search service unavailable")
      ))

    override def searchByTitle(title: String): IO[Either[adopter.SearchError, List[BookData]]] =
      IO.pure(Left(adopter.SearchError("Search service unavailable")))

    override def searchByISBN(isbn: String): IO[Either[adopter.SearchError, Option[BookData]]] =
      IO.pure(Left(adopter.SearchError("Search service unavailable")))
  }

  "BulkRegisterBookUseCase" should {

    "successfully process a small batch of books" in {
      val mockSearchService = new MockBookSearchService()
      val useCase = BulkRegisterBookUseCase.create(mockSearchService)

      val testBooks = List(
        BookData(title = "Test Book 1"),
        BookData(title = "Test Book 2", isbn = Some("1234567890")),
        BookData(title = "Test Book 3", author = Some("Known Author"))
      )

      val command = BulkRegisterBookCommand(
        books = testBooks,
        enableExternalSearch = true,
        maxConcurrency = 2
      )

      for {
        result <- useCase.execute(command)
        _ = result shouldBe a[Right[_, _]]
        
        bulkResult = result.right.get
        _ = bulkResult.totalBooks shouldBe 3
        _ = bulkResult.requestId should not be empty
        
        // 少し待って処理が完了するのを待つ
        _ <- IO.sleep(2.seconds)
        
        finalResult <- useCase.getFinalResult(bulkResult.requestId)
        _ = finalResult shouldBe defined
        
        final = finalResult.get
        _ = final.totalBooks shouldBe 3
        _ = final.successfulBooks shouldBe 3
        _ = final.failedBooks shouldBe 0
        _ = final.status shouldBe "completed"
        
      } yield ()
    }

    "handle search service failures gracefully" in {
      val failingSearchService = new FailingBookSearchService()
      val useCase = BulkRegisterBookUseCase.create(failingSearchService)

      val testBooks = List(
        BookData(title = "Test Book 1"),
        BookData(title = "Test Book 2")
      )

      val command = BulkRegisterBookCommand(
        books = testBooks,
        enableExternalSearch = true,
        maxConcurrency = 1
      )

      for {
        result <- useCase.execute(command)
        _ = result shouldBe a[Right[_, _]]
        
        bulkResult = result.right.get
        _ = bulkResult.totalBooks shouldBe 2
        
        // 処理完了を待つ
        _ <- IO.sleep(2.seconds)
        
        finalResult <- useCase.getFinalResult(bulkResult.requestId)
        _ = finalResult shouldBe defined
        
        final = finalResult.get
        _ = final.totalBooks shouldBe 2
        _ = final.successfulBooks shouldBe 2 // 検索が失敗しても登録は成功する
        _ = final.failedBooks shouldBe 0
        
      } yield ()
    }

    "process books without external search" in {
      val mockSearchService = new MockBookSearchService()
      val useCase = BulkRegisterBookUseCase.create(mockSearchService)

      val testBooks = List(
        BookData(title = "Test Book 1"),
        BookData(title = "Test Book 2")
      )

      val command = BulkRegisterBookCommand(
        books = testBooks,
        enableExternalSearch = false,
        maxConcurrency = 1
      )

      for {
        result <- useCase.execute(command)
        _ = result shouldBe a[Right[_, _]]
        
        bulkResult = result.right.get
        _ = bulkResult.totalBooks shouldBe 2
        
        // 処理完了を待つ
        _ <- IO.sleep(1.second)
        
        finalResult <- useCase.getFinalResult(bulkResult.requestId)
        _ = finalResult shouldBe defined
        
        final = finalResult.get
        _ = final.totalBooks shouldBe 2
        _ = final.successfulBooks shouldBe 2
        _ = final.failedBooks shouldBe 0
        
      } yield ()
    }

    "handle validation errors" in {
      val mockSearchService = new MockBookSearchService()
      val useCase = BulkRegisterBookUseCase.create(mockSearchService)

      val testBooks = List(
        BookData(title = "Valid Book"),
        BookData(title = ""), // 空のタイトル
        BookData(title = "Another Valid Book")
      )

      val command = BulkRegisterBookCommand(
        books = testBooks,
        enableExternalSearch = false,
        maxConcurrency = 1
      )

      for {
        result <- useCase.execute(command)
        _ = result shouldBe a[Right[_, _]]
        
        bulkResult = result.right.get
        _ = bulkResult.totalBooks shouldBe 3
        
        // 処理完了を待つ
        _ <- IO.sleep(2.seconds)
        
        finalResult <- useCase.getFinalResult(bulkResult.requestId)
        _ = finalResult shouldBe defined
        
        final = finalResult.get
        _ = final.totalBooks shouldBe 3
        _ = final.successfulBooks shouldBe 2 // 有効な書籍のみ成功
        _ = final.failedBooks shouldBe 1 // 空タイトルは失敗
        _ = final.status shouldBe "partial_success"
        
      } yield ()
    }

    "provide progress updates" in {
      val mockSearchService = new MockBookSearchService()
      val useCase = BulkRegisterBookUseCase.create(mockSearchService)

      val testBooks = List(
        BookData(title = "Test Book 1"),
        BookData(title = "Test Book 2"),
        BookData(title = "Test Book 3")
      )

      val command = BulkRegisterBookCommand(
        books = testBooks,
        enableExternalSearch = true,
        maxConcurrency = 1
      )

      for {
        result <- useCase.execute(command)
        _ = result shouldBe a[Right[_, _]]
        
        bulkResult = result.right.get
        requestId = bulkResult.requestId
        
        // 進行状況のストリームを取得して最初の数個の更新を確認
        progressUpdates <- useCase.getProgressUpdates(requestId)
          .take(5) // 最初の5つの更新のみ取得
          .compile
          .toList
          .timeout(5.seconds)
        
        _ = progressUpdates should not be empty
        _ = progressUpdates.head.requestId shouldBe requestId
        _ = progressUpdates.head.totalBooks shouldBe 3
        
      } yield ()
    }

    "return None for non-existent request ID" in {
      val mockSearchService = new MockBookSearchService()
      val useCase = BulkRegisterBookUseCase.create(mockSearchService)

      for {
        result <- useCase.getFinalResult("non-existent-id")
        _ = result shouldBe None
      } yield ()
    }
  }
}