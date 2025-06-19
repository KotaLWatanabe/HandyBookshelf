package com.handybookshelf
package adopter

import cats.effect.IO
import cats.implicits.*
import ISBN.*
import domain.Book
import org.http4s.Uri
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.implicits.*

import scala.concurrent.duration.Duration
import scala.util.Try
import scala.xml.*

// https://iss.ndl.go.jp/information/api/riyou/#sec3
object KokkaiToshokanAccessor:

  import org.http4s.QueryParamEncoder.stringQueryParamEncoder
  private def searchAPIURL(title: String) =
    uri"https://iss.ndl.go.jp/api/opensearch" ++? ("title", List(title))

  private val isbnTagPattern =
    """<dc:identifier xsi:type="dcndl:ISBN">(.+?)</dc:identifier>""".r

  private def isbnSubStr = (str: String) => str.substring(37, str.length - 16)

  private val bookTitle = "Akka実践バイブル"

  private val httpClientBuilt =
    EmberClientBuilder.default[IO].withTimeout(Duration.Inf).build

  case class AdopterError(message: String, cause: Option[Throwable] = None)
  def searchBooks(
      title: String = bookTitle
  ): IO[Either[AdopterError, Set[Book]]] =
    httpClientBuilt.use { client =>
      client.expect[String](searchAPIURL(title)).map { xmlStr =>
        for {
          xml <- Try(XML.loadString(xmlStr)).toEither
            .leftMap(e => AdopterError(e.getMessage))
          books = xmlToBooks(xml).toMap.map { case (isbn, title) =>
            Book.generateFromISBN(isbn, title.nes)
          }.toSet
        } yield books
      }
    }

  private def xmlToBooks(elem: Elem): Seq[(ISBN, String)] =
    for {
      item <- elem \\ "item"
      title <- (item \ "title").headOption.map(_.text)
      isbnStr: String <- isbnTagPattern
        .findFirstIn(item.toString)
        .map(isbnSubStr)
      isbn <- isbnStr.isbnOpt
    } yield (isbn, title)
