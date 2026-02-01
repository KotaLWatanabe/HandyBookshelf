# 書籍一意性設計に関する専門家パネルディスカッション議事録

**日付**: 2026-02-01
**参加者**:
- 田中博士（DDDの専門家）- ドメイン駆動設計コンサルタント、15年の経験
- 佐藤教授（UUIDの専門家）- 分散システムにおける識別子設計の研究者
- 鈴木氏（関数型プログラミングの専門家）- Scala/Haskellの実務家、型安全性の追求者

**議題**: 書籍管理システムにおける一意性保証の設計

---

## 第1回ディスカッション：ドメインモデル設計

### 1. 現状分析

#### 1.1 既存のBookId生成方式

**田中博士（DDD）**：
現在の`BookId`は`ULID`をラップしており、`ULIDConverter`で生成されています。

```scala
// BookId.scala:13-18
def create(bookCode: NES, timestamp: Timestamp): BookId =
  BookId(ULIDConverter.createULID(bookCode, timestamp))

def createFromISBN(isbn: ISBN, timestamp: Timestamp): BookId =
  BookId(ULIDConverter.createULIDFromISBN(isbn, timestamp))
```

**佐藤教授（UUID）**：
`ULIDConverter.scala`の設計意図を分析すると：

```scala
// ULIDConverter.scala:10-17
def createULIDFromISBN(isbn: ISBN, timestamp: Timestamp): ULID = {
  val isbnBytes  = isbn.getBytes(StandardCharsets.UTF_8).take(10)
  val randomPart = isbnBytes.padTo(10, 0.toByte)
  ULID.fromBytes(timestamp.toBytes ++ randomPart)
}
```

ULIDの構造（48bit timestamp + 80bit random）を活用し、**randomの80bit（10バイト）にISBNを埋め込んでいる**。

特徴：
- 同じISBN + 同じタイムスタンプ（ミリ秒精度）= 同じBookId
- ISBNから逆引きも可能（`extractISBNFromULID`）

**鈴木氏（FP）**：
型安全性の観点から、`ISBN`はIronで制約されている：

```scala
// ISBN.scala:7-11
type ISBN = String :| DescribedAs[
  ForAll[Digit] & Xor[FixedLength[10], FixedLength[13]],
  "ISBN must be 10 or 13 digits."
]
```

コンパイル時に10桁または13桁の数字のみを保証。

---

### 2. 発見された落とし穴

#### 2.1 タイムスタンプ依存の衝突問題

**佐藤教授（UUID）**：

```scala
// ULIDConverter.scala:19-28
def createULID(bookCode: NES, timestamp: Timestamp): ULID =
  bookCode.isbnOpt match {
    case Some(isbn) => createULIDFromISBN(isbn, timestamp)
    case None =>
      val randomPart = Array.fill[Byte](10)(0.toByte)  // ← 危険！
      ULID.fromBytes(timestamp.toBytes ++ randomPart)
  }
```

**問題**: ISBNがない場合、randomPartが全てゼロになる。同じミリ秒内に複数の非ISBN書籍を登録すると、**完全に同じBookIdが生成される**。

#### 2.2 集約境界での一意性保証の欠如

**田中博士（DDD）**：

```scala
// BookAggregate.scala:83-94
def register(isbn: Option[ISBN], title: NES): IO[BookAggregate] =
  IO(Timestamp.now).map(timestamp =>
    val event = BookRegistered(...)
    applyEvent(event)
  )
```

アグリゲート内部では重複チェックがない。DDDの原則では、**ビジネス不変条件はドメイン層で保証すべき**。

#### 2.3 副作用の隠蔽

**鈴木氏（FP）**：

```scala
// Book.scala:17-18
def generate[R: _eval](isbnStr: NES, title: NES): Eff[R, Book] =
  TimestampGenerator.now.map(timestamp => Book(BookId.create(isbnStr, timestamp), title))
```

`Eff`モナドで副作用を管理しているが、`BookId.create`は**純粋関数ではない**（同じ入力でも時刻により異なる結果）。参照透過性の破壊。

#### 2.4 二つの「Book」概念の混在

**田中博士（DDD）**：

```scala
final case class Book private (id: BookId, title: NES)
final case class BookAggregate(bookId: BookId, title: Option[NES], ...)
```

`Book`（値オブジェクト的）と`BookAggregate`（エンティティ/集約ルート）が並存。ユビキタス言語の混乱を招く。

#### 2.5 ISBNの「同一性」の曖昧さ

**佐藤教授（UUID）**：

- ISBN-10とISBN-13は同じ本を指すことがある
- 改訂版は異なるISBNを持つ
- 同じISBNで異なるフォーマット（Paper/Ebook）がある

現在の設計では、**同じISBN + 同じタイムスタンプで登録すると同一BookIdになる**が、意図的な設計なのか不明。

---

### 3. 解決策

#### 3.1 自然キーとサロゲートキーの分離

**鈴木氏（FP）**：

```scala
// 自然キー（ビジネス識別子）とサロゲートキー（技術的識別子）の分離
final case class BookId private (value: ULID)  // サロゲートキー（常に一意）

// 自然キーは別の値オブジェクトとして定義
sealed trait BookIdentifier
case class ISBNIdentifier(isbn: ISBN) extends BookIdentifier
case class CustomIdentifier(code: NES) extends BookIdentifier
```

#### 3.2 ドメイン層での一意性保証

**田中博士（DDD）**：

```scala
trait BookUniquenessChecker[F[_]]:
  def exists(identifier: BookIdentifier): F[Boolean]
  def findByISBN(isbn: ISBN): F[Option[BookAggregate]]

class RegisterBookService[F[_]: Monad](
    checker: BookUniquenessChecker[F],
    idGenerator: BookIdGenerator[F]
):
  def register(identifier: BookIdentifier, title: NES): F[Either[DuplicateBookError, BookAggregate]] =
    for {
      exists <- checker.exists(identifier)
      result <- if (exists)
                  DuplicateBookError(identifier).asLeft.pure[F]
                else
                  idGenerator.generate.map(id => BookAggregate.empty(id).register(...))
    } yield result
```

#### 3.3 完全ランダムULID

**佐藤教授（UUID）**：

```scala
object BookIdGenerator:
  def generate[F[_]: Sync]: F[BookId] =
    Sync[F].delay(BookId(ULID.newULID))
```

理由：
1. **衝突確率**: ULIDの80bitランダム部分は十分に広い（2^80 ≈ 10^24）
2. **関心の分離**: IDはあくまで識別子、ISBNはビジネス属性
3. **可逆性不要**: IDからISBNを逆引きする必要があるなら、それは別の責務

#### 3.4 最終合意案

```scala
// 1. BookIdは純粋なサロゲートキー
final case class BookId private (value: ULID)
object BookId:
  def generate[F[_]: Sync]: F[BookId] = Sync[F].delay(BookId(ULID.newULID))

// 2. 自然キーはアグリゲート内の属性として保持
final case class BookAggregate(
    bookId: BookId,
    isbn: Option[ISBN],           // 自然キー（検索用インデックス）
    title: NES,
    // ...
)

// 3. 一意性制約はリポジトリインターフェースで明示
trait BookRepository[F[_]]:
  def save(book: BookAggregate): F[Either[DuplicateISBNError, Unit]]
  def findByISBN(isbn: ISBN): F[Option[BookAggregate]]

// 4. ドメインサービスで整合性を担保
class BookRegistrationService[F[_]: MonadError[*[_], Throwable]](
    repo: BookRepository[F]
):
  def register(isbn: Option[ISBN], title: NES): F[BookAggregate] = ...
```

---

### 4. 第1回まとめ

| 問題点 | 現在の設計 | 提案する解決策 |
|--------|----------|--------------|
| 非ISBN書籍の衝突 | 同一ミリ秒で同じID | 完全ランダムULID |
| 一意性保証の層 | 不明確（インフラ依存?） | ドメインサービス + リポジトリ |
| ISBNとIDの結合 | ULIDにISBNを埋め込み | 分離（ISBNは属性として保持） |
| 型の混乱 | Book と BookAggregate | BookAggregateに統一 |
| 同一ISBN判定 | ISBN-10/13の区別なし | ISBN正規化または両方保持 |

---

## 第2回ディスカッション：ステートマシンによるDBレス重複判定

### 1. 要件

- すべての本情報をステートマシン（Pekkoアクター）に保持
- DBアクセスなしで重複判定を行いたい

### 2. 現状分析

**田中博士（DDD）**：
`Bookshelf`は`Map[BookId, (Filters, BookReference)]`で書籍を保持：

```scala
// Bookshelf.scala:38-44
final case class Bookshelf(
    private val _books: Map[BookId, (Filters, BookReference)],
    private val _sorter: BookSorter
)
```

問題: **BookIdをキーにしている**ため、ISBNで重複チェックするには全件走査が必要。

**佐藤教授（UUID）**：
現在の構造での重複チェック：
```scala
def existsByISBN(isbn: ISBN): Boolean =
  _books.values.exists { case (_, ref) => ref.book.isbn.contains(isbn) }
```
**O(n)の計算量**。書籍が1万冊あれば1万回の比較が必要。

**鈴木氏（FP）**：
`UserStateActor`のパターンが参考になる：
```scala
// UserStateActor.scala:48
active(Map.empty[UserAccountId, UserState])
```
アクターの状態として`Map`を持ち、メッセージで更新する。

---

### 3. インメモリ重複判定の設計パターン

#### 3.1 デュアルインデックス構造

**田中博士（DDD）**：
DDDの観点では、**2つのインデックスを持つ**のが定石：

```
┌─────────────────────────────────────────────────────┐
│                  BookshelfState                      │
├─────────────────────────────────────────────────────┤
│  Primary Index:    Map[BookId, BookAggregate]       │
│  Secondary Index:  Map[ISBN, BookId]    ← 追加！    │
└─────────────────────────────────────────────────────┘
```

効果：
- **登録時**: ISBNインデックスでO(1)で重複チェック
- **更新時**: BookIdで直接アクセス

#### 3.2 ISBN正規化

**佐藤教授（UUID）**：
ISBN-10とISBN-13の正規化が必要：

```
ISBN-10: 4873115655
ISBN-13: 9784873115658
→ 同じ書籍を指す
```

インデックスキーには正規化したISBNを使うべき。

#### 3.3 不変データ構造での整合性

**鈴木氏（FP）**：
問題は**整合性**。両方のMapを同時に更新しないと不整合が起きる：

```scala
// 悪い例：2つの操作が分離している
val newPrimary   = primary + (bookId -> book)
val newSecondary = secondary + (isbn -> bookId)  // ここで例外が起きたら？
```

---

### 4. 発見された落とし穴

#### 4.1 Race Condition（競合状態）

**田中博士（DDD）**：

```
Thread A: ISBN存在チェック → なし → 登録開始
Thread B: ISBN存在チェック → なし → 登録開始  ← 同じISBN！
Thread A: 登録完了
Thread B: 登録完了 ← 重複発生！
```

**解決策**: アクターモデルでは**メッセージは順次処理**されるので回避可能。

#### 4.2 メモリ消費

**佐藤教授（UUID）**：

```
1冊あたり: BookAggregate(約500バイト) + ISBN索引(約50バイト)
10万冊: 約55MB
100万冊: 約550MB
```

個人の蔵書なら問題ないが、スケールには注意。

#### 4.3 永続化との同期

**鈴木氏（FP）**：

```scala
// アクター再起動時
def recover(): Behavior[Command] =
  Behaviors.setup { context =>
    val allBooks = loadFromDB()  // ← ここでDBアクセスが必要
    active(buildIndices(allBooks))
  }
```

「DBアクセスなし」といっても、**起動時の状態復元**は必要。

---

### 5. 解決策

#### 5.1 アーキテクチャ

```
                    ┌──────────────────────────────────┐
                    │       BookshelfActor             │
                    │  ┌────────────────────────────┐  │
    RegisterBook    │  │     BookshelfState         │  │
   ──────────────►  │  │                            │  │
                    │  │  books: Map[BookId, Book]  │  │
    CheckDuplicate  │  │  isbnIndex: Map[ISBN, Id]  │  │
   ──────────────►  │  │  titleIndex: Map[NES, Id]  │  │
                    │  └────────────────────────────┘  │
                    └──────────────────────────────────┘
                                    │
                                    ▼ (非同期で永続化)
                              ┌──────────┐
                              │ EventLog │
                              └──────────┘
```

#### 5.2 状態モデル

**田中博士（DDD）**：

```scala
final case class BookshelfState(
  // Primary storage
  books: Map[BookId, BookAggregate],

  // Secondary indices for O(1) lookup
  isbnIndex: Map[NormalizedISBN, BookId],
  titleIndex: Map[NES, Set[BookId]],  // タイトルは重複許可

  // Metadata
  totalCount: Long,
  lastUpdated: Timestamp
)
```

#### 5.3 関数型での整合性保証

**鈴木氏（FP）**：

```scala
object BookshelfState:
  def addBook(
    state: BookshelfState,
    book: BookAggregate
  ): Either[DuplicateBookError, BookshelfState] =
    book.isbn match
      case Some(isbn) =>
        val normalized = NormalizedISBN(isbn)
        state.isbnIndex.get(normalized) match
          case Some(existingId) =>
            Left(DuplicateBookError(isbn, Some(existingId)))
          case None =>
            Right(state.copy(
              books = state.books + (book.bookId -> book),
              isbnIndex = state.isbnIndex + (normalized -> book.bookId),
              titleIndex = updateTitleIndex(state.titleIndex, book)
            ))
      case None =>
        // ISBNなしは常に許可（タイトル重複は警告のみ）
        Right(state.copy(
          books = state.books + (book.bookId -> book),
          titleIndex = updateTitleIndex(state.titleIndex, book)
        ))
```

#### 5.4 ISBN正規化

**佐藤教授（UUID）**：

```scala
opaque type NormalizedISBN = String

object NormalizedISBN:
  def apply(isbn: ISBN): NormalizedISBN =
    val digits = isbn.filter(_.isDigit)
    if digits.length == 10 then
      convertISBN10to13(digits)
    else
      digits

  private def convertISBN10to13(isbn10: String): String =
    val prefix = "978" + isbn10.take(9)
    val checkDigit = calculateCheckDigit(prefix)
    prefix + checkDigit
```

#### 5.5 Pekkoアクター実装

```scala
object BookshelfActor:
  // Messages
  sealed trait BookshelfCommand

  final case class RegisterBook(
    isbn: Option[ISBN],
    title: NES,
    replyTo: ActorRef[RegisterResult]
  ) extends BookshelfCommand

  final case class CheckDuplicate(
    isbn: ISBN,
    replyTo: ActorRef[DuplicateCheckResult]
  ) extends BookshelfCommand

  final case class GetBook(
    bookId: BookId,
    replyTo: ActorRef[Option[BookAggregate]]
  ) extends BookshelfCommand

  final case class FindByISBN(
    isbn: ISBN,
    replyTo: ActorRef[Option[BookAggregate]]
  ) extends BookshelfCommand

  // Results
  sealed trait RegisterResult
  case class RegisterSuccess(book: BookAggregate) extends RegisterResult
  case class RegisterFailure(error: DuplicateBookError) extends RegisterResult

  sealed trait DuplicateCheckResult
  case class IsDuplicate(existingBookId: BookId) extends DuplicateCheckResult
  case object NotDuplicate extends DuplicateCheckResult

  def apply(initial: BookshelfState): Behavior[BookshelfCommand] =
    Behaviors.setup { context =>
      context.log.info(s"BookshelfActor started with ${initial.books.size} books")
      active(initial)
    }

  private def active(state: BookshelfState): Behavior[BookshelfCommand] =
    Behaviors.receive { (context, message) =>
      message match
        case RegisterBook(isbn, title, replyTo) =>
          val newBookId = BookId.fromString(ULID.newULID.toString)
          val newBook = BookAggregate.empty(newBookId).copy(
            isbn = isbn,
            title = Some(title)
          )

          BookshelfState.addBook(state, newBook) match
            case Right(newState) =>
              context.log.info(s"Book registered: $newBookId")
              replyTo ! RegisterSuccess(newBook)
              active(newState)
            case Left(error) =>
              context.log.warn(s"Duplicate ISBN: ${error.isbn}")
              replyTo ! RegisterFailure(error)
              Behaviors.same

        case CheckDuplicate(isbn, replyTo) =>
          val normalized = NormalizedISBN(isbn)
          state.isbnIndex.get(normalized) match
            case Some(bookId) => replyTo ! IsDuplicate(bookId)
            case None         => replyTo ! NotDuplicate
          Behaviors.same

        case GetBook(bookId, replyTo) =>
          replyTo ! state.books.get(bookId)
          Behaviors.same

        case FindByISBN(isbn, replyTo) =>
          val normalized = NormalizedISBN(isbn)
          val result = for {
            bookId <- state.isbnIndex.get(normalized)
            book   <- state.books.get(bookId)
          } yield book
          replyTo ! result
          Behaviors.same
    }
```

---

### 6. 第2回まとめ

| 観点 | 解決策 |
|------|--------|
| **O(1)重複判定** | `Map[NormalizedISBN, BookId]`のセカンダリインデックス |
| **ISBN-10/13統一** | `NormalizedISBN` opaque type で正規化 |
| **整合性保証** | `Either`で成功/失敗を表現、全インデックス同時更新 |
| **Race Condition防止** | アクターモデルの順次メッセージ処理 |
| **状態復元** | 起動時にEventLogから再構築（1回だけDBアクセス） |

---

## 実装済みの変更

### 変更したファイル

1. **`domain/src/main/scala/com/handybookshelf/domain/BookId.scala`**
   - `create` / `createFromISBN` メソッドを削除
   - `generate[F[_]: Sync]: F[BookId]` を追加（完全ランダムULID）
   - `fromString` メソッドを追加

2. **`domain/src/main/scala/com/handybookshelf/domain/repositories/BookRepository.scala`**
   - `BookRepositoryError` 型を追加
   - `saveWithUniquenessCheck` メソッドを追加
   - `existsByISBN` メソッドを追加
   - `findByISBN` を `Option[BookAggregate]` に変更

3. **`domain/src/main/scala/com/handybookshelf/domain/DomainError.scala`**
   - `DuplicateBookError` を追加
   - `BookNotFoundError` を追加
   - `BookAlreadyDeletedError` を追加

4. **`domain/src/main/scala/com/handybookshelf/domain/Book.scala`**
   - ISBNフィールドを追加
   - `fromAggregate` メソッドを追加
   - 旧 `generate` / `generateFromISBN` を削除

5. **`domain/src/main/scala/com/handybookshelf/domain/BookCommands.scala`**
   - `BookCommandHandler.handle` の戻り値を `IO[Either[DomainError, List[BookEvent]]]` に変更
   - 既存集約の読み込みとエラーハンドリングを追加

6. **`util/src/main/scala/com/handybookshelf/util/ULIDConverter.scala`**
   - `@deprecated` アノテーションを追加

### 新規作成したファイル

7. **`domain/src/main/scala/com/handybookshelf/domain/services/BookRegistrationService.scala`**
   - 一意性チェックを担当するドメインサービス

8. **`domain/src/main/scala/com/handybookshelf/domain/BookIdentifier.scala`**
   - 自然キー（ビジネス識別子）を表す型
   - ISBN-10 ⇔ ISBN-13 正規化関数

---

## 今後の課題

1. **BookshelfActorの実装**: 上記の設計に基づいたアクター実装
2. **BookshelfStateの実装**: デュアルインデックス構造の状態クラス
3. **NormalizedISBNの実装**: opaque typeによるISBN正規化
4. **永続化との統合**: イベントソーシングとの連携
5. **テストの追加**: 重複判定のプロパティベーステスト

---

## 第3回ディスカッション：インメモリ状態管理の妥当性と代替案

### 1. 議題

- レスポンシブなシステムを作成したい
- 本の状態をインメモリで持つのは過剰か
- 代替案はあるか

### 2. 「レスポンシブ」の定義

**田中博士（DDD）**：
Reactive Manifestoでは4つの特性がある：
- Responsive（即応性）
- Resilient（耐障害性）
- Elastic（弾力性）
- Message Driven

個人の書籍管理では、**即応性（低レイテンシ）**が主眼。

### 3. 規模感の分析

**佐藤教授（UUID）**：

| 規模 | 冊数 | インメモリサイズ | 判定 |
|------|------|-----------------|------|
| 読書家 | 500冊 | 約300KB | 全く問題なし |
| コレクター | 5,000冊 | 約3MB | 問題なし |
| 図書館レベル | 50,000冊 | 約30MB | まだ許容範囲 |
| 大学図書館 | 500,000冊 | 約300MB | 要検討 |

個人利用なら5,000冊でも3MB程度。現代のサーバーなら誤差レベル。

### 4. インメモリの本当のコスト

**鈴木氏（FP）**：
メモリ使用量より深刻な問題：

1. **起動時間**: 全データロードに数秒かかる
2. **永続化**: アクタークラッシュ時にデータ消失リスク
3. **複数インスタンス**: スケールアウト時に状態の整合性が崩壊

### 5. 代替案の比較

#### 案1: インデックスのみインメモリ

```scala
final case class LightweightState(
  isbnIndex: Set[NormalizedISBN],  // 重複チェック用のみ
  // 詳細データはDBから取得
)
```

- メモリ使用量: 5,000冊で約200KB
- 重複チェックはO(1)
- 検索時にはDBアクセスが必要

#### 案2: CQRS + Read Model

- 書き込みと読み取りを独立して最適化
- Read Modelは用途に応じて複数作成可能
- 複雑性が増す、結果整合性

#### 案3: キャッシュ層（Redis等）

- 複数インスタンスで共有可能
- 永続化オプションあり
- 追加のインフラが必要

#### 案4: Hybrid Approach（推奨）

```scala
final case class BookshelfCache(
  recentBooks: LRUCache[BookId, BookAggregate],  // 直近100冊
  isbnIndex: Map[NormalizedISBN, BookId],        // 重複チェック用
)
```

アクセスパターン：
1. キャッシュヒット → 即座に返却（<1ms）
2. キャッシュミス → DBから取得してキャッシュに追加（<10ms）
3. 重複チェック → 常にインメモリインデックス（<1ms）

### 6. 推奨アーキテクチャ

```
┌────────────────────────────────────────────────────────────┐
│                    BookshelfActor                          │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                  BookshelfCache                       │  │
│  │  ┌─────────────────┐  ┌─────────────────────────────┐│  │
│  │  │  ISBN Index     │  │  LRU Cache (Hot Books)     ││  │
│  │  │  Set[ISBN]      │  │  Map[BookId, BookAggregate]││  │
│  │  │  ~200KB         │  │  最大100冊, ~50KB          ││  │
│  │  └─────────────────┘  └─────────────────────────────┘│  │
│  └──────────────────────────────────────────────────────┘  │
│                            │ cache miss                     │
│                            ▼                                │
│                    ┌───────────────┐                        │
│                    │  Repository   │                        │
│                    └───────────────┘                        │
└────────────────────────────────────────────────────────────┘
```

### 7. 実装案

```scala
trait BookCache[F[_]]:
  def get(bookId: BookId): F[Option[BookAggregate]]
  def existsByISBN(isbn: ISBN): F[Boolean]
  def put(book: BookAggregate): F[Unit]
  def stats: F[CacheStats]

final case class CacheStats(
  hitRate: Double,
  size: Int,
  indexSize: Int
)

class LRUBookCache[F[_]: Sync](
  repository: BookRepository,
  maxSize: Int = 100
) extends BookCache[F]
```

### 8. レスポンス時間比較

| 操作 | 全インメモリ | ハイブリッド | DBのみ |
|------|------------|-------------|--------|
| 重複チェック | <1ms | <1ms | 5-20ms |
| 書籍取得（Hot） | <1ms | <1ms | 5-20ms |
| 書籍取得（Cold） | <1ms | 5-20ms | 5-20ms |
| 検索 | <5ms | 10-50ms | 10-50ms |
| 起動時間 | 数秒 | <100ms | <10ms |

### 9. 結論

| 質問 | 回答 |
|------|------|
| 全書籍インメモリは過剰か？ | 個人利用（〜5,000冊）なら**過剰ではない** |
| ただし考慮すべき点 | 起動時間、永続化、スケールアウト |
| 推奨アプローチ | **ISBNインデックス + LRUキャッシュ**のハイブリッド |

### 10. 実用的な推奨

1. まずは**シンプルにインメモリ**で始める（YAGNI原則）
2. 性能問題が出たら**LRUキャッシュ**に移行
3. スケールアウトが必要なら**Redis**を検討

個人の蔵書管理なら、最初のアプローチで十分。

---

## 第4回ディスカッション：CQRSアーキテクチャの設計

### 1. 議題

- CQRSを規模に関わらず採用したい
- イベントソーシングと組み合わせたアーキテクチャ設計

### 2. CQRS + Event Sourcing 基本構造

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         CQRS + Event Sourcing                            │
│                                                                          │
│   ┌─────────────┐                           ┌─────────────────────┐     │
│   │   Client    │                           │      Client         │     │
│   └──────┬──────┘                           └──────────┬──────────┘     │
│          │                                             │                 │
│          ▼                                             ▼                 │
│   ┌─────────────┐                           ┌─────────────────────┐     │
│   │  Command    │                           │       Query         │     │
│   │    API      │                           │        API          │     │
│   └──────┬──────┘                           └──────────┬──────────┘     │
│          │                                             │                 │
│   ┌──────▼──────┐                           ┌──────────▼──────────┐     │
│   │  Command    │                           │    Query Service    │     │
│   │  Handler    │                           │   (Read Optimized)  │     │
│   └──────┬──────┘                           └──────────┬──────────┘     │
│          │                                             │                 │
│   ┌──────▼──────┐                           ┌──────────▼──────────┐     │
│   │ Aggregate   │                           │     Read Model      │     │
│   │  (Domain)   │                           │    (Projection)     │     │
│   └──────┬──────┘                           └──────────▲──────────┘     │
│          │                                             │                 │
│          ▼                                             │                 │
│   ┌─────────────┐       ┌─────────────┐      ┌────────┴────────┐        │
│   │   Event     │──────►│  Projector  │─────►│   Read Store    │        │
│   │   Store     │       │             │      │  (Optimized DB) │        │
│   └─────────────┘       └─────────────┘      └─────────────────┘        │
│                                                                          │
│   ◄───────── Write Side ─────────►  ◄───────── Read Side ─────────►     │
└─────────────────────────────────────────────────────────────────────────┘
```

### 3. データフロー

#### Write Flow（書き込み）

1. Client: POST /books { isbn, title }
2. CommandEndpoint: RegisterBookCommand を生成
3. BookCommandHandler: BookshelfActor に Ask、ISBNインデックスで重複チェック
4. BookAggregate.register(): BookRegistered イベント生成
5. EventStore.append(): イベントを永続化
6. Projector (非同期): イベントを購読、ReadModel を更新
7. Response: { bookId, status: "created" }

#### Read Flow（読み取り）

1. Client: GET /books/search?tag=技術書
2. QueryEndpoint: BookQuery を生成
3. BookQueryService: ReadModel のインデックスを検索（<1ms）
4. Response: [{ bookId, title, tags }, ...]

### 4. 重複チェックの配置

| Option | 方式 | 利点 | 欠点 |
|--------|------|------|------|
| A | Command Side | 強い整合性、競合状態なし | Write側が状態を持つ |
| B | Read Side | Write側がステートレス | 結果整合性 |
| C | Hybrid（推奨） | 強い整合性 + 軽量Write | - |

**推奨: Option C（Hybrid）**
- BookshelfActor: `Set[ISBN]` のみ保持（~200KB）
- ReadModel: 詳細データ + 複数インデックス

### 5. コンポーネント設計

#### Command Side
```scala
sealed trait BookCommand
case class RegisterBook(isbn: Option[ISBN], title: NES) extends BookCommand

trait BookCommandHandler[F[_]]:
  def handle(cmd: BookCommand): F[Either[DomainError, List[BookEvent]]]

trait EventStore[F[_]]:
  def append(aggregateId: String, events: List[DomainEvent]): F[Unit]
  def subscribe(fromPosition: Long): fs2.Stream[F, DomainEvent]
```

#### Read Side
```scala
sealed trait BookQuery
case class SearchByTag(tag: Tag) extends BookQuery

final case class BookReadModel(
  books: Map[BookId, BookView],
  isbnIndex: Map[NormalizedISBN, BookId],
  tagIndex: Map[Tag, Set[BookId]],
  titleIndex: TrieMap[String, Set[BookId]]
)

trait BookQueryService[F[_]]:
  def query(q: BookQuery): F[QueryResult]
  def exists(isbn: ISBN): F[Boolean]
```

#### Projector
```scala
trait Projector[F[_]]:
  def project(event: DomainEvent): F[Unit]
  def rebuild(): F[Unit]
```

### 6. 最終アーキテクチャ

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        HandyBookshelf - CQRS Architecture                        │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│    ┌──────────────────────────────────────────────────────────────────────────┐ │
│    │                              API Gateway (HTTP4s + Tapir)                 │ │
│    └────────────────────────────────┬─────────────────────────────────────────┘ │
│                                     │                                           │
│              ┌──────────────────────┴──────────────────────┐                    │
│              ▼                                             ▼                    │
│    ┌─────────────────────┐                     ┌─────────────────────────────┐  │
│    │    COMMAND SIDE     │                     │        QUERY SIDE           │  │
│    │                     │                     │                             │  │
│    │  BookCommandHandler │                     │    BookQueryService         │  │
│    │         │           │                     │           │                 │  │
│    │         ▼           │                     │           ▼                 │  │
│    │  BookshelfActor     │◄── exists(isbn) ───│    BookReadModel            │  │
│    │  ┌───────────────┐  │                     │    ┌───────────────────┐   │  │
│    │  │ ISBN Index    │  │                     │    │ books: Map        │   │  │
│    │  │ Set[ISBN]     │  │                     │    │ isbnIndex: Map    │   │  │
│    │  │ (~200KB)      │  │                     │    │ tagIndex: Map     │   │  │
│    │  └───────────────┘  │                     │    │ titleIndex: Trie  │   │  │
│    │         │           │                     │    └─────────▲─────────┘   │  │
│    │         ▼           │                     │              │             │  │
│    └─────────────────────┘                     └──────────────┼─────────────┘  │
│               │                                               │                 │
│               ▼                                               │                 │
│    ┌─────────────────────┐                     ┌──────────────┴──────────────┐  │
│    │    EVENT STORE      │ ──── Subscribe ───► │        PROJECTOR            │  │
│    │  (ScyllaDB/Dynamo)  │     (fs2.Stream)    │  - Updates ReadModel        │  │
│    └─────────────────────┘                     └─────────────────────────────┘  │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 7. 起動シーケンス

1. Event Store 接続
2. ReadModel 初期化（空）
3. Projector 起動 & Catch-up（全イベント読み込み、ReadModel再構築）
4. BookshelfActor 起動（ReadModelからISBN Indexをコピー）
5. Projector を Live モードに切り替え（リアルタイム購読）
6. API Server 起動

---

## 第5回ディスカッション：非ISBN識別子対応（EntityID設計）

### 1. 議題

arXiv論文やDOI付き記事など、ISBNを持たない文献を登録できるようにするための設計。

### 2. 実装内容

#### 2.1 新規作成ファイル

**`util/src/main/scala/com/handybookshelf/util/Identifiers.scala`**

Iron制約を使った型安全な識別子定義：

```scala
// arXiv ID: YYMM.NNNNN or YYMM.NNNNNvN
type ArxivIdConstraint = Match["^\\d{4}\\.\\d{4,5}(v\\d+)?$"]
type ArxivId = String :| ArxivIdConstraint

// DOI: 10.prefix/suffix
type DOIConstraint = Match["^10\\.\\d{4,9}/[^\\s]+$"]
type DOI = String :| DOIConstraint

// 拡張メソッド
object ArxivId:
  def fromString(str: String): Option[ArxivId]
  extension (arxivId: ArxivId)
    def version: Option[Int]      // バージョン番号
    def withoutVersion: String    // バージョンなしのベースID
    def normalized: String        // 正規化（バージョンなし、小文字）

object DOI:
  def fromString(str: String): Option[DOI]
  extension (doi: DOI)
    def prefix: String            // 10.XXXX部分
    def suffix: String            // suffix部分
    def normalized: String        // 正規化（小文字）

// NESへの拡張
extension (str: NES)
  def arxivIdOpt: Option[ArxivId]
  def doiOpt: Option[DOI]
```

**`util/src/test/scala/com/handybookshelf/util/IdentifiersSpec.scala`**

プロパティベーステストを含む63件のテスト：
- arXiv ID: バリデーション、バージョン抽出、正規化
- DOI: バリデーション、prefix/suffix分割、正規化

#### 2.2 更新ファイル

**`domain/src/main/scala/com/handybookshelf/domain/BookIdentifier.scala`**

識別子タイプを4種類に拡張：

```scala
sealed trait BookIdentifier:
  def description: String
  def normalizedKey: NormalizedIdentifier

object BookIdentifier:
  final case class ISBN(isbn: util.ISBN) extends BookIdentifier
  final case class Arxiv(arxivId: ArxivId) extends BookIdentifier
  final case class DOI(doi: util.DOI) extends BookIdentifier
  final case class Title(title: NES) extends BookIdentifier  // フォールバック

// 正規化された識別子（重複チェック用）
opaque type NormalizedIdentifier = String
// フォーマット: "isbn:9784873115658", "arxiv:2301.12345", "doi:10.1038/nature12373", "title:some title"
```

**`domain/src/main/scala/com/handybookshelf/domain/BookAggregate.scala`**

- `isbn: Option[ISBN]` → `identifier: Option[BookIdentifier]` に変更
- 後方互換性: `def isbn: Option[ISBN]` 計算プロパティを追加
- `register(identifier: BookIdentifier, title: NES)` メソッドを追加
- `registerWithISBN` は後方互換性のために残す

**`domain/src/main/scala/com/handybookshelf/domain/BookEvents.scala`**

`BookRegistered` イベントのフィールド変更：

```scala
final case class BookRegistered(
    eventId: EventId,
    bookId: BookId,
    identifier: BookIdentifier,  // 変更: isbn: Option[ISBN] から
    title: NES,
    version: EventVersion,
    timestamp: Timestamp
) extends BookEvent:
  def isbn: Option[ISBN] = identifier match  // 後方互換性
    case BookIdentifier.ISBN(isbn) => Some(isbn)
    case _                         => None
```

**`domain/src/main/scala/com/handybookshelf/domain/Book.scala`**

- `isbn: Option[ISBN]` → `identifier: Option[BookIdentifier]` に変更
- 後方互換性: `def isbn: Option[ISBN]` 計算プロパティを追加
- カスタムCirceコーデックを追加

**`domain/src/main/scala/com/handybookshelf/domain/BookCommands.scala`**

`RegisterBook` コマンドの変更：

```scala
final case class RegisterBook(
    bookId: BookId,
    identifier: BookIdentifier,  // 変更: isbn: Option[ISBN] から
    title: NES
) extends BookCommand

object RegisterBook:
  def withISBN(bookId: BookId, isbn: Option[ISBN], title: NES): RegisterBook  // 後方互換性
```

**`domain/src/main/scala/com/handybookshelf/domain/DomainError.scala`**

`DuplicateBookError` の変更：

```scala
final case class DuplicateBookError(
    identifier: BookIdentifier,  // 変更: isbn: ISBN から
    existingBookId: Option[BookId] = None,
    cause: Option[Throwable] = None
) extends DomainError:
  def isbn: Option[ISBN] = identifier match  // 後方互換性
    case BookIdentifier.ISBN(isbn) => Some(isbn)
    case _                         => None

object DuplicateBookError:
  def fromISBN(isbn: ISBN, ...): DuplicateBookError  // 後方互換性
```

**`domain/src/main/scala/com/handybookshelf/domain/repositories/BookRepository.scala`**

新しいメソッドの追加：

```scala
trait BookRepository extends AggregateRepository[BookAggregate, BookEvent]:
  // 新規追加
  def existsByIdentifier(identifier: BookIdentifier): IO[Boolean]
  def findByIdentifier(identifier: BookIdentifier): IO[Option[BookAggregate]]
  def findByNormalizedIdentifier(normalizedKey: NormalizedIdentifier): IO[Option[BookAggregate]]

  // 既存メソッドはデフォルト実装でexistsByIdentifierに委譲
  def existsByISBN(isbn: ISBN): IO[Boolean] =
    existsByIdentifier(BookIdentifier.ISBN(isbn))
```

**`domain/src/main/scala/com/handybookshelf/domain/services/BookRegistrationService.scala`**

識別子ベースの登録サービスに更新：

```scala
trait BookRegistrationService[F[_]]:
  def register(identifier: BookIdentifier, title: NES): F[Either[DuplicateBookError, BookAggregate]]
  def registerWithISBN(isbn: Option[ISBN], title: NES): F[Either[DuplicateBookError, BookAggregate]]
  def checkDuplicate(identifier: BookIdentifier): F[Option[BookAggregate]]
  def checkDuplicateByISBN(isbn: ISBN): F[Option[BookAggregate]]
```

**`domain/src/main/scala/com/handybookshelf/domain/BookProjections.scala`**

`BookView` の変更：

```scala
final case class BookView(
    id: BookId,
    title: NES,
    identifier: Option[BookIdentifier],  // 変更: isbn: Option[ISBN] から
    ...
):
  def isbn: Option[ISBN] = identifier.collect { case BookIdentifier.ISBN(isbn) => isbn }
```

### 3. 設計方針

1. **後方互換性**: 既存のISBNベースのAPIは全て維持
2. **正規化による重複検出**:
   - ISBN-10/13 → 常にISBN-13形式に正規化
   - arXiv ID → バージョンを除去して小文字化
   - DOI → 小文字化
   - タイトル → 小文字化 & トリム
3. **型安全性**: Iron制約による実行時・コンパイル時バリデーション
4. **拡張性**: 新しい識別子タイプの追加が容易（sealed traitに追加するだけ）

### 4. テスト結果

```
util:
  - IdentifiersSpec: 27 tests passed
  - ISBNConverterSpec: 21 tests passed
  - ULIDConverterSpec: 15 tests passed
  Total: 63 tests passed

domain:
  - Compilation successful (22 Scala sources)
```

### 5. 今後の課題

1. **infrastructureモジュールの更新**: `BookRepository` 実装クラスの更新
2. **adopterモジュールの警告修正**: 既存の未使用インポート警告の解消
3. **usecaseモジュールの更新**: 新しい識別子タイプを使用するユースケースの追加
4. **controllerモジュールの更新**: APIエンドポイントでの新識別子対応

---

*以上、議事録終わり*
