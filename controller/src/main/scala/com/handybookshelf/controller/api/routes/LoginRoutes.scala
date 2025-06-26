package com.handybookshelf
package controller
package api
package routes

import cats.effect.Async
import cats.syntax.all.*
import cats.effect.kernel.MonadCancelThrow
import com.handybookshelf.controller.api.endpoints.LoginEndpoints.{loginEndpoint, logoutEndpoint, statusEndpoint}
import com.handybookshelf.controller.api.endpoints.*
import com.handybookshelf.domain.UserAccountId
import com.handybookshelf.infrastructure.{SessionService, LoginResult, LogoutResult, UserStatusResult}
import com.handybookshelf.util.{IDGenerator, ULIDGen}
import com.handybookshelf.util.IDGenerator._idgen
import org.atnos.eff.*
import org.atnos.eff.interpret.*
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

class LoginRoutes[F[_]: Async](sessionService: SessionService):

  // Effect stack with ULIDGen
  type EffStack = Fx.fx1[ULIDGen]

  import org.atnos.eff.all.*
  import wvlet.airframe.ulid.ULID

  // ULIDGen effect runner
  private def runULIDGen[A](effect: Eff[EffStack, A]): A = {
    interpret.translate(effect)(new Translate[ULIDGen, NoFx] {
      def apply[X](ulidGen: ULIDGen[X]): Eff[NoFx, X] = 
        Eff.pure(ulidGen())
    }).runPure.get
  }

  private def handleLogin(request: LoginRequest): F[Either[String, LoginResponse]] =
    // Parse userAccountId from request - in real implementation this would be validated
    val userIdGeneration: Eff[EffStack, UserAccountId] = 
      UserAccountId.generate[EffStack]()
    
    val userId = runULIDGen(userIdGeneration)
    
    for {
      // Use the session service directly
      response <- MonadCancelThrow[F].fromFuture(MonadCancelThrow[F].pure(
        sessionService.login(userId).unsafeToFuture()
      ))
      
      result = if response.success then
        Right(LoginResponse(
          success = true,
          message = response.message,
          userAccountId = request.userAccountId
        ))
      else
        Left(response.message)
    } yield result

  private def handleLogout(request: LogoutRequest): F[Either[String, LogoutResponse]] =
    // Parse userAccountId from request - in real implementation this would come from session/token
    val userIdGeneration: Eff[EffStack, UserAccountId] = 
      UserAccountId.generate[EffStack]()
    
    val userId = runULIDGen(userIdGeneration)
    
    for {
      response <- MonadCancelThrow[F].fromFuture(MonadCancelThrow[F].pure(
        sessionService.logout(userId).unsafeToFuture()
      ))
      
      result = if response.success then
        Right(LogoutResponse(
          success = true,
          message = response.message
        ))
      else
        Left(response.message)
    } yield result

  private def handleStatus(userAccountId: String): F[Either[String, UserStatusResponse]] =
    // Parse userAccountId from string - in real implementation this would be proper parsing
    val userIdGeneration: Eff[EffStack, UserAccountId] = 
      UserAccountId.generate[EffStack]()
    
    val userId = runULIDGen(userIdGeneration)
    
    for {
      response <- MonadCancelThrow[F].fromFuture(MonadCancelThrow[F].pure(
        sessionService.getUserStatus(userId).unsafeToFuture()
      ))
      
      result = Right(UserStatusResponse(
        userAccountId = userAccountId,
        isLoggedIn = response.isLoggedIn
      ))
    } yield result

  val routes: HttpRoutes[F] = {
    val interpreter = Http4sServerInterpreter[F]()
    interpreter.toRoutes(loginEndpoint.serverLogic(handleLogin)) <+>
      interpreter.toRoutes(logoutEndpoint.serverLogic(handleLogout)) <+>
      interpreter.toRoutes(statusEndpoint.serverLogic(handleStatus))
  }

object LoginRoutes:
  def apply[F[_]: Async](sessionService: SessionService): LoginRoutes[F] =
    new LoginRoutes[F](sessionService)