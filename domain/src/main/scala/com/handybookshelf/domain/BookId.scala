package com.handybookshelf
package domain

import wvlet.airframe.ulid.ULID
import cats.implicits.*

final case class BookId private (private val value: ULID)

object BookId:
  def generate(
      bookCode: String,
      timestamp: Timestamp
  ): BookId =
    BookId(ULIDConverter.generateULID(bookCode, timestamp))

  def generateFromISBN(
      isbn: ISBN,
      timestamp: Timestamp
  ): BookId =
    BookId(ULIDConverter.generateULIDFromISBN(isbn, timestamp))
