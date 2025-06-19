package com.handybookshelf package domain
package repositories

import cats.effect.IO
import com.handybookshelf.util.{ISBN, Timestamp}

/**
 * BookAggregate専用スナップショットリポジトリインターフェース
 * SnapshotRepositoryを継承し、BookAggregate固有の操作を追加
 */
trait BookSnapshotRepository extends SnapshotRepository[BookAggregate, BookEvent]:
  
  /**
   * BookIdからスナップショットを取得する
   * @param bookId 書籍ID
   * @return 最新のスナップショット。存在しない場合はNone
   */
  def getLatestSnapshotByBookId(bookId: BookId): IO[Option[BookAggregate]] =
    getLatestSnapshot(bookId.toString)
  
  /**
   * ISBNを持つ書籍のスナップショットを取得する
   * @param isbn ISBN
   * @return 該当するスナップショットのリスト
   */
  def getSnapshotsByISBN(isbn: ISBN): IO[List[BookAggregate]]
  
  /**
   * 場所を持つ書籍のスナップショットを取得する
   * @param location 場所
   * @return 該当するスナップショットのリスト
   */
  def getSnapshotsByLocation(location: Location): IO[List[BookAggregate]]
  
  /**
   * アクティブな書籍のスナップショットを取得する
   * @return 削除されていない書籍のスナップショットのリスト
   */
  def getActiveSnapshots(): IO[List[BookAggregate]]
  
  /**
   * 指定期間に更新された書籍のスナップショットを取得する
   * @param from 開始時刻
   * @param to 終了時刻
   * @return 期間内に更新された書籍のスナップショットのリスト
   */
  def getSnapshotsUpdatedBetween(from: Timestamp, to: Timestamp): IO[List[BookAggregate]]
  
  /**
   * スナップショット作成のしきい値をチェックして、必要に応じてスナップショットを作成する
   * @param bookId 書籍ID
   * @param eventThreshold イベント数のしきい値
   * @return スナップショットが作成された場合はtrue
   */
  def createSnapshotIfNeeded(bookId: BookId, eventThreshold: Int): IO[Boolean]