package com.handybookshelf
package infrastructure

import cats.effect.IO
import cats.syntax.all.*
import com.handybookshelf.domain.UserAccountId
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import io.circe.{Codec, Decoder, Encoder, Json}
import org.http4s.*
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.headers.`Content-Type`

import java.time.Instant

/**
 * Elasticsearch client for CQRS read model operations Handles book search, indexing, and query operations
 */
class ElasticsearchClient(client: Client[IO], baseUri: Uri):
  import ElasticsearchClient.*

  private val booksIndex    = "handybookshelf-books"
  private val eventsIndex   = "handybookshelf-events"
  private val sessionsIndex = "handybookshelf-sessions"

  /**
   * Index a book document
   */
  def indexBook(bookDoc: BookDocument): IO[Unit] =
    val uri = baseUri / booksIndex / "_doc" / bookDoc.bookId
    val request = Request[IO](
      method = Method.PUT,
      uri = uri,
      headers = Headers(`Content-Type`(org.http4s.MediaType.application.json))
    ).withEntity(bookDoc.asJson)

    client.expect[Json](request).void.handleErrorWith { error =>
      IO.println(s"Failed to index book ${bookDoc.bookId}: ${error.getMessage}")
    }

  /**
   * Search books by title with Japanese text analysis
   */
  def searchBooksByTitle(
      userAccountId: String,
      title: String,
      size: Int = 10,
      from: Int = 0
  ): IO[List[BookDocument]] =
    val searchQuery = Json.obj(
      "query" -> Json.obj(
        "bool" -> Json.obj(
          "must" -> Json.arr(
            Json.obj(
              "term" -> Json.obj(
                "userAccountId" -> Json.fromString(userAccountId)
              )
            ),
            Json.obj(
              "match" -> Json.obj(
                "title" -> Json.obj(
                  "query"    -> Json.fromString(title),
                  "analyzer" -> Json.fromString("japanese_book_analyzer")
                )
              )
            )
          )
        )
      ),
      "size" -> Json.fromInt(size),
      "from" -> Json.fromInt(from),
      "sort" -> Json.arr(
        Json.obj("addedAt" -> Json.obj("order" -> Json.fromString("desc")))
      )
    )

    executeSearch[BookDocument](booksIndex, searchQuery)

  /**
   * Search books by tags
   */
  def searchBooksByTags(
      userAccountId: String,
      tags: List[String],
      size: Int = 10
  ): IO[List[BookDocument]] =
    val searchQuery = Json.obj(
      "query" -> Json.obj(
        "bool" -> Json.obj(
          "must" -> Json.arr(
            Json.obj(
              "term" -> Json.obj(
                "userAccountId" -> Json.fromString(userAccountId)
              )
            ),
            Json.obj(
              "terms" -> Json.obj(
                "tags" -> Json.fromValues(tags.map(Json.fromString))
              )
            )
          )
        )
      ),
      "size" -> Json.fromInt(size)
    )

    executeSearch[BookDocument](booksIndex, searchQuery)

  /**
   * Get all books for a user with pagination
   */
  def getAllUserBooks(
      userAccountId: String,
      size: Int = 50,
      from: Int = 0,
      sortBy: String = "addedAt"
  ): IO[List[BookDocument]] =
    val searchQuery = Json.obj(
      "query" -> Json.obj(
        "term" -> Json.obj(
          "userAccountId" -> Json.fromString(userAccountId)
        )
      ),
      "size" -> Json.fromInt(size),
      "from" -> Json.fromInt(from),
      "sort" -> Json.arr(
        Json.obj(sortBy -> Json.obj("order" -> Json.fromString("desc")))
      )
    )

    executeSearch[BookDocument](booksIndex, searchQuery)

  /**
   * Delete a book document
   */
  def deleteBook(userAccountId: String, bookId: String): IO[Unit] =
    val uri     = baseUri / booksIndex / "_doc" / userAccountId / bookId
    val request = Request[IO](method = Method.DELETE, uri = uri)

    client.expect[Json](request).void.handleErrorWith { error =>
      IO.println(s"Failed to delete book $bookId: ${error.getMessage}")
    }

  /**
   * Index an event for audit trail
   */
  def indexEvent(
      persistenceId: String,
      sequenceNr: Long,
      eventType: String,
      eventData: Json,
      userAccountId: String,
      sessionId: Option[String] = None
  ): IO[Unit] =
    val eventDoc = Json.obj(
      "persistenceId" -> Json.fromString(persistenceId),
      "sequenceNr"    -> Json.fromLong(sequenceNr),
      "eventType"     -> Json.fromString(eventType),
      "eventData"     -> eventData,
      "userAccountId" -> Json.fromString(userAccountId),
      "sessionId"     -> sessionId.fold(Json.Null)(Json.fromString),
      "timestamp"     -> Json.fromString(Instant.now().toString)
    )

    val uri = baseUri / eventsIndex / "_doc"
    val request = Request[IO](
      method = Method.POST,
      uri = uri,
      headers = Headers(`Content-Type`(org.http4s.MediaType.application.json))
    ).withEntity(eventDoc)

    client.expect[Json](request).void.handleErrorWith { error =>
      IO.println(s"Failed to index event: ${error.getMessage}")
    }

  /**
   * Index session information
   */
  def indexSession(
      userAccountId: String,
      sessionId: String,
      createdAt: Instant,
      expiresAt: Instant,
      ipAddress: Option[String] = None,
      userAgent: Option[String] = None
  ): IO[Unit] =
    val sessionDoc = Json.obj(
      "userAccountId" -> Json.fromString(userAccountId),
      "sessionId"     -> Json.fromString(sessionId),
      "createdAt"     -> Json.fromString(createdAt.toString),
      "lastActivity"  -> Json.fromString(createdAt.toString),
      "expiresAt"     -> Json.fromString(expiresAt.toString),
      "isActive"      -> Json.True,
      "ipAddress"     -> ipAddress.fold(Json.Null)(Json.fromString),
      "userAgent"     -> userAgent.fold(Json.Null)(Json.fromString)
    )

    val uri = baseUri / sessionsIndex / "_doc" / sessionId
    val request = Request[IO](
      method = Method.PUT,
      uri = uri,
      headers = Headers(`Content-Type`(org.http4s.MediaType.application.json))
    ).withEntity(sessionDoc)

    client.expect[Json](request).void

  /**
   * Aggregated search across books and events
   */
  def searchUserActivity(
      userAccountId: String,
      query: String,
      from: Instant,
      to: Instant
  ): IO[Json] =
    val searchQuery = Json.obj(
      "query" -> Json.obj(
        "bool" -> Json.obj(
          "must" -> Json.arr(
            Json.obj(
              "term" -> Json.obj(
                "userAccountId" -> Json.fromString(userAccountId)
              )
            ),
            Json.obj(
              "range" -> Json.obj(
                "timestamp" -> Json.obj(
                  "gte" -> Json.fromString(from.toString),
                  "lte" -> Json.fromString(to.toString)
                )
              )
            ),
            Json.obj(
              "multi_match" -> Json.obj(
                "query" -> Json.fromString(query),
                "fields" -> Json.arr(
                  Json.fromString("eventType"),
                  Json.fromString("eventData")
                )
              )
            )
          )
        )
      ),
      "sort" -> Json.arr(
        Json.obj("timestamp" -> Json.obj("order" -> Json.fromString("desc")))
      )
    )

    val uri = baseUri / eventsIndex / "_search"
    val request = Request[IO](
      method = Method.POST,
      uri = uri,
      headers = Headers(`Content-Type`(org.http4s.MediaType.application.json))
    ).withEntity(searchQuery)

    client.expect[Json](request)

  /**
   * Generic search execution
   */
  private def executeSearch[T: Decoder](index: String, query: Json): IO[List[T]] =
    val uri = baseUri / index / "_search"
    val request = Request[IO](
      method = Method.POST,
      uri = uri,
      headers = Headers(`Content-Type`(org.http4s.MediaType.application.json))
    ).withEntity(query)

    client.expect[Json](request).flatMap { responseJson =>
      responseJson.hcursor.downField("hits").downField("hits").as[List[Json]] match {
        case Right(hitJsons) =>
          hitJsons.traverse { hitJson =>
            hitJson.hcursor.downField("_source").as[T] match {
              case Right(doc)  => IO.pure(doc)
              case Left(error) => IO.raiseError(new RuntimeException(s"Failed to decode document: $error"))
            }
          }
        case Left(error) =>
          IO.raiseError(new RuntimeException(s"Failed to parse search response: $error"))
      }
    }

  /**
   * Health check
   */
  def healthCheck: IO[Boolean] =
    val uri     = baseUri / "_cluster" / "health"
    val request = Request[IO](method = Method.GET, uri = uri)

    client
      .expect[Json](request)
      .map { response =>
        response.hcursor.downField("status").as[String] match {
          case Right("green" | "yellow") => true
          case _                         => false
        }
      }
      .handleErrorWith(_ => IO.pure(false))

/**
 * Elasticsearch client factory
 */
object ElasticsearchClient:
  /**
   * Book document for Elasticsearch indexing
   */
  case class BookDocument(
      bookId: String,
      userAccountId: String,
      title: String,
      isbn: Option[String],
      authors: List[String],
      tags: List[String],
      location: Option[String],
      status: String,
      addedAt: Instant,
      updatedAt: Instant
  )

  /**
   * Search result wrapper
   */
  case class SearchResult(
      hits: SearchHits,
      took: Int,
      timedOut: Boolean
  )

  case class SearchHits(
      total: SearchTotal,
      hits: List[SearchHit]
  )

  case class SearchTotal(value: Long, relation: String)

  case class SearchHit(
      id: String,
      source: Json,
      score: Option[Double]
  )

  def create(client: Client[IO], baseUrl: String = "http://localhost:9200")
  //          (using ExecutionContext)
      : IO[ElasticsearchClient] =
    Uri.fromString(baseUrl) match {
      case Right(uri)  => IO.pure(new ElasticsearchClient(client, uri))
      case Left(error) => IO.raiseError(new IllegalArgumentException(s"Invalid Elasticsearch URL: $error"))
    }

  def createFromEnv(client: Client[IO])
  //                 (using ExecutionContext)
      : IO[ElasticsearchClient] =
    val baseUrl = sys.env.getOrElse("ELASTICSEARCH_HOSTS", "http://localhost:9200")
    create(client, baseUrl)

  /**
   * Given instances for JSON codecs
   */
  given Codec[BookDocument] = deriveCodec
  given Codec[SearchResult] = deriveCodec
  given Codec[SearchHit]    = deriveCodec
  given Codec[SearchHits]   = deriveCodec
  given Codec[SearchTotal]  = deriveCodec

/**
 * Book projection service for CQRS read model updates
 */
class BookProjectionService(esClient: ElasticsearchClient):
  import ElasticsearchClient.*

  /**
   * Handle BookAdded event
   */
  def handleBookAdded(
      userAccountId: UserAccountId,
//      bookId: BookId,
//      bookReference: BookReference,
//      filters: Filters,
      timestamp: Instant
  ): IO[Unit] =
    val bookDoc = BookDocument(
      bookId = ???,
      userAccountId = userAccountId.breachEncapsulationIdAsString,
      title = ???,
      isbn = ???,
      authors = ???,
      tags = ???,
      location = ???,
      status = "available",
      addedAt = timestamp,
      updatedAt = timestamp
    )

    esClient.indexBook(bookDoc)

  /**
   * Handle BookRemoved event
   */
  def handleBookRemoved(
//      userAccountId: UserAccountId,
//      bookId: BookId
  ): IO[Unit] =
    esClient.deleteBook(userAccountId = ???, bookId = ???)
