package com.handybookshelf
package controller.api.routes

import cats.effect.IO
import com.handybookshelf.controller.api.endpoints.UserEndpoints
import org.http4s.HttpRoutes
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.server.http4s.Http4sServerInterpreter

object SwaggerRoutes {
  val swaggerRoutes: HttpRoutes[IO] = Http4sServerInterpreter[IO]().toRoutes(
    SwaggerInterpreter()
      .fromEndpoints[IO](List(UserEndpoints.getUserEndpoint), "My API", "1.0")
  )
}
