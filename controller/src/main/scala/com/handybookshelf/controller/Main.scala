package com.handybookshelf 
package controller

import cats.effect.*
import com.comcast.ip4s.*
import api.routes.*
import actors.SupervisorActor
import org.http4s.server.Router
import org.http4s.ember.server.EmberServerBuilder

object Main extends IOApp:
  override def run(args: List[String]): IO[ExitCode] = {
    // Create supervisor system
    val supervisorSystem = actors.SupervisorActorUtil.createSupervisorSystem()
    
    // 全ルートを統合
    val httpApp = Router(
      "/" -> LoginRoutes[IO](supervisorSystem).routes
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
