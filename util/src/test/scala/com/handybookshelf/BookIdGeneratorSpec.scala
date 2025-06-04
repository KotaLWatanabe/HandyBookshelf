//package com.handybookshelf
//
//import cats.effect.IO
//import munit.CatsEffectSuite
//import org.http4s.*
//import org.http4s.implicits.*
//
//class BookIdGeneratorSpec extends AnyFunSpec:
//
//  test("HelloWorld returns status code 200") {
//    assertIO(retHelloWorld.map(_.status) ,Status.Ok)
//  }
//
//  test("HelloWorld returns hello world message") {
//    assertIO(retHelloWorld.flatMap(_.as[String]), "{\"message\":\"Hello, world\"}")
//  }
//
//  private[this] lazy val retHelloWorld: IO[Response[IO]] =
//    val getHW = Request[IO](Method.GET, uri"/hello/world")
//    val helloWorld = HelloWorld.impl[IO]
//    LeastbookshelfRoutes.helloWorldRoutes(helloWorld).orNotFound(getHW)
