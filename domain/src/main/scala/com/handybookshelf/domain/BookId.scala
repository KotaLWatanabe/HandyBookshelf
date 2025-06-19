package com.handybookshelf package domain

import com.handybookshelf.util.{ISBN, Timestamp, ULIDConverter}
import wvlet.airframe.ulid.ULID

final case class BookId private (private val value: ULID):
  override def toString: String = value.toString

object BookId:
  def generate(
      bookCode: String,
      timestamp: Timestamp
  ): BookId =
    BookId(ULIDConverter.createULID(bookCode, timestamp))

  def generateFromISBN(
      isbn: ISBN,
      timestamp: Timestamp
  ): BookId =
    BookId(ULIDConverter.createULIDFromISBN(isbn, timestamp))
