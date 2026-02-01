# イベントストーミング: 本のリストソート

**日付**: 2026-02-01
**参加者**:
- ユーザー（プロダクトオーナー）
- 田中博士（DDD専門家）
- 佐藤教授（データベース専門家）
- 鈴木氏（Scala専門家）
- 山田氏（分散システム専門家）

**スコープ**: 本のリスト表示時のソート機能

---

## 1. ビッグピクチャー

### 1.1 ユーザーストーリー

> 「本の一覧をタイトル順、著者順、登録日順、出版年順でソートしたい」

### 1.2 要件（確定）

| 項目 | 決定 |
|------|------|
| ソート項目 | タイトル、著者、登録日、出版年 |
| 適用範囲 | 全画面共通（検索、フィルタ、一覧すべて） |
| ソート方向 | 昇順/降順 両対応 |

---

## 2. 専門家ディスカッション

### 田中博士（DDD）: ソート機能の位置づけ

**分析**:
- ソートはデータの「表示方法」であり、ドメインロジックではない
- Write Sideには影響なし
- Read Model（Projection）のクエリパラメータとして実装

**結論**: **クエリ機能の拡張のみ**で対応。新規コマンド/イベントは不要。

### 佐藤教授（データベース）: ソート実装の考慮点

**日本語ソートの課題**:
```
問題: 「あ」「ア」「亜」の順序
  - Unicode順: 数字 → 英字 → ひらがな → カタカナ → 漢字
  - 辞書順: 読みがなベース

推奨: Collator（ロケール対応ソート）の使用
```

**インメモリソートのパフォーマンス**:
```
5,000件のソート:
  - Scala標準sortBy: ~5ms
  - 事前ソート済みリスト: ~1ms（キャッシュ）

結論: インメモリで十分高速
```

### 鈴木氏（Scala）: ソート関連の型設計

```scala
// ソートフィールド
enum SortField:
  case Title        // タイトル
  case Author       // 著者
  case RegisteredAt // 登録日
  case PublishedYear // 出版年

// ソート方向
enum SortDirection:
  case Asc   // 昇順
  case Desc  // 降順

// ソート指定
final case class SortSpec(
  field: SortField,
  direction: SortDirection = SortDirection.Asc
)

object SortSpec:
  val byTitleAsc = SortSpec(SortField.Title, SortDirection.Asc)
  val byRecentFirst = SortSpec(SortField.RegisteredAt, SortDirection.Desc)
```

### 山田氏（分散システム）: 一貫性

**考慮点**:
- ソートはRead Modelに対して行う
- 同じクエリは同じ順序を返すべき（決定的）
- Null値の扱いを統一（末尾に配置）

---

## 3. 設計決定

### 3.1 ソートフィールド詳細

| フィールド | ソートキー | Null時の扱い |
|-----------|-----------|-------------|
| タイトル | `title` (String) | なし（必須フィールド） |
| 著者 | `author` (Option[String]) | 末尾に配置 |
| 登録日 | `registeredAt` (Timestamp) | なし（必須フィールド） |
| 出版年 | `publishedYear` (Option[Int]) | 末尾に配置 |

### 3.2 デフォルトソート

| 画面 | デフォルトソート |
|------|----------------|
| 本の一覧 | タイトル昇順 |
| 検索結果 | 関連度降順（スコア） |
| タグフィルタ | タイトル昇順 |

### 3.3 共通ソートインターフェース

```scala
// すべてのリスト取得クエリに適用
trait SortableQuery:
  def sortBy: SortSpec

// 検索クエリ（既存を拡張）
final case class SearchQuery(
  text: String,
  fields: Set[SearchField],
  sortBy: SortSpec = SortSpec.byRelevance,  // 検索はスコアデフォルト
  limit: Int = 50,
  offset: Int = 0
)

// タグフィルタクエリ（既存を拡張）
final case class TagFilterQuery(
  tags: Set[NormalizedTagName],
  operator: TagFilterOperator,
  sortBy: SortSpec = SortSpec.byTitleAsc,  // フィルタはタイトルデフォルト
  limit: Int = 50,
  offset: Int = 0
)

// 一覧取得クエリ
final case class ListBooksQuery(
  sortBy: SortSpec = SortSpec.byTitleAsc,
  limit: Int = 50,
  offset: Int = 0
)
```

---

## 4. イベントストーミング結果

### 4.1 ドメインイベント（オレンジ付箋）

**新規イベントなし** - ソートは表示ロジックであり、ドメインイベントは不要

### 4.2 コマンド（青付箋）

**新規コマンドなし** - ソートはクエリのみ

### 4.3 クエリ（緑付箋）

| クエリ名 | 変更内容 | 状態 |
|---------|---------|------|
| `SearchBooks` | `sortBy`パラメータ追加 | **拡張** |
| `FilterBooksByTags` | `sortBy`パラメータ追加 | **拡張** |
| `ListBooks` | `sortBy`パラメータ追加 | **拡張** |

### 4.4 共通型（黄付箋）

| 型名 | 説明 | 状態 |
|------|------|------|
| `SortField` | ソートフィールドenum | **新規** |
| `SortDirection` | ソート方向enum | **新規** |
| `SortSpec` | ソート指定 | **新規** |

---

## 5. 実装設計

### 5.1 SortField enum

```scala
enum SortField(val apiName: String):
  case Title extends SortField("title")
  case Author extends SortField("author")
  case RegisteredAt extends SortField("registered")
  case PublishedYear extends SortField("published")
  case Relevance extends SortField("relevance")  // 検索用

object SortField:
  def fromString(s: String): Option[SortField] =
    SortField.values.find(_.apiName == s.toLowerCase)
```

### 5.2 ソートロジック

```scala
def sortBooks(books: List[BookSummary], spec: SortSpec): List[BookSummary] =
  val comparator: Ordering[BookSummary] = spec.field match
    case SortField.Title =>
      Ordering.by(_.title.toLowerCase)

    case SortField.Author =>
      Ordering.by(b => b.author.map(_.toLowerCase).getOrElse("\uFFFF"))

    case SortField.RegisteredAt =>
      Ordering.by(_.registeredAt.toEpochMilli)

    case SortField.PublishedYear =>
      Ordering.by(b => b.publishedYear.getOrElse(Int.MaxValue))

    case SortField.Relevance =>
      Ordering.by(_.score).reverse  // スコアは高い順がデフォルト

  val ordered = spec.direction match
    case SortDirection.Asc => comparator
    case SortDirection.Desc => comparator.reverse

  books.sorted(ordered)
```

### 5.3 日本語対応（オプション）

```scala
import java.text.Collator
import java.util.Locale

def sortBooksJapanese(books: List[BookSummary], spec: SortSpec): List[BookSummary] =
  val collator = Collator.getInstance(Locale.JAPANESE)

  spec.field match
    case SortField.Title =>
      val comparator = (a: BookSummary, b: BookSummary) =>
        collator.compare(a.title, b.title)
      if spec.direction == SortDirection.Desc then
        books.sortWith((a, b) => comparator(a, b) > 0)
      else
        books.sortWith((a, b) => comparator(a, b) < 0)

    case _ => sortBooks(books, spec)  // 他はデフォルト
```

---

## 6. API設計

### 6.1 ソートパラメータ（全API共通）

```
Query Parameters:
  - sort: ソートフィールド（title | author | registered | published | relevance）
  - order: ソート方向（asc | desc）

デフォルト:
  - 一覧/フィルタ: sort=title, order=asc
  - 検索: sort=relevance, order=desc
```

### 6.2 API例

**本の一覧（タイトル昇順）**:
```
GET /api/books?sort=title&order=asc
```

**本の一覧（登録日降順 = 新しい順）**:
```
GET /api/books?sort=registered&order=desc
```

**検索結果（関連度順、デフォルト）**:
```
GET /api/books/search?q=scala
```

**検索結果（タイトル順に変更）**:
```
GET /api/books/search?q=scala&sort=title&order=asc
```

**タグフィルタ（著者順）**:
```
GET /api/books/filter-by-tags?tags=programming&sort=author&order=asc
```

### 6.3 レスポンス例

```json
{
  "books": [
    {
      "id": "01ARZ3NDEKTSV4RRFFQ69G5FAV",
      "title": "Effective Java",
      "author": "Joshua Bloch",
      "publishedYear": 2018,
      "registeredAt": "2026-01-15T10:30:00Z"
    },
    {
      "id": "01ARZ3NDEKTSV4RRFFQ69G5FAW",
      "title": "プログラミングScala",
      "author": "Dean Wampler",
      "publishedYear": 2021,
      "registeredAt": "2026-01-20T14:00:00Z"
    }
  ],
  "total": 25,
  "sort": {
    "field": "title",
    "direction": "asc"
  }
}
```

---

## 7. 専門家最終コメント

### 田中博士（DDD）

> ソートはプレゼンテーション層の関心事であり、ドメインモデルには影響しません。クエリの拡張として実装するのが適切です。全画面共通のソートオプションにより、一貫したユーザー体験を提供できます。

### 佐藤教授（データベース）

> インメモリソートは数千件規模では十分高速です。日本語のCollatorソートはオプションとして実装し、パフォーマンスに問題があれば読みがなフィールドの追加を検討してください。

### 鈴木氏（Scala）

> `SortField`と`SortDirection`のenum設計により、型安全なソート指定が可能です。将来的に複合ソート（第1キー、第2キー）が必要になった場合も、`List[SortSpec]`への拡張で対応できます。

### 山田氏（分散システム）

> ソートは決定的であるべきです。同じクエリは常に同じ順序を返すよう、Null値の扱いと同値の場合のセカンダリソート（IDなど）を統一してください。

---

## 8. 実装タスク

### Phase 1: 共通型
1. [ ] `SortField` enumの作成
2. [ ] `SortDirection` enumの作成
3. [ ] `SortSpec` case classの作成
4. [ ] ソートロジック関数の実装

### Phase 2: クエリ拡張
5. [ ] `SearchQuery`に`sortBy`追加
6. [ ] `TagFilterQuery`に`sortBy`追加
7. [ ] `ListBooksQuery`の作成

### Phase 3: API拡張
8. [ ] 全エンドポイントに`sort`/`order`パラメータ追加
9. [ ] レスポンスにソート情報を含める

### Phase 4: テスト
10. [ ] ソートロジックの単体テスト
11. [ ] Null値の扱いテスト
12. [ ] APIパラメータのパーステスト

---

## 9. 修正対象ファイル

| ファイル | 変更内容 |
|---------|---------|
| `domain/.../SortSpec.scala` | 新規作成（ソート関連型） |
| `domain/.../BookSearch.scala` | SearchQueryにsortBy追加 |
| `domain/.../TagFilter.scala` | TagFilterQueryにsortBy追加 |
| `infrastructure/.../BookSearchProjection.scala` | ソートロジック追加 |
| `infrastructure/.../TagFilterProjection.scala` | ソートロジック追加 |
| `controller/.../BookEndpoints.scala` | sort/orderパラメータ追加 |
| `controller/.../BookSearchEndpoints.scala` | sort/orderパラメータ追加 |
| `controller/.../TagFilterEndpoints.scala` | sort/orderパラメータ追加 |

---

## 10. 本のリストソート イベントストーミング完了

### 成果物
- コマンド: 0個（Write Sideに変更なし）
- ドメインイベント: 0個
- クエリ拡張: 3個（既存クエリにsortByパラメータ追加）
- 共通型: 3個（SortField, SortDirection, SortSpec）

### 設計ポイント
- **表示ロジック**: ドメインモデルに影響なし
- **全画面共通**: 一貫したソートオプション
- **4フィールド対応**: タイトル、著者、登録日、出版年
- **昇順/降順**: 両方向対応

---

*イベントストーミング（本のリストソート）完了*
