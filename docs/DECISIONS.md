# 設計決定事項

議事録の圧縮版。詳細は `docs/YYYY-MM-DD-*.md` を参照。

---

## 1. BookId設計（2026-02-01）

| 項目 | 決定 |
|------|------|
| ID方式 | 完全ランダムULID（サロゲートキー） |
| 自然キー | `BookIdentifier`として分離（ISBN, arXiv, DOI, Title） |
| 生成方法 | `BookId.generate[F[_]: Sync]: F[BookId]` |
| 旧方式 | `ULIDConverter`は`@deprecated` |

## 2. 一意性保証（2026-02-01）

| 項目 | 決定 |
|------|------|
| 重複チェック層 | ドメインサービス + リポジトリ |
| インデックス構造 | `Map[NormalizedIdentifier, BookId]`（O(1)判定） |
| ISBN正規化 | ISBN-10 → ISBN-13に統一 |
| 競合状態対策 | アクターモデルで順次処理 |

## 3. BookIdentifier（2026-02-01）

```scala
sealed trait BookIdentifier
├── ISBN(isbn: ISBN)           // 10桁 or 13桁
├── Arxiv(arxivId: ArxivId)    // YYMM.NNNNN(vN)?
├── DOI(doi: DOI)              // 10.prefix/suffix
└── Title(title: NES)          // フォールバック
```

正規化形式: `"isbn:9784873115658"`, `"arxiv:2301.12345"`, `"doi:10.1038/nature12373"`

## 4. アーキテクチャ（2026-02-01）

**CQRS + Event Sourcing採用**

```
Write Side                      Read Side
───────────                     ─────────
BookshelfActor                  BookReadModel
├── ISBN Index (~200KB)         ├── books: Map[BookId, BookView]
└── 重複チェックのみ             ├── isbnIndex: Map
                                ├── tagIndex: Map
        ↓ Event Store ↓         └── titleIndex: Trie
              └──────────→ Projector ──→ ReadModel更新
```

## 5. インメモリ管理（2026-02-01）

| 規模 | 冊数 | メモリ | 判定 |
|------|------|--------|------|
| 個人 | ~5,000 | ~3MB | 問題なし |
| 図書館 | ~50,000 | ~30MB | 許容範囲 |

**推奨**: ISBNインデックス + LRUキャッシュのハイブリッド

## 6. 実装済み変更

- `BookId.scala`: ランダムULID生成
- `BookIdentifier.scala`: 4種類の識別子
- `Identifiers.scala`: ArxivId, DOI型（Iron制約）
- `BookRepository.scala`: `existsByIdentifier`, `findByIdentifier`
- `BookRegistrationService.scala`: 一意性チェック

## 7. イベントストーミング: 本の登録（2026-02-01）

### ビジネスルール
| ルール | 決定 |
|--------|------|
| メタデータ取得失敗 | 仮登録状態にする |
| 重複検出 | 形態違い（現物/電子書籍）なら許可 |
| 現物/電子書籍 | 同じ本の異なる形態として1つのBookに紐づけ |

### コマンド（9個）
`RegisterBook`, `ValidateIdentifier`, `CheckDuplicate`, `FetchBookMetadata`, `ConfirmRegistration`, `AddFormatToExistingBook`, `RegisterAsPending`, `EnterBookMetadata`, `CompleteBookRegistration`

### ドメインイベント（13個）
`BookRegistrationRequested`, `BookIdentifierValidated`, `BookIdentifierInvalid`, `DuplicateBookDetected`, `ExistingBookWithDifferentFormatDetected`, `BookMetadataFetched`, `BookMetadataFetchFailed`, `BookRegistered`, `BookPendingRegistered`, `BookFormatAdded`, `BookRegistrationRejected`, `BookMetadataEntered`, `BookRegistrationCompleted`

### 新しい値オブジェクト
```scala
enum BookFormat: Physical | Ebook | Audiobook | PDF

final case class OwnedFormat(format: BookFormat, location: StorageLocation)

sealed trait StorageLocation
  case class Bookshelf(name: NES)     // 現物
  case class Platform(name: NES)       // 電子書籍 (Kindle, Kobo等)
```

---

## 8. イベントストーミング: 本の複数登録（2026-02-01）

### ビジネスルール
| ルール | 決定 |
|--------|------|
| 部分失敗時 | 失敗行を保留して続行（後で手動修正可能） |
| 処理方式 | 非同期（バックグラウンドジョブ） |
| 重複時 | 形態追加を試みる（単一登録と同じ） |

### 新規集約: BulkImportJob
```
BulkImportJob
├── Status: Pending | Processing | Completed | Failed
├── Progress: {total, processed, succeeded, failed, held}
└── Items: List[ImportItem]
    └── status: Queued | Processing | Succeeded | Failed | Held | Skipped
```

### コマンド（7個）
`CreateBulkImportJob`, `StartBulkImportJob`, `ProcessImportItem`, `ResolveHeldItem`, `SkipHeldItem`, `CancelBulkImportJob`, `RetryFailedItems`

### ドメインイベント（11個）
`BulkImportJobCreated`, `BulkImportJobStarted`, `ImportItemProcessingStarted`, `ImportItemSucceeded`, `ImportItemFailed`, `ImportItemHeld`, `ImportItemResolved`, `ImportItemSkipped`, `BulkImportJobCompleted`, `BulkImportJobFailed`, `BulkImportJobCancelled`

### CSVフォーマット
```csv
identifier_type,identifier,format,location,title,on_duplicate
```

---

## 9. イベントストーミング: 本の削除（2026-02-01）

### ビジネスルール
| ルール | 決定 |
|--------|------|
| 削除の粒度 | 両方可能（形態単位 + 本全体） |
| 復元機能 | 30日間の猶予期間あり |
| 確認プロセス | ドメインは関与しない（UIの責務） |

### 状態遷移
```
Active → PendingDeletion → Deleted（30日後）
       ↖ CancelDeletion ↙
```

### コマンド（6個）
`RemoveBookFormat`, `RestoreBookFormat`, `RequestBookDeletion`, `CancelBookDeletion`, `ProcessExpiredDeletions`, `PurgeBook`

### ドメインイベント（7個）
`BookFormatRemoved`, `BookFormatRestored`, `LastFormatRemoved`, `BookDeletionRequested`, `BookDeletionCancelled`, `BookDeleted`, `BookPurged`

### 特記事項
- 最後の形態削除時 → 本削除を提案（`LastFormatRemoved`）
- 日次スケジューラで猶予期間満了チェック

---

## 10. イベントストーミング: タグ付け（2026-02-01）

### ビジネスルール
| ルール | 決定 |
|--------|------|
| 正規化 | 大文字小文字を区別しない（小文字に統一） |
| 削除ポリシー | 参照カウント0で自動削除 |
| タグ名制約 | 最大50文字、空白・特殊文字許可 |

### アーキテクチャ（CQRSベース）

```
Write Side                    Read Side
───────────                   ─────────
Book Aggregate                TagRegistry
├── AddBookTag                ├── getAllTags()
├── RemoveBookTag             ├── searchTags(query)
└── イベント発行              ├── getPopularTags(limit)
                              └── suggestTags(prefix)
        ↓ Event Store ↓
              └──────────→ TagRegistryProjector
                              ├── BookTagAdded → カウント増加
                              └── BookTagRemoved → カウント減少/削除
```

### 型定義
```scala
opaque type NormalizedTagName = String  // 正規化されたタグ名

final case class Tag(name: NormalizedTagName)

final case class TagInfo(
  tag: Tag,
  bookCount: Int,
  lastUsedAt: Timestamp
)
```

### コマンド（2個、既存）
`AddBookTag`, `RemoveBookTag` - バリデーション追加

### クエリ（4個、新規）
`GetAllTags`, `SearchTags`, `GetPopularTags`, `SuggestTags`

### 新規API
```
GET /api/tags           - タグ一覧
GET /api/tags/suggest   - タグサジェスト
```

---

## 11. イベントストーミング: 本の検索（2026-02-01）

### ビジネスルール
| ルール | 決定 |
|--------|------|
| 著者情報管理 | ハイブリッド（自動取得 + 手動編集） |
| 検索レベル | Standard（複数フィールド、部分一致、AND/OR） |
| 検索対象 | タイトル、著者、タグ、出版社・ISBN |

### アーキテクチャ

```
Write Side                    Read Side
───────────                   ─────────
Book Aggregate                BookSearchProjection
├── UpdateBookMetadata        ├── search(query)
└── イベント発行              ├── getById(bookId)
                              └── getAll(limit, offset)
        ↓ Event Store ↓
              └──────────→ 転置インデックス
                              ├── titleIndex
                              ├── authorIndex
                              └── tagIndex
```

### 新規値オブジェクト
```scala
final case class BookMetadata(
  author: Option[NES],
  publisher: Option[NES],
  publishedYear: Option[Int],
  description: Option[String]
)

enum MetadataSource:
  case NDL, OpenLibrary, GoogleBooks, Manual
```

### コマンド（1個、新規）
`UpdateBookMetadata` - メタデータの手動更新

### ドメインイベント（2個、新規）
`BookMetadataFetched`, `BookMetadataUpdated`

### クエリ（3個、新規）
`SearchBooks`, `GetBookById`, `GetAllBooks`

### 新規API
```
GET /api/books/search         - 本の検索
PUT /api/books/{id}/metadata  - メタデータ更新
```

### 検索機能
| 機能 | サポート |
|------|---------|
| 複数フィールド検索 | Yes |
| 部分一致 | Yes |
| AND/OR組み合わせ | Yes |
| スコアリング | シンプルな関連度のみ |

---

## 12. イベントストーミング: タグフィルタリング（2026-02-01）

### ビジネスルール
| ルール | 決定 |
|--------|------|
| 複数タグ組み合わせ | AND/OR両方選択可能（UI切り替え） |
| 検索との関係 | 独立した機能（別画面） |

### アーキテクチャ

```
Read Model専用（Write Sideに変更なし）

TagFilterProjection
├── tagIndex: Map[TagName, Set[BookId]]
└── filterByTags(query): TagFilterResult
    ├── AND: tags.map(tagIndex).reduce(_ & _)
    └── OR:  tags.map(tagIndex).reduce(_ | _)
```

### クエリ関連型
```scala
final case class TagFilterQuery(
  tags: Set[NormalizedTagName],
  operator: TagFilterOperator,  // And | Or
  limit: Int,
  offset: Int,
  sortBy: BookSortField
)

final case class TagFilterResult(
  books: List[BookSummary],
  total: Int,
  appliedTags: Set[NormalizedTagName],
  operator: TagFilterOperator
)
```

### コマンド
なし（Write Sideに変更なし）

### ドメインイベント
なし（既存のBookTagAdded/Removedを流用）

### クエリ（1個、新規）
`FilterBooksByTags` - 複数タグでフィルタリング

### 新規API
```
GET /api/books/filter-by-tags?tags=scala,programming&operator=and
```

---

## 13. イベントストーミング: 本のリストソート（2026-02-01）

### ビジネスルール
| ルール | 決定 |
|--------|------|
| ソート項目 | タイトル、著者、登録日、出版年 |
| 適用範囲 | 全画面共通（検索、フィルタ、一覧すべて） |
| ソート方向 | 昇順/降順 両対応 |

### 型定義
```scala
enum SortField:
  case Title, Author, RegisteredAt, PublishedYear, Relevance

enum SortDirection:
  case Asc, Desc

final case class SortSpec(field: SortField, direction: SortDirection)
```

### デフォルトソート
| 画面 | デフォルト |
|------|-----------|
| 一覧/フィルタ | タイトル昇順 |
| 検索結果 | 関連度降順 |

### コマンド/イベント
なし（表示ロジックのみ）

### クエリ拡張
既存の`SearchQuery`, `TagFilterQuery`に`sortBy: SortSpec`パラメータ追加

### APIパラメータ
```
?sort=title|author|registered|published|relevance
&order=asc|desc
```

---

## 14. イベントストーミング: 本の詳細表示（2026-02-01）

### 表示項目
| カテゴリ | 項目 |
|---------|------|
| 基本情報 | タイトル、著者、出版社 |
| 識別情報 | ISBN/識別子、出版年 |
| 管理情報 | タグ、場所、デバイス |
| システム情報 | 登録日、更新日 |

### 型定義
```scala
final case class BookDetailView(
  id: BookId,
  title: NES,
  author: Option[NES],
  publisher: Option[NES],
  identifier: Option[BookIdentifier],
  publishedYear: Option[Int],
  tags: List[Tag],
  location: Option[Location],
  devices: Set[Device],
  registeredAt: Timestamp,
  lastModifiedAt: Timestamp,
  isDeleted: Boolean
)
```

### コマンド/イベント
なし（参照のみ）

### クエリ（1個、新規）
`GetBookDetail` - IDで本の詳細取得

### API
```
GET /api/books/{bookId}
```

### 必要な拡張
- BookViewProjectionに`registeredAt`フィールド追加

---

## 15. イベントストーミング: 著者・出版社フィルタリング（2026-02-01）

### ビジネスルール
| ルール | 決定 |
|--------|------|
| マッチング | 完全一致（正規化後） |
| 操作方法 | 詳細画面から著者名/出版社名をクリック |
| 一覧表示 | 不要（リンククリックのみ） |

### 正規化ルール
- 大文字→小文字
- 前後空白トリム
- 連続空白→単一空白

### Read Model拡張
```scala
authorIndex: Map[NormalizedAuthor, Set[BookId]]
publisherIndex: Map[NormalizedPublisher, Set[BookId]]
```

### コマンド/イベント
なし（参照のみ）

### クエリ（2個、新規）
`FilterBooksByAuthor`, `FilterBooksByPublisher`

### APIパラメータ
```
GET /api/books?author=Dean%20Wampler
GET /api/books?publisher=オライリー・ジャパン
```

---

## 16. イベントストーミング: 電子書籍ディープリンク（2026-02-01）

### ビジネスルール
| ルール | 決定 |
|--------|------|
| 複数リンク | 1冊の本に複数プラットフォームのリンクを保存可能 |
| 登録方法 | 手動入力（将来的に自動生成を検討） |

### 型定義
```scala
enum EbookPlatformType:
  case Kindle, Kobo, GooglePlayBooks, AppleBooks, Other(name: NES)

sealed trait EbookDeepLink:
  def url: String
  def platformType: EbookPlatformType

// BookAggregateに追加
ebookLinks: Set[EbookDeepLink]
```

### プラットフォーム別リンク形式
| プラットフォーム | リンク形式 |
|-----------------|-----------|
| Kindle | `kindle://book?action=open&asin=XXX` |
| Google Play Books | `https://play.google.com/store/books/details?id=XXX` |
| Kobo | `https://www.kobo.com/ebook/XXX` |
| Apple Books | `itms-books://apple.co/book/idXXX` |

### コマンド（3個、新規）
`AddEbookDeepLink`, `UpdateEbookDeepLink`, `RemoveEbookDeepLink`

### ドメインイベント（3個、新規）
`EbookDeepLinkAdded`, `EbookDeepLinkUpdated`, `EbookDeepLinkRemoved`

### 新規API
```
POST   /api/books/{bookId}/ebook-links
GET    /api/books/{bookId}/ebook-links
PUT    /api/books/{bookId}/ebook-links/{platform}
DELETE /api/books/{bookId}/ebook-links/{platform}
```

---

## 17. 未実装課題

- [ ] BookshelfActor実装
- [ ] BookshelfState（デュアルインデックス）
- [ ] Projector実装
- [ ] infrastructure/adopter/usecase/controller更新
- [ ] BookFormat, OwnedFormat, StorageLocation実装
- [ ] 仮登録→本登録の状態遷移実装
- [ ] BulkImportJob集約実装
- [ ] CSVパーサー実装
- [ ] NormalizedTagName型実装
- [ ] TagRegistry実装
- [ ] タグAPI実装（一覧、サジェスト）
- [ ] BookMetadata値オブジェクト実装
- [ ] BookSearchProjection実装
- [ ] 検索API実装
- [ ] TagFilterProjection実装
- [ ] タグフィルタリングAPI実装
- [ ] SortSpec型実装
- [ ] ソートロジック実装
- [ ] 各APIにソートパラメータ追加
- [ ] BookDetailView実装
- [ ] registeredAtフィールド追加
- [ ] 詳細取得API実装
- [ ] NormalizedAuthor/NormalizedPublisher型実装
- [ ] authorIndex/publisherIndex実装
- [ ] 著者・出版社フィルタAPI実装
- [ ] EbookDeepLink型実装
- [ ] ASIN, GoogleBooksId等の型実装
- [ ] 電子書籍ディープリンクAPI実装
