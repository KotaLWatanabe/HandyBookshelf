# HandyBookshelf 業務フロー

このドキュメントでは、HandyBookshelfアプリケーションの業務フローをドメインイベントとコマンドに基づいて説明します。

## 概要

HandyBookshelfは本の管理システムで、以下の主要な操作をサポートしています：
- 本の登録・削除
- 場所の変更
- タグの追加・削除
- デバイスの追加・削除
- タイトルの更新

## ドメインモデル構造

```mermaid
classDiagram
    class BookAggregate {
        +BookId bookId
        +Option[NES] title
        +Option[ISBN] isbn
        +Option[Location] location
        +Set[Tag] tags
        +Set[Device] devices
        +EventVersion version
        +Boolean isDeleted
    }
    
    class BookCommand {
        <<interface>>
        +BookId bookId
    }
    
    class BookEvent {
        <<interface>>
        +BookId bookId
        +BookEventType bookEventType
    }
    
    BookCommand <|-- RegisterBook
    BookCommand <|-- ChangeBookLocation
    BookCommand <|-- AddBookTag
    BookCommand <|-- RemoveBookTag
    BookCommand <|-- AddBookDevice
    BookCommand <|-- RemoveBookDevice
    BookCommand <|-- UpdateBookTitle
    BookCommand <|-- RemoveBook
    
    BookEvent <|-- BookRegistered
    BookEvent <|-- BookLocationChanged
    BookEvent <|-- BookTagAdded
    BookEvent <|-- BookTagRemoved
    BookEvent <|-- BookDeviceAdded
    BookEvent <|-- BookDeviceRemoved
    BookEvent <|-- BookTitleUpdated
    BookEvent <|-- BookRemoved
```

## 業務フロー全体図

```mermaid
stateDiagram-v2
    [*] --> Unregistered: 新しい本ID生成
    
    Unregistered --> Registered: RegisterBook / BookRegistered
    
    Registered --> Registered: ChangeBookLocation / BookLocationChanged
    Registered --> Registered: AddBookTag / BookTagAdded
    Registered --> Registered: RemoveBookTag / BookTagRemoved
    Registered --> Registered: AddBookDevice / BookDeviceAdded
    Registered --> Registered: RemoveBookDevice / BookDeviceRemoved
    Registered --> Registered: UpdateBookTitle / BookTitleUpdated
    
    Registered --> Deleted: RemoveBook / BookRemoved
    Deleted --> [*]
    
    note right of Registered
        本が登録済み状態では
        複数の操作が可能
    end note
    
    note right of Deleted
        削除された本への
        操作は全て無効
    end note
```

## コマンド・イベントフロー

```mermaid
sequenceDiagram
    participant Client
    participant CommandHandler
    participant BookAggregate
    participant EventStore
    
    Client->>CommandHandler: BookCommand
    
    CommandHandler->>EventStore: getEvents(streamId)
    EventStore-->>CommandHandler: List[BookEvent]
    
    CommandHandler->>BookAggregate: fromEvents(bookId, events)
    BookAggregate-->>CommandHandler: BookAggregate
    
    CommandHandler->>BookAggregate: handleCommand(command)
    BookAggregate-->>CommandHandler: BookAggregate with events
    
    CommandHandler->>EventStore: saveEvents(streamId, events)
    EventStore-->>CommandHandler: Success
    
    CommandHandler-->>Client: List[BookEvent]
```

## 各操作の詳細フロー

### 1. 本の登録

```mermaid
flowchart TD
    A[RegisterBook Command] --> B{既に登録済み？}
    B -->|Yes| C[IllegalStateException]
    B -->|No| D[BookRegistered Event生成]
    D --> E[BookAggregateの状態更新]
    E --> F[EventStore保存]
```

### 2. 場所変更

```mermaid
flowchart TD
    A[ChangeBookLocation Command] --> B{削除済み？}
    B -->|Yes| C[IllegalStateException]
    B -->|No| D[BookLocationChanged Event生成]
    D --> E[location更新]
    E --> F[EventStore保存]
```

### 3. タグ管理

```mermaid
flowchart TD
    A[AddBookTag/RemoveBookTag] --> B{削除済み？}
    B -->|Yes| C[IllegalStateException]
    B -->|No| D{タグ操作可能？}
    D -->|Add: 既存| E[変更なし]
    D -->|Remove: 存在しない| E
    D -->|操作可能| F[BookTagAdded/Removed Event]
    F --> G[tags Set更新]
    G --> H[EventStore保存]
```

### 4. デバイス管理

```mermaid
flowchart TD
    A[AddBookDevice/RemoveBookDevice] --> B{削除済み？}
    B -->|Yes| C[IllegalStateException]
    B -->|No| D{デバイス操作可能？}
    D -->|Add: 既存| E[変更なし]
    D -->|Remove: 存在しない| E
    D -->|操作可能| F[BookDeviceAdded/Removed Event]
    F --> G[devices Set更新]
    G --> H[EventStore保存]
```

### 5. タイトル更新

```mermaid
flowchart TD
    A[UpdateBookTitle Command] --> B{削除済み？}
    B -->|Yes| C[IllegalStateException]
    B -->|No| D{登録済み？}
    D -->|No| E[IllegalStateException - 未登録]
    D -->|Yes| F{同じタイトル？}
    F -->|Yes| G[変更なし]
    F -->|No| H[BookTitleUpdated Event]
    H --> I[title更新]
    I --> J[EventStore保存]
```

### 6. 本の削除

```mermaid
flowchart TD
    A[RemoveBook Command] --> B{既に削除済み？}
    B -->|Yes| C[変更なし]
    B -->|No| D[BookRemoved Event生成]
    D --> E[isDeleted = true]
    E --> F[EventStore保存]
```

## イベントソーシングパターン

本システムはイベントソーシングパターンを採用しており、以下の流れで動作します：

1. **コマンド受信**: クライアントからのコマンドを受信
2. **イベント復元**: EventStoreから過去のイベントを取得してアグリゲートを復元
3. **ビジネスロジック実行**: コマンドに基づいてビジネスルールを適用
4. **イベント生成**: 新しいドメインイベントを生成
5. **イベント永続化**: EventStoreにイベントを保存
6. **アグリゲート更新**: 新しいイベントでアグリゲートの状態を更新

このパターンにより、すべての変更履歴が保持され、システムの状態を任意の時点に復元することが可能です。

## ビジネスルール

- 未登録の本に対する操作（場所変更、タグ追加等）は不可
- 削除済みの本に対するあらゆる操作は不可
- 既に存在するタグ・デバイスの重複追加は無視
- 存在しないタグ・デバイスの削除は無視
- 同じタイトルへの更新は無視