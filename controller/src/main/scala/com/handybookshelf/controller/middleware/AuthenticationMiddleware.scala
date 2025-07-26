package com.handybookshelf
package controller.middleware

import cats.effect.Async
import cats.syntax.all.*
import org.http4s.*
import org.http4s.server.middleware.authentication.DigestAuth
import org.http4s.headers.Authorization
import io.circe.generic.auto.*
import io.circe.syntax.*

// Authentication error types
sealed trait AuthError {
  def message: String
  def status: Status
}

object AuthError {
  case object MissingToken extends AuthError {
    val message = "Authorization token is missing"
    val status = Status.Unauthorized
  }
  
  case object InvalidToken extends AuthError {
    val message = "Authorization token is invalid"
    val status = Status.Unauthorized
  }
  
  case object ExpiredToken extends AuthError {
    val message = "Authorization token has expired"
    val status = Status.Unauthorized
  }
  
  case object InsufficientPermissions extends AuthError {
    val message = "Insufficient permissions for this resource"
    val status = Status.Forbidden
  }
}

// User context from authentication
final case class AuthenticatedUser(
    userId: String,
    roles: Set[String],
    permissions: Set[String]
)

// Authentication result
sealed trait AuthResult
case object Unauthenticated extends AuthResult
final case class Authenticated(user: AuthenticatedUser) extends AuthResult

// Token service for JWT or similar token validation
trait TokenService[F[_]] {
  def validateToken(token: String): F[Either[AuthError, AuthenticatedUser]]
  def extractToken(request: Request[F]): Option[String]
}

// Simple implementation for demonstration
class SimpleTokenService[F[_]: Async] extends TokenService[F] {
  
  // In a real implementation, this would validate JWT, check database, etc.
  def validateToken(token: String): F[Either[AuthError, AuthenticatedUser]] = {
    Async[F].delay {
      token match {
        case "valid-admin-token" => 
          Right(AuthenticatedUser(
            userId = "admin-123",
            roles = Set("admin", "user"),
            permissions = Set("read", "write", "admin")
          ))
        case "valid-user-token" => 
          Right(AuthenticatedUser(
            userId = "user-456",
            roles = Set("user"),
            permissions = Set("read", "write")
          ))
        case "expired-token" => 
          Left(AuthError.ExpiredToken)
        case _ => 
          Left(AuthError.InvalidToken)
      }
    }
  }
  
  def extractToken(request: Request[F]): Option[String] = {
    request.headers.get[Authorization].flatMap {
      case Authorization(Credentials.Token(AuthScheme.Bearer, token)) => Some(token)
      case _ => None
    }
  }
}

// Authentication middleware
object AuthenticationMiddleware {
  
  def apply[F[_]: Async](
    tokenService: TokenService[F],
    onFailure: AuthError => Response[F] = defaultAuthFailureResponse[F]
  ): AuthMiddleware[F, AuthenticatedUser] = {
    
    val authUser: Kleisli[F, Request[F], Either[String, AuthenticatedUser]] = Kleisli { request =>
      tokenService.extractToken(request) match {
        case Some(token) =>
          tokenService.validateToken(token).map {
            case Right(user) => Right(user)
            case Left(error) => Left(error.message)
          }
        case None =>
          Async[F].pure(Left(AuthError.MissingToken.message))
      }
    }
    
    AuthMiddleware(authUser, onFailure = _ => defaultAuthFailureResponse())
  }
  
  // Optional authentication (doesn't fail if no token)
  def optional[F[_]: Async](
    tokenService: TokenService[F]
  ): HttpMiddleware[F] = { routes =>
    HttpRoutes.of[F] { request =>
      tokenService.extractToken(request) match {
        case Some(token) =>
          tokenService.validateToken(token).flatMap {
            case Right(user) =>
              // Add user to request attributes for later access
              val requestWithUser = request.withAttribute(AuthenticatedUserKey, user)
              routes.run(requestWithUser)
            case Left(_) =>
              // Continue without authentication
              routes.run(request)
          }
        case None =>
          routes.run(request)
      }
    }
  }
  
  // Key for storing authenticated user in request attributes
  val AuthenticatedUserKey: Key[AuthenticatedUser] = Key.newKey[SyncIO, AuthenticatedUser].unsafeRunSync()
  
  // Default authentication failure response
  def defaultAuthFailureResponse[F[_]: Async](error: AuthError = AuthError.MissingToken): Response[F] = {
    Response[F](status = error.status)
      .withEntity(Map("error" -> error.message, "code" -> error.status.code).asJson)
  }
  
  // Extract authenticated user from request (for use in routes)
  def getAuthenticatedUser[F[_]](request: Request[F]): Option[AuthenticatedUser] = {
    request.attributes.lookup(AuthenticatedUserKey)
  }
  
  // Permission checking helper
  def hasPermission(user: AuthenticatedUser, requiredPermission: String): Boolean = {
    user.permissions.contains(requiredPermission) || user.roles.contains("admin")
  }
  
  def hasAnyRole(user: AuthenticatedUser, requiredRoles: Set[String]): Boolean = {
    user.roles.intersect(requiredRoles).nonEmpty || user.roles.contains("admin")
  }
}

// Permission-based middleware
object PermissionMiddleware {
  
  def requirePermission[F[_]: Async](
    permission: String,
    onDenied: Response[F] = Response[F](Status.Forbidden).withEntity(
      Map("error" -> "Insufficient permissions").asJson
    )
  ): HttpMiddleware[F] = { routes =>
    HttpRoutes.of[F] { request =>
      AuthenticationMiddleware.getAuthenticatedUser(request) match {
        case Some(user) if AuthenticationMiddleware.hasPermission(user, permission) =>
          routes.run(request)
        case Some(_) =>
          Async[F].pure(onDenied)
        case None =>
          Async[F].pure(AuthenticationMiddleware.defaultAuthFailureResponse(AuthError.MissingToken))
      }
    }
  }
  
  def requireRole[F[_]: Async](
    roles: Set[String],
    onDenied: Response[F] = Response[F](Status.Forbidden).withEntity(
      Map("error" -> "Insufficient role").asJson
    )
  ): HttpMiddleware[F] = { routes =>
    HttpRoutes.of[F] { request =>
      AuthenticationMiddleware.getAuthenticatedUser(request) match {
        case Some(user) if AuthenticationMiddleware.hasAnyRole(user, roles) =>
          routes.run(request)
        case Some(_) =>
          Async[F].pure(onDenied)
        case None =>
          Async[F].pure(AuthenticationMiddleware.defaultAuthFailureResponse(AuthError.MissingToken))
      }
    }
  }
}