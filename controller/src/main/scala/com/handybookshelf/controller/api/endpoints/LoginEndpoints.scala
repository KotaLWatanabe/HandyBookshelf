package com.handybookshelf
package controller.api.endpoints

import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder}
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*

// Request/Response DTOs
final case class LoginRequest(userAccountId: String)
final case class LoginResponse(success: Boolean, message: String, userAccountId: String)
final case class LogoutRequest(userAccountId: String)
final case class LogoutResponse(success: Boolean, message: String)
final case class UserStatusResponse(userAccountId: String, isLoggedIn: Boolean)

// Circe codecs
given Schema[LoginRequest] = Schema.derived
given Decoder[LoginRequest] = deriveDecoder[LoginRequest]
given Encoder[LoginRequest] = deriveEncoder[LoginRequest]

given Schema[LoginResponse] = Schema.derived
given Decoder[LoginResponse] = deriveDecoder[LoginResponse]
given Encoder[LoginResponse] = deriveEncoder[LoginResponse]

given Schema[LogoutRequest] = Schema.derived
given Decoder[LogoutRequest] = deriveDecoder[LogoutRequest]
given Encoder[LogoutRequest] = deriveEncoder[LogoutRequest]

given Schema[LogoutResponse] = Schema.derived
given Decoder[LogoutResponse] = deriveDecoder[LogoutResponse]
given Encoder[LogoutResponse] = deriveEncoder[LogoutResponse]

given Schema[UserStatusResponse] = Schema.derived
given Decoder[UserStatusResponse] = deriveDecoder[UserStatusResponse]
given Encoder[UserStatusResponse] = deriveEncoder[UserStatusResponse]

object LoginEndpoints:
  private val base = endpointRoot.in("auth")

  val loginEndpoint: PublicEndpoint[LoginRequest, String, LoginResponse, Any] =
    base.post
      .in("login")
      .in(jsonBody[LoginRequest])
      .out(jsonBody[LoginResponse])
      .errorOut(stringBody)
      .description("Login a user and create/activate UserAccountActor")

  val logoutEndpoint: PublicEndpoint[LogoutRequest, String, LogoutResponse, Any] =
    base.post
      .in("logout")
      .in(jsonBody[LogoutRequest])
      .out(jsonBody[LogoutResponse])
      .errorOut(stringBody)
      .description("Logout a user")

  val statusEndpoint: PublicEndpoint[String, String, UserStatusResponse, Any] =
    base.get
      .in("status")
      .in(path[String]("userAccountId"))
      .out(jsonBody[UserStatusResponse])
      .errorOut(stringBody)
      .description("Get user login status")