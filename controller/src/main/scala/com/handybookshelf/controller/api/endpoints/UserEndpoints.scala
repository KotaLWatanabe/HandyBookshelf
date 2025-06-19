package com.handybookshelf
package controller.api.endpoints

import util.controller.User
import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder}
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*

given Schema[User] = Schema.derived
given Decoder[User] = deriveDecoder[User]
given Encoder[User] = deriveEncoder[User]

object UserEndpoints:
  private val base = endpointRoot.in("user")

  val getUserEndpoint: PublicEndpoint[Int, String, User, Any] =
    base.get
      .in(path[Int]("id"))
      .out(jsonBody[User])
      .errorOut(stringBody)

  val postUserEndpoint: PublicEndpoint[User, String, String, Any] =
    base.post
      .in(jsonBody[User]) // JSONリクエストボディ
      .out(stringBody)
      .errorOut(stringBody)
