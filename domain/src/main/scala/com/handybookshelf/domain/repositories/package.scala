package com.handybookshelf 
package domain

/**
 * リポジトリパッケージ
 * Event Sourcingアーキテクチャにおけるリポジトリインターフェースを提供
 * 
 * 主要なリポジトリ:
 * - AggregateRepository: 汎用的な集約リポジトリの基底トレイト
 * - BookRepository: Book集約専用リポジトリ
 * - EventRepository: ドメインイベント専用リポジトリ
 * - BookEventRepository: BookEvent専用リポジトリ
 * - SnapshotRepository: 集約スナップショット専用リポジトリ
 * - BookSnapshotRepository: BookAggregate専用スナップショットリポジトリ
 */
package object repositories:
  
  /**
   * リポジトリエラーの基底トレイト
   */
  sealed trait RepositoryError extends DomainError
  
  /**
   * 集約が見つからない場合のエラー
   */
  final case class AggregateNotFound(aggregateId: String, cause: Option[Throwable] = None) extends RepositoryError:
    override def message: String = s"Aggregate not found: $aggregateId"
  
  /**
   * バージョン競合エラー
   */
  final case class VersionConflict(
      aggregateId: String, 
      expectedVersion: EventVersion, 
      actualVersion: EventVersion,
      cause: Option[Throwable] = None
  ) extends RepositoryError:
    override def message: String = 
      s"Version conflict for aggregate $aggregateId: expected $expectedVersion, actual $actualVersion"
  
  /**
   * 永続化エラー
   */
  final case class PersistenceError(override val message: String, cause: Option[Throwable] = None) extends RepositoryError
  
  /**
   * 同時実行制御エラー
   */
  final case class ConcurrencyError(aggregateId: String, errorMessage: String, cause: Option[Throwable] = None) extends RepositoryError:
    override def message: String = s"Concurrency error for aggregate $aggregateId: $errorMessage"