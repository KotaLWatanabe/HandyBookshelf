package com.handybookshelf
package domain
package repositories

import cats.effect.IO

/**
 * Book集約専用リポジトリインターフェース
 * AggregateRepositoryを継承し、Book固有の操作を追加
 */
trait BookRepository extends AggregateRepository[BookAggregate, BookEvent]:
  
  /**
   * BookIdから書籍集約を取得する
   * @param bookId 書籍ID
   * @return 書籍集約。存在しない場合はNone
   */
  def findByBookId(bookId: BookId): IO[Option[BookAggregate]] =
    findById(bookId.toString)
  
  /**
   * ISBNから書籍集約を検索する
   * @param isbn ISBN
   * @return 該当する書籍集約のリスト
   */
  def findByISBN(isbn: ISBN): IO[List[BookAggregate]]
  
  /**
   * タイトルで書籍集約を検索する（部分一致）
   * @param titlePattern タイトルの検索パターン
   * @return 該当する書籍集約のリスト
   */
  def findByTitleContaining(titlePattern: String): IO[List[BookAggregate]]
  
  /**
   * 場所から書籍集約を検索する
   * @param location 場所
   * @return 該当する書籍集約のリスト
   */
  def findByLocation(location: Location): IO[List[BookAggregate]]
  
  /**
   * タグから書籍集約を検索する
   * @param tag タグ
   * @return 該当する書籍集約のリスト
   */
  def findByTag(tag: Tag): IO[List[BookAggregate]]
  
  /**
   * デバイスから書籍集約を検索する
   * @param device デバイス
   * @return 該当する書籍集約のリスト
   */
  def findByDevice(device: Device): IO[List[BookAggregate]]
  
  /**
   * 削除されていない全ての書籍集約を取得する
   * @return アクティブな書籍集約のリスト
   */
  def findAllActive(): IO[List[BookAggregate]]
  
  /**
   * 削除された書籍集約を取得する
   * @return 削除された書籍集約のリスト
   */
  def findAllDeleted(): IO[List[BookAggregate]]
  
  /**
   * 書籍集約の総数を取得する（削除されたものを除く）
   * @return アクティブな書籍の総数
   */
  def countActive(): IO[Long]
  
  /**
   * 指定された期間に更新された書籍集約を取得する
   * @param from 開始時刻
   * @param to 終了時刻
   * @return 期間内に更新された書籍集約のリスト
   */
  def findUpdatedBetween(from: Timestamp, to: Timestamp): IO[List[BookAggregate]]