package com.handybookshelf
package domain

import com.handybookshelf.util.{ISBN, Timestamp, ULIDConverter}
import wvlet.airframe.ulid.ULID

final case class BookId private (private val value: ULID):
  override def toString: String = value.toString

object BookId:
  def create(
      bookCode: String,
      timestamp: Timestamp
  ): BookId =
    BookId(ULIDConverter.createULID(bookCode, timestamp))

  def createFromISBN(
      isbn: ISBN,
      timestamp: Timestamp
  ): BookId =
    BookId(ULIDConverter.createULIDFromISBN(isbn, timestamp))
