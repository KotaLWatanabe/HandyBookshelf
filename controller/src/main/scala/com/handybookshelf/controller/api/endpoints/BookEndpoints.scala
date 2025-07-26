package com.handybookshelf
package controller.api.endpoints

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import util.{ISBN, NES}

final case class RegisterBookRequest(
    isbn: Option[String] = None,
    title: String
)

final case class RegisterBookResponse(
    bookId: String,
    message: String
)

final case class BookRegistrationError(
    error: String,
    details: Option[String] = None
)

object BookEndpoints {
  
  given Encoder[RegisterBookRequest] = deriveEncoder
  given Decoder[RegisterBookRequest] = deriveDecoder
  given Encoder[RegisterBookResponse] = deriveEncoder
  given Decoder[RegisterBookResponse] = deriveDecoder
  given Encoder[BookRegistrationError] = deriveEncoder
  given Decoder[BookRegistrationError] = deriveDecoder

  private val bookEndpointRoot = endpointRoot.in("books")

  val registerBook: PublicEndpoint[RegisterBookRequest, BookRegistrationError, RegisterBookResponse, Any] =
    bookEndpointRoot.post
      .in(jsonBody[RegisterBookRequest])
      .out(jsonBody[RegisterBookResponse])
      .errorOut(jsonBody[BookRegistrationError])
      .description("Register a new book")
      .summary("Book Registration")
      .tag("Books")

  val getBook: PublicEndpoint[String, BookRegistrationError, RegisterBookResponse, Any] =
    bookEndpointRoot.get
      .in(path[String]("bookId"))
      .out(jsonBody[RegisterBookResponse])
      .errorOut(jsonBody[BookRegistrationError])
      .description("Get book by ID")
      .summary("Get Book")
      .tag("Books")

  val allBookEndpoints = List(registerBook, getBook)
}