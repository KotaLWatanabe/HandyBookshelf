package com.handybookshelf
package domain
package repositories

import cats.effect.IO

/**
 * 集約スナップショット専用リポジトリインターフェース 長いイベント履歴を持つ集約のパフォーマンス最適化に使用
 */
trait SnapshotRepository[A <: AggregateRoot[A, E], E <: DomainEvent]:

  /**
   * スナップショットを保存する
   * @param aggregate
   *   スナップショットを保存する集約
   * @return
   *   保存成功を示すIO
   */
  def saveSnapshot(aggregate: A): IO[Unit]

  /**
   * 最新のスナップショットを取得する
   * @param aggregateId
   *   集約ID
   * @return
   *   最新のスナップショット。存在しない場合はNone
   */
  def getLatestSnapshot(aggregateId: String): IO[Option[A]]

  /**
   * 指定バージョンのスナップショットを取得する
   * @param aggregateId
   *   集約ID
   * @param version
   *   バージョン
   * @return
   *   指定バージョンのスナップショット。存在しない場合はNone
   */
  def getSnapshotAtVersion(aggregateId: String, version: EventVersion): IO[Option[A]]

  /**
   * 指定バージョン以前の最新スナップショットを取得する
   * @param aggregateId
   *   集約ID
   * @param maxVersion
   *   最大バージョン
   * @return
   *   該当するスナップショット。存在しない場合はNone
   */
  def getLatestSnapshotBeforeVersion(aggregateId: String, maxVersion: EventVersion): IO[Option[A]]

  /**
   * 古いスナップショットを削除する
   * @param aggregateId
   *   集約ID
   * @param keepCount
   *   保持するスナップショット数
   * @return
   *   削除成功を示すIO
   */
  def deleteOldSnapshots(aggregateId: String, keepCount: Int): IO[Unit]

  /**
   * 全ての古いスナップショットを削除する
   * @param keepCount
   *   保持するスナップショット数
   * @return
   *   削除成功を示すIO
   */
  def deleteAllOldSnapshots(keepCount: Int): IO[Unit]

  /**
   * スナップショットが存在するかチェックする
   * @param aggregateId
   *   集約ID
   * @return
   *   存在する場合はtrue
   */
  def hasSnapshot(aggregateId: String): IO[Boolean]
