# API Design Standards / API設計規約

このスキルはHandyBookshelfプロジェクトのHTTP API設計規約を定義します。

## エンドポイント設計

### RESTful 命名規則
```
GET    /api/v1/books          # 一覧取得
GET    /api/v1/books/{id}     # 単一取得
POST   /api/v1/books          # 新規作成
PUT    /api/v1/books/{id}     # 全体更新
PATCH  /api/v1/books/{id}     # 部分更新
DELETE /api/v1/books/{id}     # 削除
```

### リソース名
- 複数形を使用: `/books`, `/users`, `/tags`
- ネストは2階層まで: `/users/{userId}/books`
- アクションが必要な場合は動詞を許容: `/books/{id}/archive`

## Tapir エンドポイント定義

### 基本構造
```scala
val getBook: Endpoint[Unit, BookId, ApiError, BookResponse, Any] =
  endpoint
    .get
    .in("api" / "v1" / "books" / path[BookId]("bookId"))
    .out(jsonBody[BookResponse])
    .errorOut(
      oneOf[ApiError](
        oneOfVariant(statusCode(NotFound), jsonBody[NotFoundError]),
        oneOfVariant(statusCode(BadRequest), jsonBody[ValidationError])
      )
    )
    .description("指定されたIDの書籍を取得する")
```

### 認証付きエンドポイント
```scala
val secureEndpoint: PartialServerEndpoint[String, UserId, Unit, ApiError, Unit, Any, F] =
  endpoint
    .securityIn(auth.bearer[String]())
    .serverSecurityLogic(token => authenticate(token))
```

## リクエスト/レスポンス設計

### リクエストボディ
```scala
// コマンドオブジェクトとして定義
case class RegisterBookRequest(
  isbn: String,
  title: String,
  location: Option[String]
) derives Codec.AsObject, Schema

// バリデーションはドメイン層で実施
```

### レスポンス形式
```scala
// 成功レスポンス
case class BookResponse(
  id: String,
  isbn: String,
  title: String,
  location: Option[String],
  tags: List[String],
  createdAt: Instant,
  updatedAt: Instant
) derives Codec.AsObject, Schema

// 一覧レスポンス（ページネーション付き）
case class BooksResponse(
  items: List[BookResponse],
  total: Long,
  page: Int,
  pageSize: Int,
  hasNext: Boolean
) derives Codec.AsObject, Schema
```

## エラーハンドリング

### エラーレスポンス形式
```scala
case class ApiError(
  code: String,
  message: String,
  details: Option[Map[String, String]] = None
) derives Codec.AsObject, Schema

// エラーコード体系
object ErrorCodes:
  val NotFound = "NOT_FOUND"
  val ValidationError = "VALIDATION_ERROR"
  val Unauthorized = "UNAUTHORIZED"
  val Forbidden = "FORBIDDEN"
  val Conflict = "CONFLICT"
  val InternalError = "INTERNAL_ERROR"
```

### ドメインエラーからの変換
```scala
def toApiError(error: DomainError): (StatusCode, ApiError) = error match
  case DomainError.BookNotFound(id) =>
    (StatusCode.NotFound, ApiError(ErrorCodes.NotFound, s"Book not found: $id"))
  case DomainError.InvalidISBN(reason) =>
    (StatusCode.BadRequest, ApiError(ErrorCodes.ValidationError, reason))
  case DomainError.DuplicateBook(isbn) =>
    (StatusCode.Conflict, ApiError(ErrorCodes.Conflict, s"Book already exists: $isbn"))
```

## ページネーション

### クエリパラメータ
```scala
case class PaginationParams(
  page: Int = 1,
  pageSize: Int = 20,
  sortBy: Option[String] = None,
  sortOrder: Option[SortOrder] = None  // asc | desc
)

val listBooks: Endpoint[Unit, PaginationParams, ApiError, BooksResponse, Any] =
  endpoint
    .get
    .in("api" / "v1" / "books")
    .in(query[Int]("page").default(1))
    .in(query[Int]("pageSize").default(20).validate(Validator.max(100)))
    .in(query[Option[String]]("sortBy"))
    .in(query[Option[String]]("sortOrder"))
```

## バリデーション

### Tapir バリデータ
```scala
val registerBook: Endpoint[Unit, RegisterBookRequest, ApiError, BookResponse, Any] =
  endpoint
    .post
    .in("api" / "v1" / "books")
    .in(
      jsonBody[RegisterBookRequest]
        .validate(Validator.custom(req =>
          if req.isbn.length == 10 || req.isbn.length == 13 then
            ValidationResult.Valid
          else
            ValidationResult.Invalid("ISBN must be 10 or 13 digits")
        ))
    )
```

## OpenAPI ドキュメント

### メタデータ
```scala
val openApiInfo = Info(
  title = "HandyBookshelf API",
  version = "1.0.0",
  description = Some("書籍管理アプリケーションAPI"),
  contact = Some(Contact(name = Some("Support"), email = Some("support@example.com")))
)
```

### タグによる分類
```scala
val bookEndpoints = List(getBook, listBooks, registerBook)
  .map(_.tag("Books"))

val userEndpoints = List(getUser, updateUser)
  .map(_.tag("Users"))
```

## HTTP ステータスコード

| 状況 | ステータスコード |
|------|------------------|
| 取得成功 | 200 OK |
| 作成成功 | 201 Created |
| 更新成功（ボディなし）| 204 No Content |
| バリデーションエラー | 400 Bad Request |
| 認証エラー | 401 Unauthorized |
| 認可エラー | 403 Forbidden |
| リソース未発見 | 404 Not Found |
| 重複エラー | 409 Conflict |
| サーバーエラー | 500 Internal Server Error |

## セキュリティ

### 認証ヘッダー
```
Authorization: Bearer <jwt-token>
```

### CORS 設定
```scala
val corsConfig = CORSConfig.default
  .withAllowedOrigins(Set("https://app.example.com"))
  .withAllowedMethods(Set(GET, POST, PUT, DELETE, OPTIONS))
  .withAllowedHeaders(Set("Authorization", "Content-Type"))
```
