package com.handybookshelf
package domain

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import cats.syntax.all.*

sealed trait BookSorter:
  def compare: (BookReference, BookReference) => Int
  def sortAsc: (BookReference, BookReference) => Boolean = compare(_, _) < 0

// Concrete sorter implementations
case object TitleSorter extends BookSorter:
  def compare: (BookReference, BookReference) => Int = 
    (a, b) => a.book.title.toString.compareTo(b.book.title.toString)

case object DateSorter extends BookSorter:
  def compare: (BookReference, BookReference) => Int = 
    (a, b) => a.book.id.toString.compareTo(b.book.id.toString)

sealed trait Filter:
  def predicate: BookReference => Boolean

// Concrete filter implementations
final case class TitleFilter(title: String) extends Filter:
  def predicate: BookReference => Boolean = 
    br => br.book.title.toString.contains(title)

final case class TagFilter(tag: String) extends Filter:
  def predicate: BookReference => Boolean = 
    br => br.tags.exists(_.name.toString.contains(tag))

final case class DeviceFilter(device: Device) extends Filter:
  def predicate: BookReference => Boolean = 
    br => br.devices.contains_(device)

final case class Filters(filters: Set[Filter]):
  def predicates: BookReference => Boolean  = br => filters.forall(_.predicate(br))
  def addFilter(filter: Filter): Filters    = Filters(filters + filter)
  def removeFilter(filter: Filter): Filters = Filters(filters - filter)

final case class Bookshelf(
    private val _books: Map[BookId, (Filters, BookReference)],
    private val _sorter: BookSorter
):
  // Public accessors for persistence
  def books: Map[BookId, (Filters, BookReference)] = _books
  def sorter: BookSorter = _sorter
  
  private val filteredBooks: IndexedSeq[BookReference] =
    _books.toIndexedSeq.filter { case (_, (filters, ref)) => filters.predicates(ref) }.map(_._2._2)

  val viewBooks: IndexedSeq[BookReference] = filteredBooks.sortWith(_sorter.sortAsc(_, _))

  def changeSorter(newSorter: BookSorter): Bookshelf = Bookshelf(_books, newSorter)
  def addBook(book: BookReference, filters: Filters, bookId: BookId): Bookshelf =
    Bookshelf(_books.updated(bookId, (filters, book)), _sorter)
  def addFilter(newFilter: Filter, bookId: BookId): Bookshelf =
    updateBookshelf(bookId, (filters, bookRef) => (filters.addFilter(newFilter), bookRef))
  def removeFilter(newFilter: Filter, bookId: BookId): Bookshelf =
    updateBookshelf(bookId, (filters, bookRef) => (filters.removeFilter(newFilter), bookRef))

  private def updateBookshelf(
      bookId: BookId,
      updateFunc: (Filters, BookReference) => (Filters, BookReference)
  ): Bookshelf = Bookshelf(
    _books.updatedWith(bookId)(_.map(updateFunc(_, _))),
    _sorter
  )

// Circe codecs for JSON serialization
given Encoder[Filter] = deriveEncoder
given Decoder[Filter] = deriveDecoder
given Encoder[Filters] = deriveEncoder
given Decoder[Filters] = deriveDecoder

// BookSorter and other codecs
given Encoder[BookSorter] = deriveEncoder
given Decoder[BookSorter] = deriveDecoder

// Manual Bookshelf codecs to handle Map serialization
given Encoder[Bookshelf] = Encoder.instance { bookshelf =>
  import io.circe.Json
  Json.obj(
    "books" -> Encoder[Map[BookId, (Filters, BookReference)]].apply(bookshelf.books),
    "sorter" -> Encoder[BookSorter].apply(bookshelf.sorter)
  )
}

given Decoder[Bookshelf] = Decoder.instance { cursor =>
  for {
    books <- cursor.downField("books").as[Map[BookId, (Filters, BookReference)]]
    sorter <- cursor.downField("sorter").as[BookSorter]
  } yield Bookshelf(books, sorter)
}
