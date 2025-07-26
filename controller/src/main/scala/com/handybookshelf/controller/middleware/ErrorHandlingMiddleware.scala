package com.handybookshelf
package controller.middleware

import cats.effect.Async
import cats.syntax.all.*
import org.http4s.*
import org.http4s.dsl.Http4sDsl
import io.circe.generic.auto.*
import io.circe.syntax.*
import com.handybookshelf.usecase.UseCaseError
import java.time.Instant

// Standardized error response format
final case class ErrorResponse(
    error: String,
    message: String,
    code: Int,
    timestamp: String,
    path: Option[String] = None,
    details: Option[Map[String, String]] = None
)

// Error mapping utilities
object ErrorMapper {
  
  def mapUseCaseError(error: UseCaseError): (Status, ErrorResponse) = {
    val timestamp = Instant.now().toString
    error match {
      case UseCaseError.ValidationError(message) =>
        (Status.BadRequest, ErrorResponse(
          error = "VALIDATION_ERROR",
          message = message,
          code = 400,
          timestamp = timestamp
        ))
      case UseCaseError.NotFoundError(message) =>
        (Status.NotFound, ErrorResponse(
          error = "NOT_FOUND",
          message = message,
          code = 404,
          timestamp = timestamp
        ))
      case UseCaseError.ExternalServiceError(message) =>
        (Status.BadGateway, ErrorResponse(
          error = "EXTERNAL_SERVICE_ERROR",
          message = message,
          code = 502,
          timestamp = timestamp
        ))
      case UseCaseError.InternalError(message) =>
        (Status.InternalServerError, ErrorResponse(
          error = "INTERNAL_ERROR",
          message = message,
          code = 500,
          timestamp = timestamp
        ))
      case UseCaseError.TimeoutError(message) =>
        (Status.RequestTimeout, ErrorResponse(
          error = "TIMEOUT_ERROR",
          message = message,
          code = 408,
          timestamp = timestamp
        ))
    }
  }
  
  def mapGenericError(throwable: Throwable): (Status, ErrorResponse) = {
    val timestamp = Instant.now().toString
    throwable match {
      case _: IllegalArgumentException =>
        (Status.BadRequest, ErrorResponse(
          error = "BAD_REQUEST",
          message = throwable.getMessage,
          code = 400,
          timestamp = timestamp
        ))
      case _: NoSuchElementException =>
        (Status.NotFound, ErrorResponse(
          error = "NOT_FOUND",
          message = "Requested resource not found",
          code = 404,
          timestamp = timestamp
        ))
      case _ =>
        (Status.InternalServerError, ErrorResponse(
          error = "INTERNAL_SERVER_ERROR",
          message = "An unexpected error occurred",
          code = 500,
          timestamp = timestamp,
          details = Some(Map("exception" -> throwable.getClass.getSimpleName))
        ))
    }
  }
}

// Global error handling middleware
object ErrorHandlingMiddleware {
  
  def apply[F[_]: Async]: HttpMiddleware[F] = { routes =>
    HttpRoutes.of[F] { request =>
      routes.run(request).handleErrorWith { error =>
        val (status, errorResponse) = ErrorMapper.mapGenericError(error)
        val responseWithPath = errorResponse.copy(path = Some(request.uri.path.toString))
        
        // Log the error (in production, use proper logging)
        Async[F].delay(
          println(s"[ERROR] ${request.method} ${request.uri.path} - ${error.getMessage}")
        ) *> Async[F].pure(
          Response[F](status = status)
            .withEntity(responseWithPath.asJson)
            .withHeaders(Header.Raw(CIString("Content-Type"), "application/json"))
        )
      }
    }
  }
  
  // Specialized middleware for UseCase errors
  def useCaseErrorHandler[F[_]: Async]: HttpMiddleware[F] = { routes =>
    HttpRoutes.of[F] { request =>
      routes.run(request).handleErrorWith {
        case error: UseCaseError =>
          val (status, errorResponse) = ErrorMapper.mapUseCaseError(error)
          val responseWithPath = errorResponse.copy(path = Some(request.uri.path.toString))
          
          Async[F].pure(
            Response[F](status = status)
              .withEntity(responseWithPath.asJson)
              .withHeaders(Header.Raw(CIString("Content-Type"), "application/json"))
          )
        case other =>
          val (status, errorResponse) = ErrorMapper.mapGenericError(other)
          val responseWithPath = errorResponse.copy(path = Some(request.uri.path.toString))
          
          Async[F].pure(
            Response[F](status = status)
              .withEntity(responseWithPath.asJson)
              .withHeaders(Header.Raw(CIString("Content-Type"), "application/json"))
          )
      }
    }
  }
}

// Response standardization middleware
object ResponseStandardizationMiddleware {
  
  // Standard success response wrapper
  final case class SuccessResponse[T](
      success: Boolean = true,
      data: T,
      timestamp: String,
      path: Option[String] = None
  )
  
  def apply[F[_]: Async]: HttpMiddleware[F] = { routes =>
    HttpRoutes.of[F] { request =>
      routes.run(request).map { response =>
        // Only wrap successful JSON responses
        if (response.status.isSuccess && 
            response.headers.get(CIString("Content-Type")).exists(_.head.value.contains("application/json"))) {
          response // For now, keep responses as-is. Could wrap with SuccessResponse if needed
        } else {
          response
        }
      }
    }
  }
}

// Request/Response logging middleware
object RequestResponseLoggingMiddleware {
  
  def apply[F[_]: Async](
    logHeaders: Boolean = false,
    logBody: Boolean = false
  ): HttpMiddleware[F] = { routes =>
    HttpRoutes.of[F] { request =>
      val startTime = System.currentTimeMillis()
      
      for {
        _ <- if (logHeaders || logBody) {
          Async[F].delay(
            println(s"[REQUEST] ${request.method} ${request.uri.path} - Headers: ${if (logHeaders) request.headers else "hidden"}")
          )
        } else {
          Async[F].delay(
            println(s"[REQUEST] ${request.method} ${request.uri.path}")
          )
        }
        
        response <- routes.run(request)
        
        endTime = System.currentTimeMillis()
        duration = endTime - startTime
        
        _ <- Async[F].delay(
          println(s"[RESPONSE] ${request.method} ${request.uri.path} - ${response.status.code} (${duration}ms)")
        )
        
      } yield response
    }
  }
}

// Combined middleware stack
object MiddlewareStack {
  
  def standard[F[_]: Async](
    tokenService: Option[TokenService[F]] = None,
    enableAuth: Boolean = false
  ): HttpMiddleware[F] = { routes =>
    
    val baseStack = RequestResponseLoggingMiddleware[F]() andThen
                   ErrorHandlingMiddleware[F] andThen
                   ResponseStandardizationMiddleware[F]
    
    val withAuth = if (enableAuth && tokenService.isDefined) {
      baseStack andThen AuthenticationMiddleware.optional[F](tokenService.get)
    } else {
      baseStack
    }
    
    withAuth(routes)
  }
}