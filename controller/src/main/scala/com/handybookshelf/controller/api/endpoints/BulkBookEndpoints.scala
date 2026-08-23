package com.handybookshelf
package controller.api.endpoints

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import java.time.Instant
import com.handybookshelf.domain.BookData

final case class BulkBookRegistrationRequest(
    books: List[BookData],
    enableExternalSearch: Boolean = true,
    maxConcurrency: Option[Int] = None
)

enum BulkRegistrationStatus:
  case Started, InProgress, Completed, Failed

final case class BookRegistrationProgress(
    bookIndex: Int,
    bookData: BookData,
    status: String, // "processing", "completed", "failed", "enriched"
    bookId: Option[String] = None,
    enrichedData: Option[BookData] = None,
    error: Option[String] = None,
    timestamp: Instant = Instant.now()
)

final case class BulkRegistrationProgressUpdate(
    requestId: String,
    overallStatus: String, // "started", "in_progress", "completed", "failed"
    totalBooks: Int,
    processedBooks: Int,
    successfulBooks: Int,
    failedBooks: Int,
    currentBookProgress: Option[BookRegistrationProgress] = None,
    timestamp: Instant = Instant.now()
)

final case class BulkRegistrationResponse(
    requestId: String,
    message: String,
    totalBooks: Int,
    status: String = "started"
)

final case class BulkRegistrationResult(
    requestId: String,
    status: String,
    totalBooks: Int,
    successfulBooks: Int,
    failedBooks: Int,
    results: List[BookRegistrationProgress],
    startTime: Instant,
    endTime: Option[Instant] = None
)

final case class BulkRegistrationError(
    error: String,
    details: Option[String] = None,
    requestId: Option[String] = None
)

object BulkBookEndpoints {

  // JSON codecs (BookData codecs are in domain module)
  given Encoder[BulkBookRegistrationRequest]    = deriveEncoder
  given Decoder[BulkBookRegistrationRequest]    = deriveDecoder
  given Encoder[BookRegistrationProgress]       = deriveEncoder
  given Decoder[BookRegistrationProgress]       = deriveDecoder
  given Encoder[BulkRegistrationProgressUpdate] = deriveEncoder
  given Decoder[BulkRegistrationProgressUpdate] = deriveDecoder
  given Encoder[BulkRegistrationResponse]       = deriveEncoder
  given Decoder[BulkRegistrationResponse]       = deriveDecoder
  given Encoder[BulkRegistrationResult]         = deriveEncoder
  given Decoder[BulkRegistrationResult]         = deriveDecoder
  given Encoder[BulkRegistrationError]          = deriveEncoder
  given Decoder[BulkRegistrationError]          = deriveDecoder

  private val bulkBookEndpointRoot = endpointRoot.in("books").in("bulk")

  // 大量書籍登録の開始エンドポイント
  val startBulkRegistration
      : PublicEndpoint[BulkBookRegistrationRequest, BulkRegistrationError, BulkRegistrationResponse, Any] =
    bulkBookEndpointRoot.post
      .in(jsonBody[BulkBookRegistrationRequest])
      .out(jsonBody[BulkRegistrationResponse])
      .errorOut(jsonBody[BulkRegistrationError])
      .description("Start bulk book registration")
      .summary("Bulk Book Registration")
      .tag("Bulk Books")

  // 進行状況をServer-Sent Eventsで取得するエンドポイント
  val getBulkRegistrationProgress: PublicEndpoint[String, BulkRegistrationError, String, Any] =
    bulkBookEndpointRoot
      .in("progress")
      .in(path[String]("requestId"))
      .get
      .out(stringBody)
      .out(header("Content-Type", "text/event-stream"))
      .out(header("Cache-Control", "no-cache"))
      .out(header("Connection", "keep-alive"))
      .errorOut(jsonBody[BulkRegistrationError])
      .description("Get bulk registration progress via Server-Sent Events")
      .summary("Bulk Registration Progress (SSE)")
      .tag("Bulk Books")

  // 最終結果を取得するエンドポイント
  val getBulkRegistrationResult: PublicEndpoint[String, BulkRegistrationError, BulkRegistrationResult, Any] =
    bulkBookEndpointRoot
      .in("result")
      .in(path[String]("requestId"))
      .get
      .out(jsonBody[BulkRegistrationResult])
      .errorOut(jsonBody[BulkRegistrationError])
      .description("Get bulk registration final result")
      .summary("Bulk Registration Result")
      .tag("Bulk Books")

  val allBulkBookEndpoints = List(
    startBulkRegistration,
    getBulkRegistrationProgress,
    getBulkRegistrationResult
  )
}
