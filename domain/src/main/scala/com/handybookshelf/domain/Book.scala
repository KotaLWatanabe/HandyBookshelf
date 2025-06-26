package com.handybookshelf
package domain

import cats.data.NonEmptyList
import cats.kernel.Eq
import com.handybookshelf.util.CurrentDateTimeGenerator._current
import org.atnos.eff.Eff
import util.{CurrentDateTimeGenerator, ISBN}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

final case class Book private (id: BookId, title: NES) {
  def idCompare(other: Book): Int    = id.compareTo(other.id)
  def titleCompare(other: Book): Int = title.compareTo(other.title)
}
object Book:
  def generate[R: _current](isbnStr: NES, title: NES): Eff[R, Book] =
    CurrentDateTimeGenerator.now.map(timestamp => Book(BookId.create(isbnStr, timestamp), title))

  def generateFromISBN[R: _current](isbn: ISBN, title: NES): Eff[R, Book] =
    CurrentDateTimeGenerator.now.map(timestamp => Book(BookId.createFromISBN(isbn, timestamp), title))

final case class BookReference(book: Book, tags: Seq[Tag], devices: NonEmptyList[Device]) {
  val bookId: BookId = book.id
}

sealed trait Device
object Device:
  case object Paper extends Device
  case object Pdf   extends Device
  case object Ebook extends Device

  // Eq instance for Device
  given Eq[Device] = Eq.fromUniversalEquals

  // Circe codecs for Device
  given Encoder[Device] = deriveEncoder
  given Decoder[Device] = deriveDecoder

final case class Tag(name: NES)

// Circe codecs for other domain objects
given Encoder[Book]          = deriveEncoder
given Decoder[Book]          = deriveDecoder
given Encoder[BookReference] = deriveEncoder
given Decoder[BookReference] = deriveDecoder
given Encoder[Tag]           = deriveEncoder
given Decoder[Tag]           = deriveDecoder
