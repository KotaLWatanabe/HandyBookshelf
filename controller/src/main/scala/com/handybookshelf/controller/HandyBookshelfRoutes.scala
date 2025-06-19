package com.handybookshelf package controller

import cats.effect.Sync
import cats.syntax.all.*
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

object HandyBookshelfRoutes:

  def jokeRoutes[F[_]: Sync](J: Jokes[F]): HttpRoutes[F] =
    val dsl = new Http4sDsl[F] {}
    import dsl.*
    HttpRoutes.of[F] { case GET -> Root / "joke" =>
      for {
        joke <- J.get
        resp <- Ok(joke)
      } yield resp
    }


