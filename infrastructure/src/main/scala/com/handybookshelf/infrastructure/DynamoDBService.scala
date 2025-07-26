package com.handybookshelf.infrastructure

import cats.effect.{IO, Resource}
import com.handybookshelf.domain.{Book, BookId}
import io.circe.Codec

import java.net.URI

class DynamoDBService private (
  bookRepository: BookDynamoDBRepository[IO]
) {
  
  def saveBook(book: Book): IO[Unit] = 
    bookRepository.saveBook(book)
  
  def getBook(bookId: BookId): IO[Option[Book]] = 
    bookRepository.getBook(bookId)
  
  def deleteBook(bookId: BookId): IO[Unit] = 
    bookRepository.deleteBook(bookId)
  
  def listBooks(): IO[List[Book]] = 
    bookRepository.listBooks()
}

object DynamoDBService {
  
  def createLocal(tableName: String = "HandyBookshelf")(using Codec[Book]): Resource[IO, DynamoDBService] = 
    for {
      client <- DynamoDBClient.createLocalClient()
      repository = new DynamoDBRepositoryImpl(client, tableName)
      _ <- Resource.eval(repository.createTableIfNotExists())
      bookRepository = new BookDynamoDBRepositoryImpl(repository)
    } yield new DynamoDBService(bookRepository)
  
  def create(endpoint: URI, tableName: String = "HandyBookshelf")(using Codec[Book]): Resource[IO, DynamoDBService] = 
    for {
      client <- DynamoDBClient.createClient(endpoint)
      repository = new DynamoDBRepositoryImpl(client, tableName)
      _ <- Resource.eval(repository.createTableIfNotExists())
      bookRepository = new BookDynamoDBRepositoryImpl(repository)
    } yield new DynamoDBService(bookRepository)
}