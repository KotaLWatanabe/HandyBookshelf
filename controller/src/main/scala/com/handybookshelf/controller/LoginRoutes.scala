package com.handybookshelf
package controller

import cats.effect.Async
import cats.syntax.all.*
import akka.actor.typed.ActorSystem
import akka.util.Timeout
import controller.actors.{SupervisorActor, UserAccountActorUtil}
import com.handybookshelf.util.domain.UserAccountId
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.Http4sServerInterpreter
import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext
import controller.api.endpoints.LoginEndpoints.*
import controller.api.endpoints.{LoginRequest, LoginResponse, LogoutRequest, LogoutResponse, UserStatusResponse}

class LoginRoutes[F[_]: Async](supervisorSystem: ActorSystem[SupervisorActor.SupervisorCommand]):
  
  implicit val timeout: Timeout = 3.seconds
  implicit val ec: ExecutionContext = ExecutionContext.global

  private def createUserAccountActor(userAccountId: UserAccountId): F[Unit] =
    Async[F].delay {
      val actorName = UserAccountActorUtil.generateActorName(userAccountId)
      supervisorSystem ! SupervisorActor.StartChildActor(actorName)
    }

  private def handleLogin(request: LoginRequest): F[Either[String, LoginResponse]] =
    val userAccountId = UserAccountId.create() // TODO: Use proper ID from request
    
    for {
      _ <- createUserAccountActor(userAccountId)
      response = LoginResponse(
        success = true,
        message = s"User ${request.userAccountId} logged in successfully",
        userAccountId = request.userAccountId
      )
    } yield Right(response)

  private def handleLogout(request: LogoutRequest): F[Either[String, LogoutResponse]] =
    val response = LogoutResponse(
      success = true,
      message = s"User ${request.userAccountId} logged out successfully"
    )
    Async[F].pure(Right(response))

  private def handleStatus(userAccountId: String): F[Either[String, UserStatusResponse]] =
    val response = UserStatusResponse(
      userAccountId = userAccountId,
      isLoggedIn = true // Simplified for now
    )
    Async[F].pure(Right(response))

  val routes: HttpRoutes[F] = {
    val interpreter = Http4sServerInterpreter[F]()
    interpreter.toRoutes(loginEndpoint.serverLogic(handleLogin)) <+>
    interpreter.toRoutes(logoutEndpoint.serverLogic(handleLogout)) <+>
    interpreter.toRoutes(statusEndpoint.serverLogic(handleStatus))
  }

object LoginRoutes:
  def apply[F[_]: Async](supervisorSystem: ActorSystem[SupervisorActor.SupervisorCommand]): LoginRoutes[F] =
    new LoginRoutes[F](supervisorSystem)