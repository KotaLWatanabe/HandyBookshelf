# HandyBookshelf Podman Setup

## 概要

HandyBookshelfのPodmanベース開発環境セットアップガイドです。ScyllaDB（イベントストア）とElasticsearch（クエリサイド）を含む完全なCQRSアーキテクチャを提供します。

## アーキテクチャ

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│ HandyBookshelf  │    │   ScyllaDB      │    │ Elasticsearch   │
│   Application   │◄───┤  (Event Store)  │    │ (Query Side)    │
│                 │    │                 │    │                 │
│ - UserSession   │    │ - event_store   │    │ - books index   │
│ - Bookshelf     │    │ - snapshots     │    │ - events index  │
│ - Supervisor    │    │ - sessions      │    │ - sessions idx  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌─────────────────┐
                    │     Redis       │
                    │   (Caching)     │
                    └─────────────────┘
```

## サービス構成

| サービス | ポート | 用途 | 
|---------|--------|------|
| HandyBookshelf App | 8080 | メインアプリケーション |
| ScyllaDB | 9042 | イベントソーシング用データストア |
| Elasticsearch | 9200 | 検索・クエリ用インデックス |
| Kibana | 5601 | Elasticsearchビジュアライゼーション |
| Redis | 6379 | キャッシュ層 |

## クイックスタート

### 1. インフラサービス起動

```bash
# インフラサービス（ScyllaDB, Elasticsearch, Redis）のみ起動
./scripts/start-services.sh

# Kibana付きで起動
./scripts/start-services.sh --with-kibana

# ログ表示付きで起動
./scripts/start-services.sh --logs
```

### 2. アプリケーション起動（ローカル）

```bash
# 便利スクリプト使用（推奨）
./scripts/run-app-local.sh

# 手動実行
sbt "project controller" run
```

### 3. サービス確認

```bash
# 全サービス状態確認
podman-compose ps

# 個別サービスログ確認
podman-compose logs -f handybookshelf-app
podman-compose logs -f scylladb
podman-compose logs -f elasticsearch

# ヘルスチェック
curl http://localhost:8080/health
curl http://localhost:9200/_cluster/health
```

### 4. データベース操作

#### ScyllaDB操作
```bash
# CQLシェル接続
podman-compose exec scylladb cqlsh -u cassandra -p cassandra

# キースペース確認
DESCRIBE KEYSPACES;

# テーブル確認
USE handybookshelf;
DESCRIBE TABLES;

# イベント確認
SELECT * FROM event_store LIMIT 10;
```

#### Elasticsearch操作
```bash
# インデックス一覧
curl http://localhost:9200/_cat/indices?v

# 書籍検索例
curl -X POST "http://localhost:9200/handybookshelf-books/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "match": {
        "title": "プログラミング"
      }
    }
  }'

# イベント検索例  
curl -X POST "http://localhost:9200/handybookshelf-events/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "term": {
        "eventType": "BookAdded"
      }
    }
  }'
```

## 開発ワークフロー

### アプリケーション開発

```bash
# アプリケーションのみ再ビルド・再起動
podman-compose build handybookshelf-app
podman-compose restart handybookshelf-app

# ローカル開発（SBT使用）
sbt "project controller" run
# この場合、データベースサービスのみ起動
podman-compose up -d scylladb elasticsearch redis
```

### デバッグ

```bash
# アプリケーションコンテナに接続
podman-compose exec handybookshelf-app /bin/bash

# ログリアルタイム監視
podman-compose logs -f --tail=100

# 特定サービスのリスタート
podman-compose restart scylladb
```

## 設定

### 環境変数

`.env`ファイルで設定をカスタマイズできます：

```env
# データベース設定
SCYLLA_HOSTS=scylladb:9042
SCYLLA_KEYSPACE=handybookshelf
SCYLLA_USERNAME=cassandra
SCYLLA_PASSWORD=cassandra

# Elasticsearch設定
ELASTICSEARCH_HOSTS=http://elasticsearch:9200
ELASTICSEARCH_INDEX_PREFIX=handybookshelf

# アプリケーション設定
HTTP_HOST=0.0.0.0
HTTP_PORT=8080
LOG_LEVEL=INFO
```

### ボリューム

データの永続化用ボリューム：

- `scylla_data`: ScyllaDBデータ
- `elasticsearch_data`: Elasticsearchデータ  
- `redis_data`: Redisデータ

## トラブルシューティング

### よくある問題

1. **ScyllaDBが起動しない**
   ```bash
   # メモリ不足の場合
   podman-compose down
   # podman-compose.ymlでScyllaDBメモリ設定を調整
   podman-compose up -d scylladb
   ```

2. **Elasticsearchが起動しない**
   ```bash
   # vm.max_map_count設定
   sudo sysctl -w vm.max_map_count=262144
   echo 'vm.max_map_count=262144' | sudo tee -a /etc/sysctl.conf
   ```

3. **アプリケーションがデータベースに接続できない**
   ```bash
   # ネットワーク確認
   podman-compose exec handybookshelf-app ping scylladb
   podman-compose exec handybookshelf-app ping elasticsearch
   
   # 待機スクリプト確認
   podman-compose logs handybookshelf-app
   ```

### クリーンアップ

```bash
# サービス停止
podman-compose down

# データも含めて完全クリーンアップ
podman-compose down -v --remove-orphans

# イメージも削除
podman-compose down -v --rmi all
```

## パフォーマンスチューニング

### ScyllaDB
- `--smp=1`: CPU使用コア数調整
- `--memory=750M`: メモリ使用量調整
- `--overprovisioned=1`: オーバープロビジョニングモード

### Elasticsearch
- `ES_JAVA_OPTS=-Xms512m -Xmx512m`: JVMヒープサイズ
- `bootstrap.memory_lock=true`: メモリロック

## 監視

### ヘルスチェック
```bash
# 全サービスヘルス確認
./scripts/health-check.sh

# 個別ヘルス確認
curl http://localhost:8080/health        # アプリケーション
curl http://localhost:9200/_cluster/health # Elasticsearch
```

### メトリクス
- ScyllaDB: http://localhost:10000 (REST API)
- Elasticsearch: http://localhost:9200/_nodes/stats
- アプリケーション: http://localhost:8080/metrics

## 本番環境への展開

本番環境では以下を検討：

1. **セキュリティ**
   - データベース認証情報の暗号化
   - ネットワークセグメンテーション
   - TLS/SSL設定

2. **スケーリング**
   - ScyllaDBクラスタ構成
   - Elasticsearchクラスタ構成
   - アプリケーションレプリカ

3. **バックアップ**
   - ScyllaDBスナップショット
   - Elasticsearchインデックススナップショット
   - 設定ファイルバックアップ

## API使用例

### ユーザーログイン
```bash
curl -X POST http://localhost:8080/login \
  -H "Content-Type: application/json" \
  -d '{"userAccountId": "user123"}'
```

### 書籍追加
```bash
curl -X POST http://localhost:8080/books \
  -H "Content-Type: application/json" \
  -d '{
    "bookId": "book123",
    "title": "Scala実践プログラミング",
    "isbn": "9784567890123",
    "authors": ["田中太郎", "佐藤花子"]
  }'
```

このセットアップにより、HandyBookshelfの完全な開発・テスト環境が構築できます。