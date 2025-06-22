# HandyBookshelf Database Migrations

このディレクトリには、HandyBookshelfアプリケーション用のScyllaDBデータベーススキーママイグレーションファイルが含まれています。

## マイグレーションファイル一覧

### V001__Create_Event_Store_Tables.cql
**Event Sourcing 基盤テーブル**
- `event_store`: ドメインイベントの永続化
- `snapshot_store`: パフォーマンス最適化用スナップショット
- `event_metadata`: イベント分析用メタデータ

### V002__Create_User_Session_Tables.cql
**ユーザーセッション管理テーブル**
- `user_sessions`: セッション情報の管理
- `sessions_by_expiration`: 期限切れセッションのクリーンアップ用
- `active_sessions`: アクティブセッションの監視用
- `user_activity_log`: ユーザー活動の監査ログ

### V003__Create_Bookshelf_Read_Model_Tables.cql
**CQRS Query Side テーブル**
- `bookshelf_read_model`: 書庫の読み取り最適化モデル
- `books_by_title`: タイトル検索用
- `books_by_isbn`: ISBN検索用
- `books_by_tag`: タグフィルタリング用
- `books_by_status`: ステータス管理用
- `books_by_location`: 物理的位置管理用
- `recent_books_activity`: ダッシュボード用最近の活動

### V004__Create_Materialized_Views.cql
**マテリアライズドビューとUDF**
- `books_by_recent_addition`: 最近追加された書籍
- `books_by_recent_update`: 最近更新された書籍
- `active_books_by_status`: ステータス別アクティブ書籍
- `recent_user_activity`: 最近のユーザー活動
- `current_active_sessions`: 現在のアクティブセッション
- `events_by_type_recent`: タイプ別最近のイベント
- UDF: `title_prefix()`, `days_since_added()`, `book_display_name()`

### V005__Insert_Reference_Data.cql
**基準データとマスターデータ**
- `app_config`: アプリケーション設定
- `book_status_ref`: 書籍ステータス基準データ
- `book_location_ref`: 書籍位置基準データ
- `event_type_ref`: イベントタイプ基準データ
- 初期データの投入

### V006__Create_Analytics_Tables.cql
**分析・レポート用テーブル**
- `daily_stats`: 日次統計
- `user_engagement_metrics`: ユーザーエンゲージメント指標
- `popular_books`: 人気書籍ランキング
- `search_analytics`: 検索分析
- `system_performance_metrics`: システムパフォーマンス指標
- `error_logs`: エラーログ
- `feature_usage`: 機能使用追跡
- `monthly_user_stats`: 月次ユーザー統計
- `monthly_system_stats`: 月次システム統計

## 実行方法

### 手動実行
```bash
# ScyllaDBに接続
docker-compose exec scylladb cqlsh -u cassandra -p cassandra

# 各マイグレーションを順番に実行
SOURCE '/docker-entrypoint-initdb.d/V001__Create_Event_Store_Tables.cql';
SOURCE '/docker-entrypoint-initdb.d/V002__Create_User_Session_Tables.cql';
SOURCE '/docker-entrypoint-initdb.d/V003__Create_Bookshelf_Read_Model_Tables.cql';
SOURCE '/docker-entrypoint-initdb.d/V004__Create_Materialized_Views.cql';
SOURCE '/docker-entrypoint-initdb.d/V005__Insert_Reference_Data.cql';
SOURCE '/docker-entrypoint-initdb.d/V006__Create_Analytics_Tables.cql';
```

### スクリプト実行
```bash
# 全マイグレーション実行
./scripts/run-migrations.sh

# 特定バージョンまで実行
./scripts/run-migrations.sh V003
```

## テーブル構造概要

### Event Sourcing パターン
```
event_store → snapshot_store → event_metadata
     ↓
Command Side (Write)
```

### CQRS パターン
```
event_store → projection → bookshelf_read_model
                      ↓
                  Query Side (Read)
```

### セッション管理
```
user_sessions → sessions_by_expiration → active_sessions
     ↓
user_activity_log
```

### 分析・監視
```
daily_stats → monthly_user_stats → monthly_system_stats
search_analytics → feature_usage → error_logs
```

## インデックス戦略

### 主要インデックス
- **Event Store**: `event_type`, `event_timestamp`
- **Sessions**: `expires_at`, `is_active`, `last_activity`
- **Bookshelf**: `book_title`, `book_status`, `added_at`, `updated_at`
- **Analytics**: `metric_type`, `user_account_id`, `search_term`

### パフォーマンス考慮事項
- **パーティションキー**: 書き込み分散を考慮した設計
- **クラスタリングキー**: 読み取りパターンに最適化
- **マテリアライズドビュー**: 頻繁なクエリパターンに対応
- **セカンダリインデックス**: 最小限に抑制

## データ保持ポリシー

### 保持期間
- **イベントストア**: 永続（スナップショットで最適化）
- **セッション**: 30日（期限切れ後）
- **ユーザー活動**: 90日
- **エラーログ**: 30日
- **分析データ**: 2年

### クリーンアップ
```sql
-- 期限切れセッションのクリーンアップ例
DELETE FROM user_sessions 
WHERE expires_at < dateOf(now()) - 30;
```

## 運用コマンド

### ステータス確認
```sql
-- テーブル一覧
DESCRIBE TABLES;

-- テーブル構造確認
DESCRIBE TABLE event_store;

-- データ件数確認
SELECT COUNT(*) FROM event_store;
```

### パフォーマンス監視
```sql
-- 最近のイベント確認
SELECT * FROM event_store 
WHERE event_timestamp >= dateOf(now()) - 1 
LIMIT 10;

-- アクティブセッション確認
SELECT COUNT(*) FROM user_sessions 
WHERE is_active = true 
  AND expires_at > toTimestamp(now());
```

このマイグレーション構成により、スケーラブルで分析可能なEvent Sourcing + CQRSアーキテクチャが実現されます。