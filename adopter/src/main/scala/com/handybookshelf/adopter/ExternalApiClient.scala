package com.handybookshelf
package adopter

import cats.effect.IO
import cats.syntax.all.*
import io.circe.{Decoder, Encoder, Json}
import org.http4s.{Uri, Method, Request, Response, Status}
import org.http4s.client.Client
import org.http4s.circe.*
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder
import org.typelevel.ci.CIString
import scala.concurrent.duration.*

// Base error types for external API interactions
sealed trait ExternalApiError extends Exception {
  def message: String
  def cause: Option[Throwable] = None
  override def getMessage: String = message
}

object ExternalApiError {
  final case class NetworkError(message: String, override val cause: Option[Throwable] = None) extends ExternalApiError
  final case class HttpError(status: Status, message: String, responseBody: Option[String] = None) extends ExternalApiError
  final case class ParseError(message: String, override val cause: Option[Throwable] = None) extends ExternalApiError
  final case class TimeoutError(message: String) extends ExternalApiError
  final case class ConfigurationError(message: String) extends ExternalApiError
}

// Configuration for external API clients
final case class ApiClientConfig(
    baseUrl: Uri,
    timeout: Duration = 30.seconds,
    retryAttempts: Int = 3,
    retryDelay: Duration = 1.second,
    apiKey: Option[String] = None,
    userAgent: String = "HandyBookshelf/1.0"
)

// Generic request/response types
final case class ApiRequest[T](
    method: Method,
    path: String,
    body: Option[T] = None,
    queryParams: Map[String, String] = Map.empty,
    headers: Map[String, String] = Map.empty
)

final case class ApiResponse[T](
    status: Status,
    body: T,
    headers: Map[String, String] = Map.empty
)

// Unified external API client interface
trait ExternalApiClient {
  
  def get[R: Decoder](
    path: String,
    queryParams: Map[String, String] = Map.empty,
    headers: Map[String, String] = Map.empty
  ): IO[Either[ExternalApiError, R]]
  
  def post[T: Encoder, R: Decoder](
    path: String,
    body: T,
    headers: Map[String, String] = Map.empty
  ): IO[Either[ExternalApiError, R]]
  
  def put[T: Encoder, R: Decoder](
    path: String,
    body: T,
    headers: Map[String, String] = Map.empty
  ): IO[Either[ExternalApiError, R]]
  
  def delete[R: Decoder](
    path: String,
    headers: Map[String, String] = Map.empty
  ): IO[Either[ExternalApiError, R]]
  
  def getRaw(
    path: String,
    queryParams: Map[String, String] = Map.empty,
    headers: Map[String, String] = Map.empty
  ): IO[Either[ExternalApiError, String]]
  
  def postRaw(
    path: String,
    body: String,
    headers: Map[String, String] = Map.empty
  ): IO[Either[ExternalApiError, String]]
  
  def healthCheck(): IO[Boolean]
}

// HTTP4s-based implementation
class Http4sExternalApiClient(
    client: Client[IO],
    config: ApiClientConfig
) extends ExternalApiClient {
  
  import org.http4s.implicits.*
  
  def get[R: Decoder](
    path: String,
    queryParams: Map[String, String] = Map.empty,
    headers: Map[String, String] = Map.empty
  ): IO[Either[ExternalApiError, R]] = {
    val request = ApiRequest[Unit](Method.GET, path, None, queryParams, headers)
    executeRequest[Unit, R](request)
  }
  
  def post[T: Encoder, R: Decoder](
    path: String,
    body: T,
    headers: Map[String, String] = Map.empty
  ): IO[Either[ExternalApiError, R]] = {
    val request = ApiRequest(Method.POST, path, Some(body), Map.empty, headers)
    executeRequest[T, R](request)
  }
  
  def put[T: Encoder, R: Decoder](
    path: String,
    body: T,
    headers: Map[String, String] = Map.empty
  ): IO[Either[ExternalApiError, R]] = {
    val request = ApiRequest(Method.PUT, path, Some(body), Map.empty, headers)
    executeRequest[T, R](request)
  }
  
  def delete[R: Decoder](
    path: String,
    headers: Map[String, String] = Map.empty
  ): IO[Either[ExternalApiError, R]] = {
    val request = ApiRequest[Unit](Method.DELETE, path, None, Map.empty, headers)
    executeRequest[Unit, R](request)
  }
  
  def getRaw(
    path: String,
    queryParams: Map[String, String] = Map.empty,
    headers: Map[String, String] = Map.empty
  ): IO[Either[ExternalApiError, String]] = {
    buildRequest(Method.GET, path, None, queryParams.view.mapValues(Seq(_)).toMap, headers).flatMap { req =>
      executeRawRequest(req)
    }
  }
  
  def postRaw(
    path: String,
    body: String,
    headers: Map[String, String] = Map.empty
  ): IO[Either[ExternalApiError, String]] = {
    buildRawRequest(Method.POST, path, Some(body), Map.empty, headers).flatMap { req =>
      executeRawRequest(req)
    }
  }
  
  def healthCheck(): IO[Boolean] = {
    val healthPath = "/health" // Can be configured
    getRaw(healthPath).map(_.isRight).handleError(_ => false)
  }
  
  private def executeRequest[T: Encoder, R: Decoder](
    apiRequest: ApiRequest[T]
  ): IO[Either[ExternalApiError, R]] = {
    (for {
      req <- buildRequest(
        apiRequest.method,
        apiRequest.path,
        apiRequest.body,
        apiRequest.queryParams.view.mapValues(Seq(_)).toMap,
        apiRequest.headers
      )
      response <- client.run(req).use { resp =>
        if (resp.status.isSuccess) {
          resp.as[R].map(Right(_))
        } else {
          resp.bodyText.compile.string.map { body =>
            Left(ExternalApiError.HttpError(resp.status, s"HTTP ${resp.status.code}", Some(body)))
          }
        }
      }
    } yield response).handleError { error =>
      Left(ExternalApiError.NetworkError(s"Network error: ${error.getMessage}", Some(error)))
    }
  }
  
  private def executeRawRequest(request: Request[IO]): IO[Either[ExternalApiError, String]] = {
    client.run(request).use { resp =>
      if (resp.status.isSuccess) {
        resp.bodyText.compile.string.map(Right(_))
      } else {
        resp.bodyText.compile.string.map { body =>
          Left(ExternalApiError.HttpError(resp.status, s"HTTP ${resp.status.code}", Some(body)))
        }
      }
    }.handleError { error =>
      Left(ExternalApiError.NetworkError(s"Network error: ${error.getMessage}", Some(error)))
    }
  }
  
  private def buildRequest[T: Encoder](
    method: Method,
    path: String,
    body: Option[T],
    queryParams: Map[String, Seq[String]],
    headers: Map[String, String]
  ): IO[Request[IO]] = {
    IO.delay {
      val uri = config.baseUrl.addPath(path).setQueryParams(queryParams)
      
      val baseRequest = Request[IO](method = method, uri = uri)
        .putHeaders(
          org.http4s.headers.`User-Agent`(org.http4s.ProductId(config.userAgent)),
          org.http4s.headers.`Content-Type`(org.http4s.MediaType.application.json)
        )
      
      val requestWithHeaders = headers.foldLeft(baseRequest) { case (req, (key, value)) =>
        req.putHeaders(org.http4s.Header.Raw(CIString(key), value))
      }
      
      val requestWithAuth = config.apiKey.fold(requestWithHeaders) { apiKey =>
        requestWithHeaders.putHeaders(org.http4s.headers.Authorization(org.http4s.Credentials.Token(org.http4s.AuthScheme.Bearer, apiKey)))
      }
      
      body.fold(requestWithAuth)(b => requestWithAuth.withEntity(b))
    }
  }
  
  private def buildRawRequest(
    method: Method,
    path: String,
    body: Option[String],
    queryParams: Map[String, String],
    headers: Map[String, String]
  ): IO[Request[IO]] = {
    IO.delay {
      val uri = config.baseUrl.addPath(path).setQueryParams(queryParams.view.mapValues(Seq(_)).toMap)
      
      val baseRequest = Request[IO](method = method, uri = uri)
        .putHeaders(org.http4s.headers.`User-Agent`(org.http4s.ProductId(config.userAgent)))
      
      val requestWithHeaders = headers.foldLeft(baseRequest) { case (req, (key, value)) =>
        req.putHeaders(org.http4s.Header.Raw(CIString(key), value))
      }
      
      val requestWithAuth = config.apiKey.fold(requestWithHeaders) { apiKey =>
        requestWithHeaders.putHeaders(org.http4s.headers.Authorization(org.http4s.Credentials.Token(org.http4s.AuthScheme.Bearer, apiKey)))
      }
      
      body.fold(requestWithAuth)(b => requestWithAuth.withEntity(b))
    }
  }
}

object ExternalApiClient {
  
  def create(client: Client[IO], config: ApiClientConfig): ExternalApiClient =
    new Http4sExternalApiClient(client, config)
  
  // Factory method for common configurations
  def createForService(
    client: Client[IO],
    baseUrl: String,
    apiKey: Option[String] = None,
    timeout: Duration = 30.seconds
  ): IO[ExternalApiClient] = {
    Uri.fromString(baseUrl) match {
      case Right(uri) =>
        val config = ApiClientConfig(
          baseUrl = uri,
          timeout = timeout,
          apiKey = apiKey
        )
        IO.pure(create(client, config))
      case Left(error) =>
        IO.raiseError(new IllegalArgumentException(s"Invalid base URL: $baseUrl, error: $error"))
    }
  }
}