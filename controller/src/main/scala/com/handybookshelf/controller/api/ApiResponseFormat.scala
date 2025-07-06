package com.handybookshelf
package controller.api

import io.circe.{Encoder, Decoder}
import io.circe.generic.semiauto.{deriveEncoder, deriveDecoder}
import java.time.Instant

// Standardized API response envelope
final case class ApiResponse[T](
    success: Boolean,
    data: Option[T],
    message: String,
    timestamp: String = Instant.now().toString,
    errors: Option[List[ApiError]] = None,
    metadata: Option[ApiMetadata] = None
)

// Error details for API responses
final case class ApiError(
    code: String,
    message: String,
    field: Option[String] = None,
    details: Option[Map[String, String]] = None
)

// Metadata for pagination and additional info
final case class ApiMetadata(
    pagination: Option[PaginationInfo] = None,
    version: String = "1.0",
    requestId: Option[String] = None,
    executionTime: Option[Long] = None
)

final case class PaginationInfo(
    page: Int,
    size: Int,
    total: Long,
    totalPages: Int,
    hasNext: Boolean,
    hasPrevious: Boolean
)

// Response builders for common patterns
object ApiResponse {
  
  // Success responses
  def success[T](data: T, message: String = "Operation completed successfully"): ApiResponse[T] =
    ApiResponse(
      success = true,
      data = Some(data),
      message = message
    )
  
  def successWithMetadata[T](
    data: T, 
    metadata: ApiMetadata,
    message: String = "Operation completed successfully"
  ): ApiResponse[T] =
    ApiResponse(
      success = true,
      data = Some(data),
      message = message,
      metadata = Some(metadata)
    )
  
  def successEmpty(message: String = "Operation completed successfully"): ApiResponse[Unit] =
    ApiResponse(
      success = true,
      data = None,
      message = message
    )
  
  // Error responses
  def error[T](
    message: String,
    errors: List[ApiError] = List.empty
  ): ApiResponse[T] =
    ApiResponse(
      success = false,
      data = None,
      message = message,
      errors = if (errors.nonEmpty) Some(errors) else None
    )
  
  def validationError[T](errors: List[ApiError]): ApiResponse[T] =
    ApiResponse(
      success = false,
      data = None,
      message = "Validation failed",
      errors = Some(errors)
    )
  
  def notFound[T](resource: String = "Resource"): ApiResponse[T] =
    ApiResponse(
      success = false,
      data = None,
      message = s"$resource not found",
      errors = Some(List(ApiError("NOT_FOUND", s"$resource not found")))
    )
  
  def unauthorized[T](): ApiResponse[T] =
    ApiResponse(
      success = false,
      data = None,
      message = "Authentication required",
      errors = Some(List(ApiError("UNAUTHORIZED", "Authentication required")))
    )
  
  def forbidden[T](): ApiResponse[T] =
    ApiResponse(
      success = false,
      data = None,
      message = "Access denied",
      errors = Some(List(ApiError("FORBIDDEN", "Insufficient permissions")))
    )
  
  def internalError[T](message: String = "Internal server error"): ApiResponse[T] =
    ApiResponse(
      success = false,
      data = None,
      message = message,
      errors = Some(List(ApiError("INTERNAL_ERROR", message)))
    )
}

// Pagination utilities
object PaginationInfo {
  
  def create(page: Int, size: Int, total: Long): PaginationInfo = {
    val totalPages = math.ceil(total.toDouble / size.toDouble).toInt
    PaginationInfo(
      page = page,
      size = size,
      total = total,
      totalPages = totalPages,
      hasNext = page < totalPages,
      hasPrevious = page > 1
    )
  }
  
  def fromPageAndSize(page: Int, size: Int, items: List[_]): PaginationInfo = {
    create(page, size, items.size.toLong)
  }
}

// Circe encoders and decoders
object ApiResponseCodecs {
  given [T: Encoder]: Encoder[ApiResponse[T]] = deriveEncoder
  given [T: Decoder]: Decoder[ApiResponse[T]] = deriveDecoder
  given Encoder[ApiError] = deriveEncoder
  given Decoder[ApiError] = deriveDecoder
  given Encoder[ApiMetadata] = deriveEncoder
  given Decoder[ApiMetadata] = deriveDecoder
  given Encoder[PaginationInfo] = deriveEncoder
  given Decoder[PaginationInfo] = deriveDecoder
}

// HTTP status code utilities
object ApiStatusCodes {
  
  import org.http4s.Status
  
  def getStatusForResponse[T](response: ApiResponse[T]): Status = {
    if (response.success) {
      response.data match {
        case Some(_) => Status.Ok
        case None => Status.NoContent
      }
    } else {
      response.errors.flatMap(_.headOption) match {
        case Some(error) => error.code match {
          case "VALIDATION_ERROR" | "BAD_REQUEST" => Status.BadRequest
          case "UNAUTHORIZED" => Status.Unauthorized
          case "FORBIDDEN" => Status.Forbidden
          case "NOT_FOUND" => Status.NotFound
          case "TIMEOUT_ERROR" => Status.RequestTimeout
          case "EXTERNAL_SERVICE_ERROR" => Status.BadGateway
          case _ => Status.InternalServerError
        }
        case None => Status.InternalServerError
      }
    }
  }
}

// Standard list response for collections
final case class ListResponse[T](
    items: List[T],
    pagination: PaginationInfo
)

object ListResponse {
  
  def create[T](
    items: List[T],
    page: Int,
    size: Int,
    total: Long
  ): ListResponse[T] =
    ListResponse(
      items = items,
      pagination = PaginationInfo.create(page, size, total)
    )
  
  given [T: Encoder]: Encoder[ListResponse[T]] = deriveEncoder
  given [T: Decoder]: Decoder[ListResponse[T]] = deriveDecoder
}