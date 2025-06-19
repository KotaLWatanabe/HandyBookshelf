package com.handybookshelf
package domain
package repositories

import cats.effect.IO
import com.handybookshelf.infrastructure.EventVersion

/**
 * 汎用的な集約リポジトリの基底トレイト
 * Event Sourcingにおける集約の永続化と復元を抽象化
 */
trait AggregateRepository[A <: AggregateRoot[A, E], E <: DomainEvent]:
  
  /**
   * 集約のイベント履歴をセーブする
   * @param aggregate セーブする集約
   * @return セーブ後の集約（未コミットイベントがクリアされた状態）
   */
  def save(aggregate: A): IO[A]
  
  /**
   * 集約IDから集約を復元する
   * @param aggregateId 集約ID
   * @return 復元された集約。存在しない場合はNone
   */
  def findById(aggregateId: String): IO[Option[A]]
  
  /**
   * 集約が存在するかチェックする
   * @param aggregateId 集約ID
   * @return 存在する場合はtrue
   */
  def exists(aggregateId: String): IO[Boolean]
  
  /**
   * 集約の現在のバージョンを取得する
   * @param aggregateId 集約ID
   * @return 現在のバージョン。存在しない場合はNone
   */
  def getVersion(aggregateId: String): IO[Option[EventVersion]]
  
  /**
   * 指定されたバージョン以降のイベントを取得する
   * @param aggregateId 集約ID
   * @param fromVersion 開始バージョン（含む）
   * @return イベントのリスト
   */
  def getEventsFromVersion(aggregateId: String, fromVersion: EventVersion): IO[List[E]]
  
  /**
   * 集約のスナップショットを保存する（オプション）
   * 長いイベント履歴を持つ集約のパフォーマンス最適化に使用
   * @param aggregate スナップショットを保存する集約
   * @return 保存成功を示すIO
   */
  def saveSnapshot(aggregate: A): IO[Unit] = 
    val _ = aggregate // 未使用パラメータ警告を回避
    IO.unit
  
  /**
   * 集約のスナップショットを取得する（オプション）
   * @param aggregateId 集約ID
   * @return スナップショット。存在しない場合はNone
   */
  def getSnapshot(aggregateId: String): IO[Option[A]] = 
    val _ = aggregateId // 未使用パラメータ警告を回避
    IO.pure(None)