package com.handybookshelf
package controller.api.routes

import cats.effect.*
import com.handybookshelf.controller.UserLogic
import com.handybookshelf.controller.api.endpoints.UserEndpoints
import org.http4s.*
import sttp.tapir.server.http4s.Http4sServerInterpreter

object UserRoutes:
  val routes: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(
      List(
        UserEndpoints.getUserEndpoint.serverLogic[IO] { id =>
          UserLogic.getUser(id).map {
            case Some(user) => Right(user)
            case None       => Left(s"User with ID $id not found")
          }
        },
        UserEndpoints.postUserEndpoint.serverLogic[IO] { user =>
          IO.pure(Right(s"User ${user.name} created"))
        }
      )
    )
