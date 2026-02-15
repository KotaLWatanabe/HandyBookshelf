package com.handybookshelf
package domain

import com.handybookshelf.util.{ArxivId, DOI, ISBN}
import com.handybookshelf.util.ArxivId.*
import com.handybookshelf.util.DOI.*
import com.handybookshelf.util.ISBN.*
import io.circe.{Decoder, Encoder}

/**
 * BookIdentifier - 書籍・文献の自然キー（ビジネス識別子）
 *
 * BookIdがサロゲートキー（技術的識別子）であるのに対し、 BookIdentifierはビジネス上の識別子を表す。
 *
 * 一意性の保証:
 *   - ISBN: 同じISBNを持つ書籍は重複とみなす（ISBN-10/13は正規化して比較）
 *   - Arxiv: 同じarXiv IDを持つ論文は重複とみなす（バージョンは無視）
 *   - DOI: 同じDOIを持つ文献は重複とみなす（大文字小文字は区別しない）
 *   - Title: 上記がない場合、タイトルで識別（厳密な一意性は保証しない）
 *
 * この型はBookRegistrationServiceで重複チェックに使用される。
 */
sealed trait BookIdentifier:
  def description: String
  def normalizedKey: NormalizedIdentifier

object BookIdentifier:

  /**
   * ISBN による識別（書籍に推奨）
   *
   * ISBNは国際的に一意な書籍識別子であり、 最も信頼性の高い重複チェックが可能。 ISBN-10とISBN-13は同一視される。
   */
  final case class ISBN(isbn: util.ISBN) extends BookIdentifier:
    override def description: String = s"ISBN: $isbn"
    override def normalizedKey: NormalizedIdentifier =
      // ISBNは既に数字のみなので、そのまま使用（ISBN-10/13は別として扱う）
      NormalizedIdentifier(s"isbn:$isbn")

  /**
   * arXiv ID による識別（論文に推奨）
   *
   * arXivは学術論文のプレプリントサーバーで、 各論文に一意のIDが付与される。 バージョン番号（v1, v2等）は無視して比較する。
   */
  final case class Arxiv(arxivId: ArxivId) extends BookIdentifier:
    override def description: String = s"arXiv: $arxivId"
    override def normalizedKey: NormalizedIdentifier =
      NormalizedIdentifier(s"arxiv:${arxivId.normalized}")

  /**
   * DOI による識別（学術論文・文献に推奨）
   *
   * DOI（Digital Object Identifier）は学術出版物の 永続的識別子。大文字小文字は区別しない。
   */
  final case class DOI(doi: util.DOI) extends BookIdentifier:
    override def description: String = s"DOI: $doi"
    override def normalizedKey: NormalizedIdentifier =
      NormalizedIdentifier(s"doi:${doi.normalized}")

  /**
   * タイトルによる識別（フォールバック）
   *
   * 上記の識別子がない場合に使用。 同一タイトルの異なる文献が存在しうるため、 厳密な一意性は保証されない。ユーザーへの警告表示に使用。
   */
  final case class Title(title: NES) extends BookIdentifier:
    override def description: String = s"Title: $title"
    override def normalizedKey: NormalizedIdentifier =
      NormalizedIdentifier(s"title:${title.toLowerCase.trim}")

  /** 後方互換性のためのエイリアス */
  @deprecated("Use ISBN instead", "2.0.0")
  type ISBNIdentifier = ISBN
  @deprecated("Use ISBN instead", "2.0.0")
  val ISBNIdentifier = ISBN

  @deprecated("Use Title instead", "2.0.0")
  type TitleIdentifier = Title
  @deprecated("Use Title instead", "2.0.0")
  val TitleIdentifier = Title

  /** ISBNからBookIdentifierを生成（便利メソッド） */
  def fromISBN(isbn: util.ISBN): BookIdentifier = ISBN(isbn)

  /** arXiv IDからBookIdentifierを生成（便利メソッド） */
  def fromArxiv(arxivId: ArxivId): BookIdentifier = Arxiv(arxivId)

  /** DOIからBookIdentifierを生成（便利メソッド） */
  def fromDOI(doi: util.DOI): BookIdentifier = DOI(doi)

  /** タイトルからBookIdentifierを生成（フォールバック用） */
  def fromTitle(title: NES): BookIdentifier = Title(title)

  // Circe codecs
  given Encoder[BookIdentifier] = Encoder.instance {
    case ISBN(isbn)     => Encoder.encodeString(s"isbn:$isbn")
    case Arxiv(arxivId) => Encoder.encodeString(s"arxiv:$arxivId")
    case DOI(doi)       => Encoder.encodeString(s"doi:$doi")
    case Title(title)   => Encoder.encodeString(s"title:$title")
  }

  given Decoder[BookIdentifier] = Decoder.decodeString.emap { str =>
    if str.startsWith("isbn:") then
      val isbnStr = str.drop(5)
      isbnStr.nesOpt.flatMap(_.isbnOpt) match
        case Some(isbn) => Right(ISBN(isbn))
        case None       => Left(s"Invalid ISBN: $isbnStr")
    else if str.startsWith("arxiv:") then
      val arxivStr = str.drop(6)
      util.ArxivId.fromString(arxivStr) match
        case Some(arxivId) => Right(Arxiv(arxivId))
        case None          => Left(s"Invalid arXiv ID: $arxivStr")
    else if str.startsWith("doi:") then
      val doiStr = str.drop(4)
      util.DOI.fromString(doiStr) match
        case Some(doi) => Right(DOI(doi))
        case None      => Left(s"Invalid DOI: $doiStr")
    else if str.startsWith("title:") then
      val titleStr = str.drop(6)
      titleStr.nesOpt match
        case Some(title) => Right(Title(title))
        case None        => Left("Title cannot be empty")
    else Left(s"Unknown identifier format: $str")
  }

/**
 * NormalizedIdentifier - 正規化された識別子
 *
 * 異なる形式の識別子（ISBN-10/13、arXivバージョンあり/なし、DOI大文字/小文字） を統一的に比較するための正規化された形式。
 *
 * フォーマット: "type:normalized_value"
 *   - isbn:9784873115658
 *   - arxiv:2301.12345
 *   - doi:10.1038/nature12373
 *   - title:some title
 */
opaque type NormalizedIdentifier = String

object NormalizedIdentifier:
  def apply(value: String): NormalizedIdentifier = value

  def from(identifier: BookIdentifier): NormalizedIdentifier =
    identifier.normalizedKey

  extension (normalized: NormalizedIdentifier)
    def value: String = normalized

    def identifierType: String =
      normalized.takeWhile(_ != ':')

    def identifierValue: String =
      normalized.dropWhile(_ != ':').drop(1)

  given Ordering[NormalizedIdentifier] = Ordering.String
  given Encoder[NormalizedIdentifier]  = Encoder.encodeString.contramap(_.value)
  given Decoder[NormalizedIdentifier]  = Decoder.decodeString.map(apply)
