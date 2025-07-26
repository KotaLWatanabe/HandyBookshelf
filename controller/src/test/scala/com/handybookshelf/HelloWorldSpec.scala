package cats.effect.IO
import com.handybookshelf import controller.HandyBookshelfRoutes
import org.http4s.*
import org.http4s.implicits.*

class HelloWorldSpec extends CatsEffectSuite:

  test("HelloWorld returns status code 200") {
    assertIO(retHelloWorld.map(_.status) ,Status.Ok)
  }

  test("HelloWorld returns hello world message") {
    assertIO(retHelloWorld.flatMap(_.as[String]), "{\"message\":\"Hello, world\"}")
  }

  private[this] lazy val retHelloWorld: IO[Response[IO]] =
    val getHW = Request[IO](Method.GET, uri"/hello/world")
    val helloWorld = HelloWorld.impl[IO]
    HandyBookshelfRoutes.helloWorldRoutes(helloWorld).orNotFound(getHW)
