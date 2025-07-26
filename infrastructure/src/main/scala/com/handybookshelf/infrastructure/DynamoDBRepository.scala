package com.handybookshelf
package infrastructure

import cats.effect.IO
import cats.syntax.all.*
import software.amazon.awssdk.services.dynamodb.DynamoDbClient as AwsDynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*

import scala.jdk.CollectionConverters.*
import io.circe.*
import io.circe.syntax.*
import domain.{Book, BookId}

trait DynamoDBRepository[F[_]] {
  def put(key: String, value: Json): F[Unit]
  def get(key: String): F[Option[Json]]
  def delete(key: String): F[Unit]
  def scan(): F[List[(String, Json)]]
}

class DynamoDBRepositoryImpl(
  client: AwsDynamoDbClient,
  tableName: String = "HandyBookshelf"
) extends DynamoDBRepository[IO] {

  def createTableIfNotExists(): IO[Unit] = 
    IO.delay {
      try {
        client.describeTable(DescribeTableRequest.builder().tableName(tableName).build())
      } catch {
        case _: ResourceNotFoundException =>
          val request = CreateTableRequest.builder()
            .tableName(tableName)
            .keySchema(
              KeySchemaElement.builder()
                .attributeName("id")
                .keyType(KeyType.HASH)
                .build()
            )
            .attributeDefinitions(
              AttributeDefinition.builder()
                .attributeName("id")
                .attributeType(ScalarAttributeType.S)
                .build()
            )
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .build()
          
          client.createTable(request)
      }
    }.void

  def put(key: String, value: Json): IO[Unit] = 
    IO.delay {
      val item = Map(
        "id" -> AttributeValue.builder().s(key).build(),
        "data" -> AttributeValue.builder().s(value.noSpaces).build()
      ).asJava

      val request = PutItemRequest.builder()
        .tableName(tableName)
        .item(item)
        .build()

      client.putItem(request)
    }.void

  def get(key: String): IO[Option[Json]] = 
    IO.delay {
      val keyMap = Map(
        "id" -> AttributeValue.builder().s(key).build()
      ).asJava

      val request = GetItemRequest.builder()
        .tableName(tableName)
        .key(keyMap)
        .build()

      val response = client.getItem(request)
      
      if (response.hasItem) {
        val dataAttr = response.item().get("data")
        if (dataAttr != null) {
          parser.parse(dataAttr.s()).toOption
        } else None
      } else None
    }

  def delete(key: String): IO[Unit] = 
    IO.delay {
      val keyMap = Map(
        "id" -> AttributeValue.builder().s(key).build()
      ).asJava

      val request = DeleteItemRequest.builder()
        .tableName(tableName)
        .key(keyMap)
        .build()

      client.deleteItem(request)
    }.void

  def scan(): IO[List[(String, Json)]] = 
    IO.delay {
      val request = ScanRequest.builder()
        .tableName(tableName)
        .build()

      val response = client.scan(request)
      
      response.items().asScala.toList.flatMap { item =>
        val id = Option(item.get("id")).map(_.s())
        val data = Option(item.get("data")).flatMap(attr => parser.parse(attr.s()).toOption)
        
        (id, data).tupled
      }
    }
}

// Book specific repository extending the generic one
trait BookDynamoDBRepository[F[_]] {
  def saveBook(book: Book): F[Unit]
  def getBook(bookId: BookId): F[Option[Book]]
  def deleteBook(bookId: BookId): F[Unit]
  def listBooks(): F[List[Book]]
}

class BookDynamoDBRepositoryImpl(
  underlying: DynamoDBRepository[IO]
)(using encoder: Codec[Book]) extends BookDynamoDBRepository[IO] {

  def saveBook(book: Book): IO[Unit] = 
    underlying.put(book.id.toString, book.asJson)

  def getBook(bookId: BookId): IO[Option[Book]] = 
    underlying.get(bookId.toString).map(_.flatMap(_.as[Book].toOption))

  def deleteBook(bookId: BookId): IO[Unit] = 
    underlying.delete(bookId.toString)

  def listBooks(): IO[List[Book]] = 
    underlying.scan().map(_.flatMap { case (_, json) =>
      json.as[Book].toOption
    })
}