package com.handybookshelf
package domain
package repositories

import cats.effect.IO
import com.handybookshelf.infrastructure.EventVersion
import com.handybookshelf.Timestamp

/**
 * ドメインイベント専用リポジトリインターフェース
 * Event Storeの低レベルアクセスを提供
 */
trait EventRepository[E <: DomainEvent]:
  
  /**
   * イベントを保存する
   * @param event 保存するイベント
   * @return 保存成功を示すIO
   */
  def saveEvent(event: E): IO[Unit]
  
  /**
   * 複数のイベントを一括保存する（トランザクション）
   * @param events 保存するイベントのリスト
   * @return 保存成功を示すIO
   */
  def saveEvents(events: List[E]): IO[Unit]
  
  /**
   * 集約IDに関連する全てのイベントを取得する
   * @param aggregateId 集約ID
   * @return イベントのリスト（時系列順）
   */
  def getEventsByAggregateId(aggregateId: String): IO[List[E]]
  
  /**
   * 集約IDの指定バージョン以降のイベントを取得する
   * @param aggregateId 集約ID
   * @param fromVersion 開始バージョン（含む）
   * @return イベントのリスト（時系列順）
   */
  def getEventsByAggregateIdFromVersion(aggregateId: String, fromVersion: EventVersion): IO[List[E]]
  
  /**
   * 指定期間のイベントを取得する
   * @param from 開始時刻
   * @param to 終了時刻
   * @return イベントのリスト（時系列順）
   */
  def getEventsBetween(from: Timestamp, to: Timestamp): IO[List[E]]
  
  /**
   * 指定されたイベントタイプのイベントを取得する
   * @param eventType イベントタイプ
   * @return イベントのリスト（時系列順）
   */
  def getEventsByType(eventType: String): IO[List[E]]
  
  /**
   * 集約の最後のイベントを取得する
   * @param aggregateId 集約ID
   * @return 最後のイベント。存在しない場合はNone
   */
  def getLastEvent(aggregateId: String): IO[Option[E]]
  
  /**
   * 集約の現在のバージョンを取得する
   * @param aggregateId 集約ID
   * @return 現在のバージョン。存在しない場合はNone
   */
  def getCurrentVersion(aggregateId: String): IO[Option[EventVersion]]
  
  /**
   * 指定されたイベントIDのイベントを取得する
   * @param eventId イベントID
   * @return イベント。存在しない場合はNone
   */
  def getEventById(eventId: EventId): IO[Option[E]]
  
  /**
   * イベントストリームを取得する（リアルタイム購読用）
   * @param fromTimestamp 開始時刻
   * @return イベントストリーム
   */
  def getEventStream(fromTimestamp: Option[Timestamp] = None): fs2.Stream[IO, E]