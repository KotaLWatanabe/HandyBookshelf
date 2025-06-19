package com.handybookshelf
package controller

import cats.effect.{Async, Resource}
import cats.syntax.all.*
import com.comcast.ip4s.*

import fs2.io.net.Network
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.middleware.Logger

import controller.actors.SupervisorActorUtil

object HandyBookshelfServer:
  def run[F[_]: {Async, Network}]: F[Nothing] = {
    val supervisorSystemResource = Resource.make(
      Async[F].delay {
        SupervisorActorUtil.createSupervisorSystem()
      }
    )(system => 
      Async[F].delay {
        system.terminate()
        ()
      }
    )

    for {
      supervisorSystem <- supervisorSystemResource
      client <- EmberClientBuilder.default[F].build
      jokeAlg = Jokes.impl[F](client)
      
      loginRoutes = LoginRoutes[F](supervisorSystem)

      // Combine Service Routes into an HttpApp.
      // Can also be done via a Router if you
      // want to extract segments not checked
      // in the underlying routes.
      httpApp = (
     //   LeastbookshelfRoutes.helloWorldRoutes[F](helloWorldAlg) <+>
          HandyBookshelfRoutes.jokeRoutes[F](jokeAlg) <+>
          loginRoutes.routes
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
