package com.handybookshelf
package domain

trait BookSorter:
  def compare: (BookReference, BookReference) => Int
  def sortAsc: (BookReference, BookReference) => Boolean = compare(_, _) < 0

trait Filter:
  def predicate[A]: A => Boolean

final case class Filters(filters: Set[Filter]):
  def predicates[A]: A => Boolean            = a => filters.forall(_.predicate(a))
  def addFilter(filter: Filter): Filters    = Filters(filters + filter)
  def removeFilter(filter: Filter): Filters = Filters(filters - filter)

final case class Bookshelf (
    private val books: Map[BookId, (Filters, BookReference)],
    private val sorter: BookSorter
):
  private val filteredBooks: IndexedSeq[BookReference] =
    books.toIndexedSeq.filter { case (_, (filters, ref)) => filters.predicates(ref) }.map(_._2._2)

  val viewBooks: IndexedSeq[BookReference] = filteredBooks.sortWith(sorter.sortAsc(_, _))

  def changeSorter(newSorter: BookSorter): Bookshelf = Bookshelf(books, newSorter)
  def addBook(book: BookReference, filters: Filters, bookId: BookId): Bookshelf =
    Bookshelf(books.updated(bookId, (filters, book)), sorter)
  def addFilter(newFilter: Filter, bookId: BookId): Bookshelf =
    updateBookshelf(bookId, (filters, bookRef) => (filters.addFilter(newFilter), bookRef))
  def removeFilter(newFilter: Filter, bookId: BookId): Bookshelf =
    updateBookshelf(bookId, (filters, bookRef) => (filters.removeFilter(newFilter), bookRef))

  private def updateBookshelf(
      bookId: BookId,
      updateFunc: (Filters, BookReference) => (Filters, BookReference)
  ): Bookshelf = Bookshelf(
    books.updatedWith(bookId)(_.map(updateFunc(_, _))),
    sorter
  )
