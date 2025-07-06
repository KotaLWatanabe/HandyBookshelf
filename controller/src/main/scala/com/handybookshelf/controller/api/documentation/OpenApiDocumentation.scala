package com.handybookshelf
package controller.api.documentation

import sttp.tapir.*
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.openapi.OpenAPI
import sttp.tapir.openapi.Info
import sttp.tapir.openapi.Server
import sttp.tapir.openapi.Contact
import sttp.tapir.openapi.License
import com.handybookshelf.controller.api.endpoints.{BookEndpoints, LoginEndpoints}

object OpenApiDocumentation {
  
  // API Information
  private val apiInfo = Info(
    title = "HandyBookshelf API",
    version = "1.0.0",
    description = Some("""
      |HandyBookshelf API provides comprehensive book management functionality 
      |including book registration, user authentication, and state management.
      |
      |## Architecture
      |This API follows Domain-Driven Design (DDD) and CQRS principles with Event Sourcing.
      |
      |## Authentication
      |Most endpoints require Bearer token authentication.
      |
      |## Error Handling
      |All endpoints return standardized error responses with proper HTTP status codes.
      """.stripMargin),
    termsOfService = Some("https://handybookshelf.com/terms"),
    contact = Some(Contact(
      name = Some("HandyBookshelf Support"),
      email = Some("support@handybookshelf.com"),
      url = Some("https://handybookshelf.com/contact")
    )),
    license = Some(License(
      name = "MIT",
      url = Some("https://opensource.org/licenses/MIT")
    ))
  )
  
  // API Servers
  private val servers = List(
    Server(
      url = "http://localhost:8080",
      description = Some("Development server")
    ),
    Server(
      url = "https://api.handybookshelf.com",
      description = Some("Production server")
    )
  )
  
  // Collect all endpoints
  private val allEndpoints: List[AnyEndpoint] = List(
    // Book endpoints
    BookEndpoints.registerBook,
    BookEndpoints.getBook,
    
    // Login endpoints  
    LoginEndpoints.loginEndpoint,
    LoginEndpoints.logoutEndpoint,
    LoginEndpoints.statusEndpoint
  )
  
  // Generate OpenAPI documentation
  val openApiDocs: OpenAPI = OpenAPIDocsInterpreter()
    .toOpenAPI(allEndpoints, apiInfo)
    .servers(servers)
    .copy(
      tags = Some(List(
        sttp.tapir.openapi.Tag(
          name = "Books",
          description = Some("Book management operations including registration and retrieval")
        ),
        sttp.tapir.openapi.Tag(
          name = "Authentication", 
          description = Some("User authentication and session management")
        ),
        sttp.tapir.openapi.Tag(
          name = "Users",
          description = Some("User state management and queries")
        )
      ))
    )
  
  // Convert to YAML string
  def openApiYaml: String = {
    import sttp.apispec.openapi.circe.yaml.*
    openApiDocs.toYaml
  }
  
  // Convert to JSON string
  def openApiJson: String = {
    import sttp.apispec.openapi.circe.*
    import io.circe.syntax.*
    openApiDocs.asJson.spaces2
  }
  
  // Endpoint to serve the OpenAPI spec
  val openApiSpecEndpoint: PublicEndpoint[Unit, Unit, String, Any] =
    endpoint
      .get
      .in("api" / "docs" / "openapi.yaml")
      .out(stringBody)
      .description("OpenAPI specification in YAML format")
      .tag("Documentation")
  
  val openApiJsonEndpoint: PublicEndpoint[Unit, Unit, String, Any] =
    endpoint
      .get
      .in("api" / "docs" / "openapi.json")
      .out(jsonBody[String])
      .description("OpenAPI specification in JSON format")
      .tag("Documentation")
}