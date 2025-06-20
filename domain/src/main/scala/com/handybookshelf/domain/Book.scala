package com.handybookshelf
package domain

import cats.data.NonEmptyList
import cats.effect.IO
import com.handybookshelf.util.{ISBN, Timestamp}

final case class Book private (id: BookId, title: NES)
object Book:
  def generate(isbnStr: String, title: NES): IO[Book] =
    Timestamp.now.map(timestamp => Book(BookId.create(isbnStr, timestamp), title))

  def generateFromISBN(isbn: ISBN, title: NES): IO[Book] =
    Timestamp.now.map(timestamp => Book(BookId.createFromISBN(isbn, timestamp), title))

final case class BookReference(book: Book, tags: Seq[Tag], devices: NonEmptyList[Device]) {
  val bookId: BookId = book.id
}

sealed trait Device
object Device:
  case object Paper extends Device
  case object Pdf   extends Device
  case object Ebook extends Device

final case class Tag(name: String)
