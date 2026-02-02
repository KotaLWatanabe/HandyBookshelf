# DDD & Event Sourcing Patterns / ドメイン駆動設計パターン

このスキルはHandyBookshelfプロジェクトのDDDおよびイベントソーシングパターンを定義します。

## 集約設計原則

### 集約境界
- 集約は一貫性境界を定義する
- トランザクションは単一の集約内で完結させる
- 集約間の参照は ID のみで行う

```scala
// Good: ID による参照
case class Book(
  id: BookId,
  ownerId: UserId,  // User 集約への ID 参照
  ...
)

// Bad: 集約をネストする
case class Book(
  id: BookId,
  owner: User,  // 集約のネスト
  ...
)
```

### 集約ルートのみが外部公開
```scala
// Good: 集約ルートを通じてアクセス
bookAggregate.addTag(tagName)

// Bad: 内部エンティティに直接アクセス
book.tags.add(tagName)
```

## イベント設計

### イベントの不変性
- イベントは一度発行されたら変更不可
- スキーマ変更はバージョニングで対応

```scala
// イベントバージョニング
sealed trait BookEvent
case class BookRegisteredV1(isbn: String, title: String) extends BookEvent
case class BookRegisteredV2(
  isbn: ISBN,
  title: Title,
  registeredAt: Instant  // 新フィールド追加
) extends BookEvent
```

### イベント粒度
- ビジネス上意味のある単位でイベントを設計
- 技術的な詳細は含めない

```scala
// Good: ビジネスイベント
case class BookLocationChanged(
  bookId: BookId,
  newLocation: Location,
  changedAt: Instant
) extends BookEvent

// Bad: 技術的すぎる
case class BookFieldUpdated(
  bookId: BookId,
  fieldName: String,
  oldValue: String,
  newValue: String
) extends BookEvent
```

## コマンドハンドリング

### コマンドバリデーション
```scala
def handle(cmd: RegisterBook): Either[DomainError, List[BookEvent]] =
  for
    isbn  <- ISBN.validate(cmd.isbn).leftMap(InvalidIsbn(_))
    title <- Title.validate(cmd.title).leftMap(InvalidTitle(_))
    _     <- validateNotDuplicate(isbn)
  yield List(BookRegistered(BookId.generate(), isbn, title, Instant.now()))
```

### 冪等性の確保
```scala
def handle(cmd: AddTag): Either[DomainError, List[BookEvent]] =
  if state.tags.contains(cmd.tagName) then
    Right(Nil)  // 既に存在する場合は何もしない（冪等）
  else
    Right(List(BookTagAdded(state.id, cmd.tagName, Instant.now())))
```

## リポジトリパターン

### イベントリポジトリ
```scala
trait EventRepository[F[_], ID, E]:
  def save(id: ID, events: List[E], expectedVersion: Version): F[Unit]
  def load(id: ID): F[List[E]]
  def loadFrom(id: ID, fromVersion: Version): F[List[E]]
```

### 集約リポジトリ
```scala
trait AggregateRepository[F[_], A <: AggregateRoot[_, _, _]]:
  def get(id: A#ID): F[Option[A]]
  def save(aggregate: A): F[Unit]
```

## ドメインサービス

### 複数集約をまたぐ操作
```scala
// ドメインサービスで集約間の協調を行う
class BookTransferService[F[_]: Monad](
  bookRepo: BookRepository[F],
  userRepo: UserRepository[F]
):
  def transfer(bookId: BookId, toUserId: UserId): F[Either[DomainError, Unit]] =
    for
      book <- bookRepo.get(bookId)
      user <- userRepo.get(toUserId)
      result <- (book, user) match
        case (Some(b), Some(u)) => executeTransfer(b, u)
        case _ => DomainError.NotFound.asLeft.pure[F]
    yield result
```

## 読み取りモデル（CQRS）

### プロジェクション
```scala
// イベントから読み取りモデルを構築
class BookProjection[F[_]](eventStore: EventStore[F]):
  def project(event: BookEvent): F[Unit] = event match
    case BookRegistered(id, isbn, title, _) =>
      insertBookView(BookView(id, isbn, title, ...))
    case BookLocationChanged(id, location, _) =>
      updateBookView(id, _.copy(location = location))
    case BookRemoved(id, _) =>
      deleteBookView(id)
```

## バリューオブジェクト

### 不変かつ自己検証
```scala
// Iron を使用した制約型
opaque type ISBN = String :| ValidISBN
object ISBN:
  def apply(s: String): Either[String, ISBN] =
    s.refineEither[ValidISBN]

// スマートコンストラクタ
opaque type Title = String :| NonEmpty
object Title:
  def apply(s: String): Option[Title] =
    Option.when(s.nonEmpty)(s.asInstanceOf[Title])
```

## ドメインエラー

### 型安全なエラー表現
```scala
sealed trait DomainError
object DomainError:
  case class BookNotFound(id: BookId) extends DomainError
  case class InvalidISBN(reason: String) extends DomainError
  case class DuplicateBook(isbn: ISBN) extends DomainError
  case class Unauthorized(userId: UserId, action: String) extends DomainError
```
