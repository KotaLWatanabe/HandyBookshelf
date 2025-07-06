package com.handybookshelf
package controller

import cats.effect.{Async, Resource}
import cats.syntax.all.*
import com.comcast.ip4s.*
import api.routes.{BookRoutes, LoginRoutes, UserStateRoutes}
import fs2.io.net.Network
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.middleware.Logger
import com.handybookshelf.infrastructure.actors.{SupervisorActor, SupervisorActorUtil}
import com.handybookshelf.infrastructure.{ActorBasedUserStateRepository, AsyncTaskService, ExecutionContexts, InMemoryEventStore, SessionService, UserStateRepository}
import com.handybookshelf.usecase.{RegisterBookUseCase, UserCommandUseCase, UserQueryUseCase}
import org.apache.pekko.actor.typed.ActorSystem

object HandyBookshelfServer:
  def run[F[_]: {Async, Network}]: F[Nothing] = {
    val sessionServiceResource = Resource.eval(
      Async[F].delay {
        val eventStore = new InMemoryEventStore()
        SessionService.create(eventStore).unsafeRunSync()
      }
    )
    
    val supervisorSystemResource: Resource[F, ActorSystem[SupervisorActor.SupervisorCommand]] = sessionServiceResource.flatMap { sessionService =>
      Resource.make(
        Async[F].delay { SupervisorActorUtil.createSupervisorSystem(sessionService) }
      )(system => Async[F].delay { system.terminate() })
    }

    def executionContextsResource: Resource[F, ExecutionContexts.ExecutionContextBundle] =
      ExecutionContexts.createExecutionContextBundle

    def userStateRepositoryResource
    (using actorSystem: ActorSystem[SupervisorActor.SupervisorCommand]): Resource[F, UserStateRepository] =
      Resource.eval(ActorBasedUserStateRepository.create())

    for {
      // Create execution context bundle
      executionContexts <-executionContextsResource
      given ExecutionContexts.ExecutionContextBundle = executionContexts
      
      supervisorSystem <- supervisorSystemResource

      // Create user state repository using actor system
      userStateRepository <- userStateRepositoryResource(supervisorSystem)

      // Create async task service
      asyncTaskService = AsyncTaskService.create
      
      // Create dependencies for book registration
      registerBookUseCase = RegisterBookUseCase.create()
      
      // Create user state use cases
      userCommandUseCase = UserCommandUseCase.create(userStateRepository)
      userQueryUseCase = UserQueryUseCase.create(userStateRepository)
      
      loginRoutes = LoginRoutes[F](supervisorSystem)
      bookRoutes = BookRoutes[F](registerBookUseCase)
      userStateRoutes = UserStateRoutes[F](userCommandUseCase, userQueryUseCase)

      // Combine Service Routes into an HttpApp.
      // Can also be done via a Router if you
      // want to extract segments not checked
      // in the underlying routes.
      httpApp = (
        loginRoutes.routes <+> bookRoutes.routes <+> userStateRoutes.routes
      ).orNotFound

      // With Middlewares in place
      finalHttpApp = Logger.httpApp(logHeaders = true, logBody = true)(httpApp)

      _ <-
        EmberServerBuilder
          .default[F]
          .withHost(ipv4"0.0.0.0")
          .withPort(port"8080")
          .withHttpApp(finalHttpApp)
          .build
    } yield ()
  }.useForever
