package com.handybookshelf
package domain

final case class Book private (id: BookId, title: NES)
object Book:
  def from(isbnStr: String, title: NES): Book =
    Book(BookId.generate(isbnStr, Timestamp.now()), title)

  def fromISBN(isbn: ISBN, title: NES):  Book =
    Book(BookId.generateFromISBN(isbn, Timestamp.now()), title)

final case class BookReference(book: Book, tags: Seq[Tag], devices: Seq[Device])

sealed trait Device
object Device:
  case object Pdf extends Device
  case object Kindle extends Device

final case class Tag(name: String)
