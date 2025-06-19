package com.handybookshelf
package domain
package repositories

import cats.effect.IO

/**
 * BookEvent専用リポジトリインターフェース
 * EventRepositoryを継承し、BookEvent固有の操作を追加
 */
trait BookEventRepository extends EventRepository[BookEvent]:
  
  /**
   * 書籍登録イベントを取得する
   * @param bookId 書籍ID
   * @return 書籍登録イベント。存在しない場合はNone
   */
  def getBookRegisteredEvent(bookId: BookId): IO[Option[BookRegistered]]
  
  /**
   * 書籍の場所変更履歴を取得する
   * @param bookId 書籍ID
   * @return 場所変更イベントのリスト（時系列順）
   */
  def getLocationChangeHistory(bookId: BookId): IO[List[BookLocationChanged]]
  
  /**
   * 書籍のタグ操作履歴を取得する
   * @param bookId 書籍ID
   * @return タグ操作イベントのリスト（時系列順）
   */
  def getTagOperationHistory(bookId: BookId): IO[List[BookEvent]]
  
  /**
   * 書籍のデバイス操作履歴を取得する
   * @param bookId 書籍ID
   * @return デバイス操作イベントのリスト（時系列順）
   */
  def getDeviceOperationHistory(bookId: BookId): IO[List[BookEvent]]
  
  /**
   * 書籍のタイトル更新履歴を取得する
   * @param bookId 書籍ID
   * @return タイトル更新イベントのリスト（時系列順）
   */
  def getTitleUpdateHistory(bookId: BookId): IO[List[BookTitleUpdated]]
  
  /**
   * 特定のISBNに関連する全てのイベントを取得する
   * @param isbn ISBN
   * @return 関連するイベントのリスト
   */
  def getEventsByISBN(isbn: ISBN): IO[List[BookEvent]]
  
  /**
   * 特定の場所に関連する全てのイベントを取得する
   * @param location 場所
   * @return 関連するイベントのリスト
   */
  def getEventsByLocation(location: Location): IO[List[BookEvent]]
  
  /**
   * 特定のタグに関連する全てのイベントを取得する
   * @param tag タグ
   * @return 関連するイベントのリスト
   */
  def getEventsByTag(tag: Tag): IO[List[BookEvent]]
  
  /**
   * 特定のデバイスに関連する全てのイベントを取得する
   * @param device デバイス
   * @return 関連するイベントのリスト
   */
  def getEventsByDevice(device: Device): IO[List[BookEvent]]
  
  /**
   * 削除された書籍のイベントを取得する
   * @return 削除イベントのリスト
   */
  def getAllBookRemovedEvents(): IO[List[BookRemoved]]
  
  /**
   * 指定期間に登録された書籍の数を取得する
   * @param from 開始時刻
   * @param to 終了時刻
   * @return 登録された書籍数
   */
  def countBooksRegisteredBetween(from: Timestamp, to: Timestamp): IO[Long]