package com.handybookshelf
package controller
package api
package routes

import cats.effect.Async
import cats.syntax.all.*
import com.handybookshelf.controller.api.documentation.OpenApiDocumentation
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter

class DocumentationRoutes[F[_]: Async] {

  private val swaggerEndpoints = SwaggerInterpreter()
    .fromServerEndpoints[F](
      List(
        OpenApiDocumentation.openApiSpecEndpoint.serverLogicSuccess[F](_ => 
          Async[F].pure(OpenApiDocumentation.openApiYaml)
        ),
        OpenApiDocumentation.openApiJsonEndpoint.serverLogicSuccess[F](_ => 
          Async[F].pure(OpenApiDocumentation.openApiJson)
        )
      ),
      "HandyBookshelf API",
      "1.0.0"
    )

  val routes: HttpRoutes[F] = {
    val interpreter = Http4sServerInterpreter[F]()
    
    // Swagger UI routes
    val swaggerRoutes = interpreter.toRoutes(swaggerEndpoints)
    
    // OpenAPI spec routes
    val specRoutes = interpreter.toRoutes(
      List(
        OpenApiDocumentation.openApiSpecEndpoint.serverLogicSuccess[F](_ => 
          Async[F].pure(OpenApiDocumentation.openApiYaml)
        ),
        OpenApiDocumentation.openApiJsonEndpoint.serverLogicSuccess[F](_ => 
          Async[F].pure(OpenApiDocumentation.openApiJson)
        )
      )
    )
    
    swaggerRoutes <+> specRoutes
  }
}

object DocumentationRoutes {
  def apply[F[_]: Async]: DocumentationRoutes[F] =
    new DocumentationRoutes[F]
}