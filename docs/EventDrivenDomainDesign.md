# ドメインイベント中心のDDD設計

HandyBookshelfプロジェクトにおけるイベント駆動ドメイン設計の実装詳細です。

## 設計概要

ドメインイベント中心のDDDアプローチを採用し、集約をイベントストリームとして表現するEvent Sourcingシステムを構築しました。すべての状態変更がイベントとして記録され、時系列での状態変化が完全に再現可能です。

## 実装された構成要素

### 🎯 **イベント基盤**

#### DomainEvent基底trait (`domain/DomainEvent.scala`)
```scala
trait DomainEvent:
  def eventId: EventId
  def aggregateId: String
  def version: EventVersion  
  def timestamp: Timestamp
  def eventType: String
```

#### EventId・EventVersion
- **EventId**: ULIDベースの一意識別子
- **EventVersion**: イベントのバージョン管理（楽観的排他制御）

#### BookEvents (`domain/BookEvents.scala`)
具体的なドメインイベント：
- **BookRegistered** - 本の登録
- **BookLocationChanged** - 配置場所変更
- **BookTagAdded/Removed** - タグ追加・削除
- **BookDeviceAdded/Removed** - デバイス追加・削除
- **BookTitleUpdated** - タイトル更新
- **BookRemoved** - 本の削除

### ⚡ **Event Sourcing集約**

#### AggregateRoot基底trait (`domain/AggregateRoot.scala`)
```scala
trait AggregateRoot[A <: AggregateRoot[A, E], E <: DomainEvent]:
  def id: String
  def version: EventVersion
  def uncommittedEvents: List[E]
  protected def applyEvent(event: E): A
  def withEvent(event: E): A
  def markEventsAsCommitted: A
  def loadFromHistory(events: List[E]): A
```

#### BookAggregate (`domain/BookAggregate.scala`)
- イベント履歴から現在状態を復元
- ビジネスロジックの実行とイベント生成
- 未コミットイベントの管理

**主要メソッド：**
- `register()` - 本の登録
- `changeLocation()` - 配置場所変更
- `addTag/removeTag()` - タグ管理
- `addDevice/removeDevice()` - デバイス管理
- `updateTitle()` - タイトル更新
- `remove()` - 本の削除

### 🔄 **CQRS分離**

#### Commands (`domain/BookCommands.scala`)
状態変更コマンド：
- **RegisterBook** - 本の登録
- **ChangeBookLocation** - 配置場所変更
- **AddBookTag/RemoveBookTag** - タグ管理
- **AddBookDevice/RemoveBookDevice** - デバイス管理
- **UpdateBookTitle** - タイトル更新
- **RemoveBook** - 本の削除

#### CommandHandler
```scala
trait BookCommandHandler:
  def handle(command: BookCommand): IO[List[BookEvent]]

class BookCommandHandlerImpl(eventStore: EventStore):
  def handle(command: BookCommand): IO[List[BookEvent]]
```

### 💾 **イベントストア**

#### EventStore (`domain/EventStore.scala`)
```scala
trait EventStore:
  def getEvents(streamId: EventStreamId): IO[List[BookEvent]]
  def getEventsFromVersion(streamId: EventStreamId, fromVersion: EventVersion): IO[List[BookEvent]]
  def saveEvents(streamId: EventStreamId, events: List[BookEvent], expectedVersion: ExpectedVersion): IO[Unit]
  def getStreamMetadata(streamId: EventStreamId): IO[Option[StreamMetadata]]
  def streamExists(streamId: EventStreamId): IO[Boolean]
```

#### InMemoryEventStore
- メモリベースのイベントストア実装
- バージョン管理による楽観的排他制御
- ストリームメタデータ管理

### 📊 **クエリ側Projection**

#### ビューモデル (`domain/BookProjections.scala`)
- **BookView** - 本の詳細ビュー
- **BookSummary** - 本の要約ビュー
- **LocationView** - 配置場所別ビュー
- **TagView** - タグ別ビュー

#### Projection基底trait
```scala
trait Projection[V]:
  def apply(event: DomainEvent): IO[Unit]
  def getView(id: String): IO[Option[V]]
  def getAllViews(): IO[List[V]]
```

#### 具体的なProjection実装
- **BookViewProjection** - 本詳細ビューの構築
- **LocationViewProjection** - 配置場所別ビューの構築
- **TagViewProjection** - タグ別ビューの構築

#### ProjectionManager
複数のProjectionを統合管理し、イベント発生時に各Projectionを更新します。

## 設計の利点

### 1. **完全な監査ログ**
すべての状態変更がイベントとして記録され、いつ何が変更されたかが完全に追跡可能

### 2. **時系列状態復元**
任意の時点での集約状態を、イベント履歴から完全に復元可能

### 3. **CQRS分離**
コマンド（書き込み）とクエリ（読み取り）が完全に分離され、それぞれ最適化可能

### 4. **スケーラビリティ**
読み取り専用のProjectionを独立してスケール可能

### 5. **イベント駆動アーキテクチャ**
イベントを通じた疎結合な連携により、新機能追加が容易

## 使用例

```scala
// コマンドの実行
val registerCommand = RegisterBook(
  bookId = BookId.generate("9784123456789", Timestamp.now()),
  isbn = Some("9784123456789".nes),
  title = "Scala実践ガイド".nes
)

for {
  events <- commandHandler.handle(registerCommand)
  _ <- projectionManager.handleEvent(events.head)
  view <- bookViewProjection.getView(registerCommand.bookId.toString)
} yield view
```

この設計により、HandyBookshelfは堅牢で拡張性の高いイベント駆動システムとして機能します。