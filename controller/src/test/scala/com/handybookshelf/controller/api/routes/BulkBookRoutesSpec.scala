package com.handybookshelf
package controller
package api
package routes

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.http4s.{Method, Request, Status, Uri}
import org.http4s.circe.*
import org.http4s.implicits.*
import io.circe.generic.auto.*
import com.handybookshelf.controller.api.endpoints.*
import com.handybookshelf.usecase.{BulkRegisterBookCommand, BulkRegisterBookResult, BulkRegisterBookUseCase, UseCaseError}
import com.handybookshelf.adopter.{BookSearchService, BookSearchResult}
import fs2.Stream

class BulkBookRoutesSpec extends AsyncWordSpec with AsyncIOSpec with Matchers {

  // テスト用のモックUseCase
  class MockBulkRegisterBookUseCase extends BulkRegisterBookUseCase {
    override def execute(command: BulkRegisterBookCommand): IO[Either[UseCaseError, BulkRegisterBookResult]] =
      IO.pure(Right(BulkRegisterBookResult(
        requestId = "test-request-id",
        message = "Bulk registration started",
        totalBooks = command.books.length
      )))

    override def getProgressUpdates(requestId: String): Stream[IO, BulkRegistrationProgressUpdate] =
      if (requestId == "test-request-id") {
        Stream.emits(List(
          BulkRegistrationProgressUpdate(
            requestId = requestId,
            overallStatus = "started",
            totalBooks = 2,
            processedBooks = 0,
            successfulBooks = 0,
            failedBooks = 0
          ),
          BulkRegistrationProgressUpdate(
            requestId = requestId,
            overallStatus = "in_progress",
            totalBooks = 2,
            processedBooks = 1,
            successfulBooks = 1,
            failedBooks = 0
          ),
          BulkRegistrationProgressUpdate(
            requestId = requestId,
            overallStatus = "completed",
            totalBooks = 2,
            processedBooks = 2,
            successfulBooks = 2,
            failedBooks = 0
          )
        ))
      } else {
        Stream.raiseError[IO](new IllegalArgumentException("Request ID not found"))
      }

    override def getFinalResult(requestId: String): IO[Option[BulkRegistrationResult]] =
      if (requestId == "test-request-id") {
        IO.pure(Some(BulkRegistrationResult(
          requestId = requestId,
          status = "completed",
          totalBooks = 2,
          successfulBooks = 2,
          failedBooks = 0,
          results = List.empty,
          startTime = java.time.Instant.now(),
          endTime = Some(java.time.Instant.now())
        )))
      } else {
        IO.pure(None)
      }
  }

  "BulkBookRoutes" should {

    "start bulk book registration successfully" in {
      val mockUseCase = new MockBulkRegisterBookUseCase()
      val routes = BulkBookRoutes[IO](mockUseCase).routes

      val testBooks = List(
        BookData(title = "Test Book 1"),
        BookData(title = "Test Book 2")
      )

      val request = Request[IO](
        method = Method.POST,
        uri = uri"/books/bulk"
      ).withEntity(BulkBookRegistrationRequest(books = testBooks))

      for {
        response <- routes.orNotFound(request)
        _ = response.status shouldBe Status.Ok
        
        responseBody <- response.as[BulkRegistrationResponse]
        _ = responseBody.requestId shouldBe "test-request-id"
        _ = responseBody.totalBooks shouldBe 2
        _ = responseBody.message shouldBe "Bulk registration started"
        
      } yield ()
    }

    "get bulk registration progress" in {
      val mockUseCase = new MockBulkRegisterBookUseCase()
      val routes = BulkBookRoutes[IO](mockUseCase).routes

      val request = Request[IO](
        method = Method.GET,
        uri = uri"/books/bulk/progress/test-request-id"
      )

      for {
        response <- routes.orNotFound(request)
        _ = response.status shouldBe Status.Ok
        _ = response.headers.get(org.http4s.headers.`Content-Type`).map(_.value) should contain("text/event-stream")
        
        responseBody <- response.bodyText.compile.string
        _ = responseBody should include("data:")
        _ = responseBody should include("test-request-id")
        
      } yield ()
    }

    "get bulk registration final result" in {
      val mockUseCase = new MockBulkRegisterBookUseCase()
      val routes = BulkBookRoutes[IO](mockUseCase).routes

      val request = Request[IO](
        method = Method.GET,
        uri = uri"/books/bulk/result/test-request-id"
      )

      for {
        response <- routes.orNotFound(request)
        _ = response.status shouldBe Status.Ok
        
        responseBody <- response.as[BulkRegistrationResult]
        _ = responseBody.requestId shouldBe "test-request-id"
        _ = responseBody.status shouldBe "completed"
        _ = responseBody.totalBooks shouldBe 2
        _ = responseBody.successfulBooks shouldBe 2
        _ = responseBody.failedBooks shouldBe 0
        
      } yield ()
    }

    "return 404 for non-existent request ID in progress endpoint" in {
      val mockUseCase = new MockBulkRegisterBookUseCase()
      val routes = BulkBookRoutes[IO](mockUseCase).routes

      val request = Request[IO](
        method = Method.GET,
        uri = uri"/books/bulk/progress/non-existent-id"
      )

      for {
        response <- routes.orNotFound(request)
        // エラーハンドリングによりBulkRegistrationErrorが返される
        responseBody <- response.as[BulkRegistrationError]
        _ = responseBody.error should include("REQUEST_NOT_FOUND")
        
      } yield ()
    }

    "return error for non-existent request ID in result endpoint" in {
      val mockUseCase = new MockBulkRegisterBookUseCase()
      val routes = BulkBookRoutes[IO](mockUseCase).routes

      val request = Request[IO](
        method = Method.GET,
        uri = uri"/books/bulk/result/non-existent-id"
      )

      for {
        response <- routes.orNotFound(request)
        responseBody <- response.as[BulkRegistrationError]
        _ = responseBody.error shouldBe "REQUEST_NOT_FOUND"
        _ = responseBody.requestId should contain("non-existent-id")
        
      } yield ()
    }

    "handle validation errors in start endpoint" in {
      // バリデーションエラーを返すUseCaseを作成
      val failingUseCase = new BulkRegisterBookUseCase {
        override def execute(command: BulkRegisterBookCommand): IO[Either[UseCaseError, BulkRegisterBookResult]] =
          IO.pure(Left(UseCaseError.ValidationError("Books list cannot be empty")))

        override def getProgressUpdates(requestId: String): Stream[IO, BulkRegistrationProgressUpdate] =
          Stream.empty

        override def getFinalResult(requestId: String): IO[Option[BulkRegistrationResult]] =
          IO.pure(None)
      }

      val routes = BulkBookRoutes[IO](failingUseCase).routes

      val request = Request[IO](
        method = Method.POST,
        uri = uri"/books/bulk"
      ).withEntity(BulkBookRegistrationRequest(books = List.empty))

      for {
        response <- routes.orNotFound(request)
        responseBody <- response.as[BulkRegistrationError]
        _ = responseBody.error shouldBe "VALIDATION_ERROR"
        _ = responseBody.details should contain("Books list cannot be empty")
        
      } yield ()
    }
  }
}