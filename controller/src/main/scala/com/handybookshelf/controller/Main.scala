package com.handybookshelf.controller

import cats.effect.*
import com.comcast.ip4s.*
import com.handybookshelf.controller.api.routes.*
import org.http4s.server.Router
import org.http4s.ember.server.EmberServerBuilder

object Main extends IOApp:
  override def run(args: List[String]): IO[ExitCode] = {
    // 全ルートを統合
    val httpApp = Router(
      "/" -> UserRoutes.routes, // APIルートを登録
      "/docs" -> SwaggerRoutes.swaggerRoutes
    ).orNotFound

    // Emberサーバの起動
    EmberServerBuilder
      .default[IO]
      .withHost(host"localhost")
      .withPort(port"8080")
      .withHttpApp(httpApp)
      .build
      .use(_ => IO.never) // サーバが終了するまで待機
      .as(ExitCode.Success)
  }.debug("result: ")
