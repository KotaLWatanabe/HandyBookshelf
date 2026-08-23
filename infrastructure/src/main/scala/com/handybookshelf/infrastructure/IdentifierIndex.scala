package com.handybookshelf
package infrastructure

import cats.effect.IO
import cats.effect.kernel.Ref
import com.handybookshelf.domain.{BookId, NormalizedIdentifier}
import NormalizedIdentifier.*

/**
 * IdentifierIndex - 識別子からBookIdへのマッピングを管理
 *
 * 書籍の重複チェックを効率的に行うためのインデックス。 NormalizedIdentifier（正規化された識別子）をキーとして、 対応するBookIdを高速にルックアップできる。
 *
 * イベントソーシングにおいて、重複チェックは頻繁に発生するため、 イベントストアを毎回スキャンするのではなく、 専用のインデックスを維持することでパフォーマンスを向上させる。
 */
trait IdentifierIndex:

  /**
   * 識別子とBookIdのマッピングを登録
   *
   * @param normalizedId
   *   正規化された識別子
   * @param bookId
   *   対応するBookId
   */
  def put(normalizedId: NormalizedIdentifier, bookId: BookId): IO[Unit]

  /**
   * 識別子からBookIdを取得
   *
   * @param normalizedId
   *   正規化された識別子
   * @return
   *   存在する場合はSome(BookId)、なければNone
   */
  def get(normalizedId: NormalizedIdentifier): IO[Option[BookId]]

  /**
   * 識別子が既に登録されているかチェック
   *
   * @param normalizedId
   *   正規化された識別子
   * @return
   *   登録済みならtrue
   */
  def exists(normalizedId: NormalizedIdentifier): IO[Boolean]

  /**
   * 識別子のマッピングを削除
   *
   * @param normalizedId
   *   正規化された識別子
   */
  def delete(normalizedId: NormalizedIdentifier): IO[Unit]

object IdentifierIndex:

  /** InMemory実装を作成 */
  def inMemory: IO[IdentifierIndex] =
    Ref.of[IO, Map[String, BookId]](Map.empty).map(new InMemoryIdentifierIndex(_))

/**
 * InMemoryIdentifierIndex - メモリ上での識別子インデックス実装
 *
 * 開発・テスト環境向けの実装。 プロセス再起動時にデータは消失するが、 起動時にイベントストアから再構築可能。
 *
 * Cats Effect の Ref を使用してスレッドセーフな状態管理を実現。
 */
class InMemoryIdentifierIndex(ref: Ref[IO, Map[String, BookId]]) extends IdentifierIndex:

  override def put(normalizedId: NormalizedIdentifier, bookId: BookId): IO[Unit] =
    ref.update(_.updated(normalizedId.value, bookId))

  override def get(normalizedId: NormalizedIdentifier): IO[Option[BookId]] =
    ref.get.map(_.get(normalizedId.value))

  override def exists(normalizedId: NormalizedIdentifier): IO[Boolean] =
    ref.get.map(_.contains(normalizedId.value))

  override def delete(normalizedId: NormalizedIdentifier): IO[Unit] =
    ref.update(_ - normalizedId.value)
