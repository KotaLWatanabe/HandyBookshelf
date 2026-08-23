package com.handybookshelf
package adopter

import cats.effect.IO
import cats.syntax.all.*
import com.handybookshelf.domain.BookData
import org.http4s.Uri
import org.http4s.implicits.*
import org.http4s.client.Client
import scala.util.Try
import scala.xml.*

final case class SearchError(message: String, cause: Option[Throwable] = None)
final case class BookSearchResult(
    originalData: BookData,
    enrichedData: Option[BookData] = None,
    searchSuccess: Boolean = false,
    error: Option[String] = None
)

trait BookSearchService:
  def searchAndEnrich(bookData: BookData): IO[BookSearchResult]
  def searchByTitle(title: String): IO[Either[SearchError, List[BookData]]]
  def searchByISBN(isbn: String): IO[Either[SearchError, Option[BookData]]]

class KokkaiToshokanSearchService(client: Client[IO]) extends BookSearchService:

  import org.http4s.QueryParamEncoder.stringQueryParamEncoder

  private def searchAPIURL(title: String) =
    uri"https://iss.ndl.go.jp/api/opensearch" ++? ("title", List(title))

  private val isbnTagPattern =
    """<dc:identifier xsi:type="dcndl:ISBN">(.+?)</dc:identifier>""".r

  private def isbnSubStr = (str: String) => str.substring(37, str.length - 16)

  override def searchAndEnrich(bookData: BookData): IO[BookSearchResult] =
    (for {
      searchResults <- searchByTitle(bookData.title)
      enrichedData <- searchResults match {
        case Right(results) if results.nonEmpty =>
          // 最初の結果を使用してデータをエンリッチ
          val bestMatch = results.head
          val enriched = bookData.copy(
            isbn = bookData.isbn.orElse(bestMatch.isbn),
            author = bookData.author.orElse(bestMatch.author),
            publisher = bookData.publisher.orElse(bestMatch.publisher),
            publishedYear = bookData.publishedYear.orElse(bestMatch.publishedYear)
          )
          IO.pure(
            BookSearchResult(
              originalData = bookData,
              enrichedData = Some(enriched),
              searchSuccess = true
            )
          )
        case Right(_) =>
          IO.pure(
            BookSearchResult(
              originalData = bookData,
              searchSuccess = false,
              error = Some("No search results found")
            )
          )
        case Left(error) =>
          IO.pure(
            BookSearchResult(
              originalData = bookData,
              searchSuccess = false,
              error = Some(error.message)
            )
          )
      }
    } yield enrichedData).handleErrorWith { throwable =>
      IO.pure(
        BookSearchResult(
          originalData = bookData,
          searchSuccess = false,
          error = Some(s"Search failed: ${throwable.getMessage}")
        )
      )
    }

  override def searchByTitle(title: String): IO[Either[SearchError, List[BookData]]] =
    client
      .expect[String](searchAPIURL(title))
      .map { xmlStr =>
        for {
          xml <- Try(XML.loadString(xmlStr)).toEither
            .leftMap(e => SearchError(s"XML parsing failed: ${e.getMessage}", Some(e)))
          books = xmlToBookData(xml)
        } yield books
      }
      .handleErrorWith { throwable =>
        IO.pure(Left(SearchError(s"HTTP request failed: ${throwable.getMessage}", Some(throwable))))
      }

  override def searchByISBN(isbn: String): IO[Either[SearchError, Option[BookData]]] =
    // 国会図書館APIはISBN検索もタイトル検索と同じエンドポイントを使用
    val searchUrl = uri"https://iss.ndl.go.jp/api/opensearch" ++? ("isbn", List(isbn))

    client
      .expect[String](searchUrl)
      .map { xmlStr =>
        for {
          xml <- Try(XML.loadString(xmlStr)).toEither
            .leftMap(e => SearchError(s"XML parsing failed: ${e.getMessage}", Some(e)))
          books = xmlToBookData(xml)
        } yield books.headOption
      }
      .handleErrorWith { throwable =>
        IO.pure(Left(SearchError(s"HTTP request failed: ${throwable.getMessage}", Some(throwable))))
      }

  private def xmlToBookData(elem: Elem): List[BookData] =
    (for {
      item  <- elem \\ "item"
      title <- (item \ "title").headOption.map(_.text)
    } yield {
      val isbnOpt = isbnTagPattern
        .findFirstIn(item.toString)
        .map(isbnSubStr(_))

      val author    = (item \ "creator").headOption.map(_.text)
      val publisher = (item \ "publisher").headOption.map(_.text)
      val publishedYear = (item \ "date").headOption
        .map(_.text)
        .flatMap(dateStr => Try(dateStr.take(4).toInt).toOption)

      BookData(
        isbn = isbnOpt,
        title = title,
        author = author,
        publisher = publisher,
        publishedYear = publishedYear
      )
    }).toList

// Circuit Breaker付きの検索サービス (一時的にコメントアウト)
/*
class CircuitBreakerBookSearchService(
    underlying: BookSearchService,
    circuitBreaker: CircuitBreakerClient
) extends BookSearchService:

  // Circuit breaker methods temporarily commented out
 */

object BookSearchService:
  def kokkaiToshokan(client: Client[IO]): BookSearchService =
    new KokkaiToshokanSearchService(client)

  // Circuit breaker methods temporarily commented out
  /*
  def withCircuitBreaker(
      underlying: BookSearchService,
      circuitBreaker: CircuitBreakerClient
  ): BookSearchService =
    new CircuitBreakerBookSearchService(underlying, circuitBreaker)

  def createKokkaiToshokanWithCircuitBreaker(): IO[BookSearchService] =
    for {
      client <- EmberClientBuilder.default[IO].withTimeout(30.seconds).build.allocated.map(_._1)
      circuitBreaker = CircuitBreakerClient.create()
      searchService = kokkaiToshokan(client)
    } yield withCircuitBreaker(searchService, circuitBreaker)
   */
