# データベース選定議論: WriteモデルとReadモデル

## 議事録: 2026-02-05

### 参加者（専門家ペルソナ）
- **データベース専門家**: イベントストア、DynamoDB、ScyllaDBの設計・最適化
- **DDD専門家**: ドメインモデリング、集約設計、イベントソーシングパターン
- **分散システム専門家**: アクターシステム、耐障害性、スケーラビリティ
- **Scala専門家**: 言語機能、関数型プログラミング、エフェクトシステム
- **サーバーセキュリティ専門家**: 認証・認可、脆弱性対策、セキュアコーディング

---

## 議題

イベントソーシングにおけるWriteモデル（イベントストア）とReadモデル（検索・プロジェクション）に最適なデータベースの選定

### 要件
1. イベントソーシング前提
2. キャッシュは別で考える（今回は対象外）
3. **本番と開発環境で使い方が同じものが望ましい**
4. Writeモデル: ドメインイベントのinsertとfetchのみ
5. Readモデル: 全ユーザーが登録した本の検索（大規模になる可能性）

---

## 現状分析

### 既存インフラ（docker-compose.yml）
- DynamoDB Local (port 8000)
- Elasticsearch 8.11.0 (port 9200)
- Kibana 8.11.0 (port 5601)
- Redis 7 (port 6379) - キャッシュ用

### 既存コード
- `DynamoDBEventStore.scala` - イベント永続化実装済み
- `ScyllaEventStore.scala` - 代替実装（存在するが未使用）
- `ElasticsearchClient.scala` - 検索クライアント実装済み
- `BookProjections.scala` - インメモリ実装（永続化なし）

---

## 専門家議論

### 1. データベース専門家

#### Writeモデル候補評価

| 選択肢 | 開発/本番一貫性 | ES適合 | 運用 | 既存実装 | 総合 |
|--------|----------------|--------|------|---------|------|
| DynamoDB | ◎ (Local) | ◎ | ○ | ◎ | **56** |
| ScyllaDB | △ | ◎ | △ | ◎ | 48 |
| PostgreSQL | ◎ | ○ | ○ | × | 41 |
| EventStoreDB | ○ | ◎ | ○ | × | 39 |

**見解**: DynamoDBのパーティションキー設計（persistence_id + sequence_nr）がイベントソーシングのアクセスパターンに最適。DynamoDB Localで開発/本番の一貫性も確保できる。

#### Readモデル候補評価

| 選択肢 | 全文検索 | 日本語 | 複合検索 | 既存実装 | 総合 |
|--------|---------|--------|---------|---------|------|
| Elasticsearch | ◎ | ◎ | ◎ | ◎ | **67** |
| PostgreSQL | △ | ○ | ○ | × | 49 |
| MongoDB | ○ | △ | ○ | × | 44 |
| DynamoDB+GSI | × | × | △ | ○ | 37 |

**見解**: Elasticsearchは日本語全文検索（kuromoji）と集約クエリに強い。全ユーザーの書籍検索という要件に最適。

### 2. DDD専門家

**見解**: WriteモデルとReadモデルの分離が明確になり、CQRSパターンに適合する。

```
┌─────────────────────────────────────────────────────────────┐
│                    Write Side                               │
│  Command → BookAggregate → DynamoDBEventStore → DynamoDB    │
│                                    │                        │
│                             イベント発行                     │
│                                    ↓                        │
├─────────────────────────────────────────────────────────────┤
│                    Read Side                                │
│  イベント → ProjectionManager → ElasticsearchClient → ES    │
│                                                             │
│  Query → SearchAPI → ElasticsearchClient → Elasticsearch    │
└─────────────────────────────────────────────────────────────┘
```

集約境界を保護しつつ、検索はRead Model経由で実現。プロジェクションの遅延（結果整合性）は許容範囲。

### 3. 分散システム専門家

**DynamoDB（Writeモデル）**:
- パーティションキー: `persistence_id`（ユーザー単位で分割可能）
- 自動スケーリング（On-Demand または Provisioned）
- 単一テーブル設計でシンプルな運用

**Elasticsearch（Readモデル）**:
- シャード分割による水平スケーリング
- レプリカによる可用性確保
- 本番環境では3ノード以上推奨

**見解**: 両DBとも個人利用から図書館規模までスケール可能。

### 4. Scala専門家

**ライブラリ評価**:
- DynamoDB: AWS SDK for Java 2.x（Cats Effect IOでラップ済み）
- Elasticsearch: http4s Client ベースの独自実装（依存最小化）

**見解**: 既存実装が`IO[Unit]`を返す設計で適切。FS2 Streamによるイベント配信も可能。

### 5. サーバーセキュリティ専門家

**DynamoDB**:
- IAMロールで最小権限の原則を適用
- 保存時暗号化（AWS KMS）を有効化すべき
- CloudTrailでアクセスログを取得

**Elasticsearch**:
- X-Pack Securityを本番環境で有効化必須
- ドキュメントレベルセキュリティの検討
- クエリインジェクション対策（パラメータサニタイズ）

**見解**: 既存実装で全クエリに`userAccountId`フィルタリングが実装済み。適切な設計。

---

## 決定事項

### Writeモデル: **DynamoDB**

**採用理由**:
1. `DynamoDBEventStore`が既に完成度高い実装として存在
2. DynamoDB Localで開発環境とAWS本番環境のAPIが完全一致
3. `persistence_id` (PK) + `sequence_nr` (SK)のスキーマがイベントソーシングに最適
4. 楽観的ロック（Expected Version）実装済み

**本番環境**: AWS DynamoDB（On-Demandキャパシティモード推奨）

### Readモデル: **Elasticsearch**

**採用理由**:
1. `ElasticsearchClient`が既に実装済み（タイトル検索、タグ検索、ユーザー別取得）
2. Docker Composeに Elasticsearch 8.11.0 設定済み
3. 日本語全文検索（kuromoji analyzer）に対応
4. 全ユーザーの書籍検索という要件に最適

**本番環境**: AWS OpenSearch Service または Elastic Cloud

---

## 不採用とした代替案

### Writeモデル
| 選択肢 | 不採用理由 |
|--------|-----------|
| ScyllaDB | 開発環境セットアップが煩雑、運用コスト高 |
| PostgreSQL | 水平スケーリング困難、イベントストア専用設計ではない |
| EventStoreDB | Scalaドライバが非公式、独自プロトコル |

### Readモデル
| 選択肢 | 不採用理由 |
|--------|-----------|
| PostgreSQL | 日本語全文検索は pg_trgm 拡張が必要、性能劣る |
| DynamoDB + GSI | 複雑な検索クエリに不向き |
| MongoDB | 全文検索は Atlas Search が必要 |

---

## 今後の課題

1. **プロジェクション永続化**: インメモリ → Elasticsearch への変更
2. **イベント駆動更新**: イベント発行 → Elasticsearch自動反映の仕組み
3. **プロジェクション再構築**: イベントストアからの再構築機能
4. **本番環境設定**: AWS DynamoDB + Amazon OpenSearch Service
5. **kuromoji設定**: 日本語アナライザーの最適化

---

## 関連ファイル

- `infrastructure/src/main/scala/com/handybookshelf/infrastructure/DynamoDBEventStore.scala`
- `infrastructure/src/main/scala/com/handybookshelf/infrastructure/ElasticsearchClient.scala`
- `query/src/main/scala/com/handybookshelf/query/BookProjections.scala`
- `docker-compose.yml`
