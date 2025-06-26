package com.handybookshelf
package controller

import cats.effect.{Async, Resource}
import cats.syntax.all.*
import com.comcast.ip4s.*
import api.routes.{LoginRoutes, BookRoutes}
import fs2.io.net.Network
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.middleware.Logger
import controller.actors.SupervisorActorUtil
import com.handybookshelf.usecase.RegisterBookUseCase

object HandyBookshelfServer:
  def run[F[_]: {Async, Network}]: F[Nothing] = {
    val supervisorSystemResource = Resource.make(
      Async[F].delay { SupervisorActorUtil.createSupervisorSystem() }
    )(system => Async[F].delay { system.terminate() })

    for {
      supervisorSystem <- supervisorSystemResource

      // Create dependencies for book registration
      registerBookUseCase = RegisterBookUseCase.create()
      
      loginRoutes = LoginRoutes[F](supervisorSystem)
      bookRoutes = BookRoutes[F](registerBookUseCase)

      // Combine Service Routes into an HttpApp.
      // Can also be done via a Router if you
      // want to extract segments not checked
      // in the underlying routes.
      httpApp = (
        loginRoutes.routes <+> bookRoutes.routes
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
