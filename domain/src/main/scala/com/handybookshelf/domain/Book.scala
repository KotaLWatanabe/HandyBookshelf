package com.handybookshelf
package domain

import cats.data.NonEmptyList
import cats.kernel.Eq
import org.atnos.eff.Eff
import org.atnos.eff.all._eval
import util.{TimestampGenerator, ISBN}
import ISBN.*
import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax.*

final case class Book private (id: BookId, title: NES, identifier: Option[BookIdentifier]) {
  def idCompare(other: Book): Int    = id.compareTo(other.id)
  def titleCompare(other: Book): Int = title.compareTo(other.title)

  /** 後方互換性: ISBNを取得（ISBN識別子の場合のみ） */
  def isbn: Option[ISBN] = identifier.collect { case BookIdentifier.ISBN(isbn) => isbn }
}
object Book:
  // privateなコンストラクタのためのapplyメソッド
  def apply(id: BookId, title: NES, identifier: Option[BookIdentifier] = None): Book =
    new Book(id, title, identifier)

  def generate[R: _eval](isbnStr: NES, title: NES): Eff[R, Book] =
    TimestampGenerator.now.map { timestamp =>
      val identifier = isbnStr.isbnOpt.map(BookIdentifier.ISBN(_))
      Book(BookId.create(isbnStr, timestamp), title, identifier)
    }

  def generateFromISBN[R: _eval](isbn: ISBN, title: NES): Eff[R, Book] =
    TimestampGenerator.now.map(timestamp =>
      Book(BookId.createFromISBN(isbn, timestamp), title, Some(BookIdentifier.ISBN(isbn)))
    )

  def generateWithIdentifier[R: _eval](identifier: BookIdentifier, title: NES): Eff[R, Book] =
    TimestampGenerator.now.map(timestamp => Book(BookId.generate(timestamp), title, Some(identifier)))

final case class BookReference(book: Book, tags: Seq[Tag], devices: NonEmptyList[Device]) {
  val bookId: BookId = book.id
}

enum Device:
  case Paper, Pdf, Ebook

object Device:
  // Eq instance for Device
  given Eq[Device] = Eq.fromUniversalEquals

  // Circe codecs for Device
  given Encoder[Device] = Encoder.encodeString.contramap(_.toString)
  given Decoder[Device] = Decoder.decodeString.emap: s =>
    scala.util.Try(Device.valueOf(s)).toEither.left.map(_ => s"Unknown device type: $s")

final case class Tag(name: NES)

// Circe codecs for domain objects

// Book - manual codec due to private constructor
given Encoder[Book] = Encoder.instance { book =>
  Json.obj(
    "id"         -> book.id.asJson,
    "title"      -> book.title.asJson,
    "identifier" -> book.identifier.asJson
  )
}
given Decoder[Book] = Decoder.instance { cursor =>
  for
    id         <- cursor.downField("id").as[BookId]
    title      <- cursor.downField("title").as[NES]
    identifier <- cursor.downField("identifier").as[Option[BookIdentifier]]
  yield Book(id, title, identifier)
}

given Encoder[Tag] = Encoder.instance { tag =>
  Json.obj("name" -> tag.name.asJson)
}
given Decoder[Tag] = Decoder.instance { cursor =>
  cursor.downField("name").as[NES].map(Tag.apply)
}

given Encoder[BookReference] = Encoder.instance { ref =>
  Json.obj(
    "book"    -> ref.book.asJson,
    "tags"    -> ref.tags.asJson,
    "devices" -> ref.devices.asJson
  )
}
given Decoder[BookReference] = Decoder.instance { cursor =>
  for
    book    <- cursor.downField("book").as[Book]
    tags    <- cursor.downField("tags").as[Seq[Tag]]
    devices <- cursor.downField("devices").as[NonEmptyList[Device]]
  yield BookReference(book, tags, devices)
}
