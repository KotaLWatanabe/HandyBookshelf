package com.handybookshelf
package controller
package api
package routes

import cats.effect.Async
import cats.syntax.all.*
import fs2.Stream
import api.endpoints.*
import usecase.{BulkRegisterBookCommand, BulkRegisterBookUseCase, UseCaseError}
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.Http4sServerInterpreter
import api.ApiResponseCodecs.given
import api.endpoints.BulkBookEndpoints.*
import io.circe.syntax.*
import scala.concurrent.duration.*

class BulkBookRoutes[F[_]: Async](
    bulkRegisterBookUseCase: BulkRegisterBookUseCase
):

  private def handleStartBulkRegistration(
      request: BulkBookRegistrationRequest
  ): F[Either[BulkRegistrationError, BulkRegistrationResponse]] =
    val command = BulkRegisterBookCommand(
      books = request.books,
      enableExternalSearch = request.enableExternalSearch,
      maxConcurrency = request.maxConcurrency.getOrElse(5)
    )

    bulkRegisterBookUseCase.execute(command).map {
      case Left(error) =>
        val errorResponse = error match {
          case UseCaseError.ValidationError(msg) =>
            BulkRegistrationError(error = "VALIDATION_ERROR", details = Some(msg))
          case UseCaseError.NotFoundError(msg) =>
            BulkRegistrationError(error = "NOT_FOUND", details = Some(msg))
          case UseCaseError.ExternalServiceError(msg) =>
            BulkRegistrationError(error = "EXTERNAL_SERVICE_ERROR", details = Some(msg))
          case UseCaseError.InternalError(msg) =>
            BulkRegistrationError(error = "INTERNAL_ERROR", details = Some(msg))
          case UseCaseError.TimeoutError(msg) =>
            BulkRegistrationError(error = "TIMEOUT_ERROR", details = Some(msg))
        }
        Left(errorResponse)

      case Right(success) =>
        val response = BulkRegistrationResponse(
          requestId = success.requestId,
          message = success.message,
          totalBooks = success.totalBooks
        )
        Right(response)
    }

  private def handleGetBulkRegistrationProgress(
      requestId: String
  ): F[Either[BulkRegistrationError, String]] =
    try {
      // Server-Sent Events形式でストリームを生成
      val progressStream = bulkRegisterBookUseCase
        .getProgressUpdates(requestId)
        .map { update =>
          val json = update.asJson.noSpaces
          s"data: $json\n\n"
        }
        .handleErrorWith { throwable =>
          Stream.emit(s"event: error\ndata: ${throwable.getMessage}\n\n")
        }
        .append(Stream.emit("event: end\ndata: stream completed\n\n"))

      // ストリームを文字列に変換（実際のSSE実装では、このアプローチではなく、
      // HTTP4sのStreamingレスポンスを使用すべき）
      progressStream.compile.string.map(Right(_))

    } catch {
      case e: IllegalArgumentException =>
        Async[F].pure(
          Left(
            BulkRegistrationError(
              error = "REQUEST_NOT_FOUND",
              details = Some(e.getMessage),
              requestId = Some(requestId)
            )
          )
        )
      case e: Exception =>
        Async[F].pure(
          Left(
            BulkRegistrationError(
              error = "INTERNAL_ERROR",
              details = Some(e.getMessage),
              requestId = Some(requestId)
            )
          )
        )
    }

  private def handleGetBulkRegistrationResult(
      requestId: String
  ): F[Either[BulkRegistrationError, BulkRegistrationResult]] =
    bulkRegisterBookUseCase.getFinalResult(requestId).map {
      case Some(result) => Right(result)
      case None =>
        Left(
          BulkRegistrationError(
            error = "REQUEST_NOT_FOUND",
            details = Some(s"No result found for request ID: $requestId"),
            requestId = Some(requestId)
          )
        )
    }

  val routes: HttpRoutes[F] = {
    val interpreter = Http4sServerInterpreter[F]()

    interpreter.toRoutes(startBulkRegistration.serverLogic(handleStartBulkRegistration)) <+>
      interpreter.toRoutes(getBulkRegistrationProgress.serverLogic(handleGetBulkRegistrationProgress)) <+>
      interpreter.toRoutes(getBulkRegistrationResult.serverLogic(handleGetBulkRegistrationResult))
  }

object BulkBookRoutes:
  def apply[F[_]: Async](bulkRegisterBookUseCase: BulkRegisterBookUseCase): BulkBookRoutes[F] =
    new BulkBookRoutes[F](bulkRegisterBookUseCase)

// より適切なSSE実装のためのヘルパー
object ServerSentEvents:
  import org.http4s.headers.{`Cache-Control`, Connection}
  import org.http4s.{CacheDirective, Header, Headers, Response, Status}
  import org.typelevel.ci.CIString

  def apply[F[_]: Async](
      stream: Stream[F, String],
      headers: Headers = Headers.empty
  ): Response[F] =
    val sseHeaders = Headers(
      Header.Raw(CIString("Content-Type"), "text/event-stream"),
      Header.Raw(CIString("Cache-Control"), "no-cache"),
      Header.Raw(CIString("Connection"), "keep-alive"),
      Header.Raw(CIString("Access-Control-Allow-Origin"), "*"),
      Header.Raw(CIString("Access-Control-Allow-Headers"), "Cache-Control")
    ) ++ headers

    Response[F](
      status = Status.Ok,
      headers = sseHeaders,
      body = stream
        .map(_.getBytes)
        .flatMap(bytes => Stream.emits(bytes))
    )

  def formatData(data: String): String                 = s"data: $data\n\n"
  def formatEvent(event: String, data: String): String = s"event: $event\ndata: $data\n\n"
  def keepAlive: String                                = ": keep-alive\n\n"
