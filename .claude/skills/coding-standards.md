# Scala Coding Standards / コーディング規約

このスキルはHandyBookshelfプロジェクトのコーディング規約を定義します。

## 命名規則

### パッケージ
- すべて小文字、ドットで区切る
- `com.handybookshelf.<module>.<subpackage>`

### クラス・トレイト・オブジェクト
- PascalCase を使用
- 集約ルート: `<Name>Aggregate` (例: `BookAggregate`)
- リポジトリ: `<Name>Repository` (例: `EventRepository`)
- アクター: `<Name>Actor` (例: `UserStateActor`)

### メソッド・変数
- camelCase を使用
- Boolean は `is`, `has`, `can` で始める
- 副作用のあるメソッドは動詞で始める

### 型パラメータ
- 単一文字: `F[_]`, `A`, `B`
- エフェクト型: `F[_]: Async`

## 型安全性

### Iron による制約型
```scala
// Good: コンパイル時に制約を保証
opaque type ISBN = String :| (DigitString & (Length[10] | Length[13]))

// Bad: ランタイム検証のみ
def validateIsbn(s: String): Boolean = ...
```

### Option / Either の使用
```scala
// Good: 失敗可能性を型で表現
def findBook(id: BookId): F[Option[Book]]
def registerBook(cmd: RegisterCommand): F[Either[DomainError, BookId]]

// Bad: 例外を投げる
def getBook(id: BookId): F[Book] // throws NotFoundException
```

## イベントソーシング規約

### コマンドとイベント
- コマンド: 動詞 + 名詞 (例: `RegisterBook`, `ChangeLocation`)
- イベント: 名詞 + 過去分詞 (例: `BookRegistered`, `LocationChanged`)

### 集約設計
```scala
// 集約は AggregateRoot を継承
trait BookAggregate extends AggregateRoot[BookId, BookEvent, BookState]:
  // コマンドハンドラ
  def handle(cmd: BookCommand): Either[DomainError, List[BookEvent]]

  // イベント適用
  def apply(event: BookEvent): BookState
```

## エフェクトシステム

### Cats Effect の使用
```scala
// Good: リソース安全な実装
def withConnection[A](f: Connection => F[A]): F[A] =
  Resource.make(acquire)(release).use(f)

// Good: 並行処理
(taskA, taskB).parTupled
```

### エラーハンドリング
```scala
// Good: ドメインエラーは Either で表現
def process(cmd: Command): F[Either[DomainError, Result]]

// Good: 技術的エラーは MonadError で処理
def fetch(id: Id): F[Entity] // F には ApplicativeError インスタンスが必要
```

## テスト規約

### テストファイル命名
- 単体テスト: `*Spec.scala`
- プロパティテスト: `*Props.scala` または `*Spec.scala` 内で ScalaCheck 使用

### テスト構造
```scala
class BookAggregateSpec extends AnyFlatSpec with Matchers:
  "BookAggregate" should "register a new book" in {
    // Given
    val command = RegisterBook(...)

    // When
    val result = aggregate.handle(command)

    // Then
    result shouldBe Right(List(BookRegistered(...)))
  }
```

## インポート規約

### 順序
1. Java/Scala 標準ライブラリ
2. サードパーティライブラリ
3. プロジェクト内パッケージ

### ワイルドカードインポート
```scala
// Good: 明示的なインポート
import cats.effect.{IO, Resource}
import cats.syntax.all.*  // syntax は許可

// Avoid: 大量のワイルドカード
import scala.collection.mutable.*
```

## ドキュメンテーション

### Scaladoc
- 公開 API には必須
- 内部実装には不要（コードを自己文書化する）

```scala
/** 書籍を登録する
  *
  * @param isbn ISBN（10桁または13桁）
  * @param title 書籍タイトル
  * @return 登録成功時は BookId、失敗時は DomainError
  */
def register(isbn: ISBN, title: Title): F[Either[DomainError, BookId]]
```

## Scala 3 Enum

### 基本的な enum
```scala
enum Color:
  case Red, Green, Blue

// 使用例
val c: Color = Color.Red
```

### パラメータ付き enum
```scala
enum Planet(val mass: Double, val radius: Double):
  case Mercury extends Planet(3.303e+23, 2.4397e6)
  case Earth   extends Planet(5.976e+24, 6.37814e6)

  def surfaceGravity: Double = 6.67300e-11 * mass / (radius * radius)
```

### ADT (代数的データ型) としての enum
```scala
// ドメインエラーの定義
enum DomainError:
  case BookNotFound(id: BookId)
  case InvalidISBN(reason: String)
  case DuplicateBook(isbn: ISBN)
  case Unauthorized(userId: UserId, action: String)

// パターンマッチ
def handle(error: DomainError): String = error match
  case DomainError.BookNotFound(id) => s"Book not found: $id"
  case DomainError.InvalidISBN(r)   => s"Invalid ISBN: $r"
  case DomainError.DuplicateBook(i) => s"Duplicate: $i"
  case DomainError.Unauthorized(u, a) => s"$u cannot $a"
```

### 便利なメソッド
```scala
enum Status:
  case Pending, InProgress, Completed

Status.values              // Array(Pending, InProgress, Completed)
Status.valueOf("Pending")  // Status.Pending
Status.Pending.ordinal     // 0
```

### JSON シリアライゼーション (Circe)
```scala
import io.circe.{Encoder, Decoder}

enum BookStatus:
  case Available, Borrowed, Reserved

object BookStatus:
  given Encoder[BookStatus] = Encoder.encodeString.contramap(_.toString)
  given Decoder[BookStatus] = Decoder.decodeString.emap: s =>
    scala.util.Try(BookStatus.valueOf(s))
      .toEither
      .left.map(_ => s"Invalid status: $s")
```

### 型パラメータ付き enum
```scala
enum Option[+A]:
  case Some(value: A)
  case None
```

## 禁止事項

- `null` の使用（`Option` を使用する）
- `throw` による例外（`Either` または `MonadError` を使用する）
- 可変コレクション（イミュータブルを優先）
- `Any`, `AnyRef` への明示的なキャスト
- `var` の使用（`val` または State モナドを使用）
- **Scala 2 スタイルの Enumeration は使用禁止**（`extends Enumeration` は使わない）
- **sealed trait + case object による列挙型は enum を使用する**
