package com.handybookshelf
package domain

import com.handybookshelf.util.{ISBN, Timestamp, ULIDConverter}
import wvlet.airframe.ulid.ULID
import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

final case class BookId private (private val value: ULID) extends Comparable[BookId]:
  override def toString: String              = value.toString
  override def compareTo(other: BookId): Int = value.compareTo(other.value)

object BookId:
  def create(
      bookCode: NES,
      timestamp: Timestamp
  ): BookId =
    BookId(ULIDConverter.createULID(bookCode, timestamp))

  def createFromISBN(
      isbn: ISBN,
      timestamp: Timestamp
  ): BookId =
    BookId(ULIDConverter.createULIDFromISBN(isbn, timestamp))

  // Circe codecs for BookId
  given Encoder[BookId]    = Encoder.encodeString.contramap(_.toString)
  given Decoder[BookId]    = Decoder.decodeString.map(str => BookId(ULID.fromString(str)))
  given KeyEncoder[BookId] = KeyEncoder.encodeKeyString.contramap(_.toString)
  given KeyDecoder[BookId] = KeyDecoder.decodeKeyString.map(str => BookId(ULID.fromString(str)))
