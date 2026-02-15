package com.handybookshelf
package util

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

/** ArxivIdConstraint - arXiv IDの制約 */
type ArxivIdConstraint = Match["^\\d{4}\\.\\d{4,5}(v\\d+)?$"]

/**
 * ArxivId - arXiv論文の識別子
 *
 * フォーマット: YYMM.NNNNN または YYMM.NNNNNvN
 *   - YY: 年（07以降）
 *   - MM: 月（01-12）
 *   - NNNNN: 4桁または5桁の番号
 *   - vN: オプションのバージョン番号
 *
 * 例: 2301.12345, 2301.12345v2, 1706.03762
 */
type ArxivId = String :| ArxivIdConstraint

/** DOIConstraint - DOIの制約 */
type DOIConstraint = Match["^10\\.\\d{4,9}/[^\\s]+$"]

/**
 * DOI - Digital Object Identifier
 *
 * フォーマット: 10.prefix/suffix
 *   - prefix: 4桁以上の登録機関ID
 *   - suffix: 任意の文字列（英数字、ハイフン、アンダースコア、ピリオド、括弧、スラッシュなど）
 *
 * 例: 10.1000/xyz123, 10.1038/nature12373
 *
 * Note: DOIは大文字小文字を区別しないため、正規化時に小文字に変換する
 */
type DOI = String :| DOIConstraint

object ArxivId:
  /** 文字列からArxivIdへの変換を試みる */
  def fromString(str: String): Option[ArxivId] =
    str.refineOption[ArxivIdConstraint]

  /** ArxivIdのベース部分（バージョンなし）を取得 */
  def baseId(arxivId: ArxivId): String =
    arxivId.replaceAll("v\\d+$", "")

  /** ArxivIdを正規化（バージョンなし、小文字） */
  def normalize(arxivId: ArxivId): String =
    baseId(arxivId).toLowerCase

  extension (arxivId: ArxivId)
    /** バージョン番号を取得（存在する場合） */
    def version: Option[Int] =
      val versionPattern = "v(\\d+)$".r
      versionPattern.findFirstMatchIn(arxivId).map(_.group(1).toInt)

    /** バージョンなしのベースIDを取得 */
    def withoutVersion: String = baseId(arxivId)

    /** 正規化されたIDを取得 */
    def normalized: String = normalize(arxivId)

object DOI:
  /** 文字列からDOIへの変換を試みる */
  def fromString(str: String): Option[DOI] =
    // DOIは大文字小文字を区別しないが、入力時はそのまま保持
    str.refineOption[DOIConstraint]

  /** DOIを正規化（小文字に変換） */
  def normalize(doi: DOI): String =
    doi.toLowerCase

  extension (doi: DOI)
    /** プレフィックス部分を取得（10.XXXX） */
    def prefix: String =
      doi.split('/').head

    /** サフィックス部分を取得 */
    def suffix: String =
      doi.split('/').tail.mkString("/")

    /** 正規化されたDOIを取得（小文字） */
    def normalized: String = normalize(doi)

// NESへの拡張メソッド（パッケージレベルで公開）
extension (str: NES)
  /** 文字列からArxivIdへの変換を試みる */
  def arxivIdOpt: Option[ArxivId] = ArxivId.fromString(str)

  /** 文字列からDOIへの変換を試みる */
  def doiOpt: Option[DOI] = DOI.fromString(str)
