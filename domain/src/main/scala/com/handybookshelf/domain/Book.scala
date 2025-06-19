package  com.handybookshelf
package domain

import cats.data.NonEmptyList
import com.handybookshelf.util.{ISBN, NES, Timestamp}

final case class Book private (id: BookId, title: NES)
object Book:
  def generate(isbnStr: String, title: NES): Book =
    Book(BookId.generate(isbnStr, Timestamp.now()), title)

  def generateFromISBN(isbn: ISBN, title: NES): Book =
    Book(BookId.generateFromISBN(isbn, Timestamp.now()), title)

final case class BookReference(book: Book, tags: Seq[Tag], devices: NonEmptyList[Device]) {
  val bookId: BookId = book.id
}

sealed trait Device
object Device:
  case object Paper  extends Device
  case object Pdf    extends Device
  case object Ebook extends Device

final case class Tag(name: String)
