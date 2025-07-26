# HandyBookshelf システムアーキテクチャ

## システム処理フロー

以下は、HandyBookshelfシステムの主要な処理フローを示したアーキテクチャ図です。

```mermaid
sequenceDiagram
    participant Client as クライアント
    participant System as ActorSystem
    participant Supervisor as SupervisorActor
    participant UserActor as UserAccountActor
    participant UseCase as ユースケース層
    participant EventStore as イベントストア
    participant QueryDB as クエリ側DB

    Note over System,QueryDB: 1. システム起動フェーズ
    System->>Supervisor: SupervisorActor起動
    activate Supervisor
    Note over Supervisor: アクター管理の準備完了

    Note over Client,QueryDB: 2. ログインフェーズ（改善後）
    Client->>Supervisor: Login Request (userAccountId)
    Supervisor->>UserActor: UserSessionActor起動（軽量）
    activate UserActor
    UserActor->>UserActor: セッション状態管理
    UserActor-->>Client: Login Response

    Note over Client,QueryDB: 3. コマンド実行フェーズ（改善後）
    Client->>Supervisor: Book操作コマンド<br/>(AddBook/RemoveBook/etc.)
    Supervisor->>UserActor: 認証確認要求
    UserActor-->>Supervisor: 認証OK
    Supervisor->>+UseCase: BookshelfActor起動＋コマンド送信
    UseCase->>UseCase: 対応するユースケース処理実行
    activate UseCase
    UseCase->>UseCase: ビジネスロジック処理
    UseCase-->>UserActor: 処理結果
    deactivate UseCase
    
    UserActor->>UserActor: コマンドイベント生成<br/>(BookAddedToShelf/etc.)
    UserActor->>EventStore: イベント永続化
    
    Note over UserActor,QueryDB: 4. 状態更新フェーズ
    UserActor->>UserActor: EventSourcing により<br/>UserAccountState.bookshelf更新
    UserActor->>QueryDB: クエリ側データベース更新<br/>(プロジェクション)
    
    UserActor-->>Client: Command Response

    Note over Client,QueryDB: 5. クエリフェーズ
    Client->>UserActor: GetBookshelf Request
    UserActor-->>Client: Bookshelf Response<br/>(現在の書庫状態)
```

## アーキテクチャコンポーネント

### 1. 現在のActorSystem層（問題のある設計）
```mermaid
graph TD
    AS[ActorSystem] --> SA[SupervisorActor]
    SA --> UA1[UserAccountActor-1<br/>📋 多すぎる責務]
    SA --> UA2[UserAccountActor-2<br/>📋 多すぎる責務]
    SA --> UAn[UserAccountActor-n<br/>📋 多すぎる責務]
    
    UA1 --> BS1[Bookshelf State 1]
    UA2 --> BS2[Bookshelf State 2]
    UAn --> BSn[Bookshelf State n]
```

### 2. 改善後のActorSystem層（責務分離）
```mermaid
graph TD
    AS[ActorSystem] --> SA[SupervisorActor]
    SA --> US1[UserSessionActor-1<br/>🔐 認証専用]
    SA --> US2[UserSessionActor-2<br/>🔐 認証専用]
    SA --> USn[UserSessionActor-n<br/>🔐 認証専用]
    
    SA --> BS1[BookshelfActor-1<br/>📚 書庫管理専用]
    SA --> BS2[BookshelfActor-2<br/>📚 書庫管理専用]
    SA --> BSn[BookshelfActor-n<br/>📚 書庫管理専用]
    
    US1 -.認証確認.-> BS1
    US2 -.認証確認.-> BS2
    USn -.認証確認.-> BSn
```

### 2. UserAccountActor 詳細構造

```mermaid
graph TB
    subgraph "UserAccountActor (PersistentActor)"
        CMD[Commands] --> CH[CommandHandler]
        CH --> EV[Events]
        EV --> EH[EventHandler]
        EH --> STATE[UserAccountState]
        
        subgraph "State"
            STATE --> USERID[userAccountId]
            STATE --> LOGIN[isLoggedIn]
            STATE --> BOOKSHELF[Bookshelf]
        end
        
        subgraph "Bookshelf"
            BOOKSHELF --> BOOKS[Books Map]
            BOOKSHELF --> SORTER[BookSorter]
            BOOKSHELF --> FILTERS[Filters]
        end
    end
    
    CH --> UC[UseCase Layer]
    EV --> ES[EventStore]
    EV --> QDB[Query Database]
```

### 3. イベントソーシングフロー

```mermaid
graph LR
    subgraph "Command Side"
        C[Command] --> UAA[UserAccountActor]
        UAA --> E[Event]
        E --> ES[EventStore]
    end
    
    subgraph "Query Side"
        E --> P[Projection]
        P --> QDB[Query Database]
        QDB --> QM[Query Model]
    end
    
    subgraph "State Reconstruction"
        ES --> ER[Event Replay]
        ER --> CS[Current State]
        CS --> UAA
    end
```

## 主要コンポーネントの責務

### SupervisorActor
- UserAccountActorの生成・管理
- アクターライフサイクル監視
- システム全体の監督

### 責務分離の改善案

#### 現在の問題
UserAccountActorが以下の複数責務を持ち、SRPに違反：
- ユーザー認証・セッション管理
- 書庫管理（Bookshelf操作）
- イベントソーシング永続化

#### 改善後のアクター設計

**UserSessionActor** (PersistentActor) 
- ⚠️ **重要**: セッション管理も永続化が必要
- ユーザー認証・ログイン状態管理
- セッション有効期限管理
- 他アクターへの認証情報提供
- 障害復旧時のセッション状態復元

**BookshelfActor** (PersistentActor)
- 書庫管理専用の永続化アクター
- 書籍の追加・削除・整理
- イベントソーシングによる状態管理
- ユーザー認証はUserSessionActorに委譲

### Bookshelf (ドメインエンティティ)
- 書籍コレクション管理
- フィルタリング・ソート機能
- 不変オブジェクトとして状態管理

### イベントストア
- 全ての状態変更イベントを永続化
- Event Replayによる状態復元
- 監査ログとしての機能

### クエリ側データベース
- 読み取り最適化されたデータ構造
- プロジェクションによる非正規化
- 高速クエリ応答

## 技術スタック

- **Actor Framework**: Apache Pekko (旧Akka)
- **Persistence**: Pekko Persistence (Event Sourcing)
- **Effect System**: Atnos Eff
- **Type Safety**: Scala 3 + Iron constraints
- **JSON Serialization**: Circe
- **HTTP API**: HTTP4s + Tapir

## イベント型一覧

### UserAccount Events
- `UserLoggedIn`: ユーザーログイン
- `UserLoggedOut`: ユーザーログアウト
- `BookAddedToShelf`: 書籍追加
- `BookRemovedFromShelf`: 書籍削除
- `SorterChanged`: ソート方法変更

### コマンド型一覧

### UserAccount Commands
- `LoginUser`: ユーザーログイン
- `LogoutUser`: ユーザーログアウト
- `AddBookToShelf`: 書籍をShelfに追加
- `RemoveBookFromShelf`: 書籍をShelfから削除
- `GetBookshelf`: 現在のBookshelf状態取得
- `ChangeSorter`: ソート方法変更
- `Shutdown`: アクター終了

## 障害復旧とセッション管理

### セッション永続化の重要性

#### 問題：非永続化セッションの場合
```mermaid
sequenceDiagram
    participant User as ユーザー
    participant Session as UserSessionActor<br/>(非永続化)
    participant System as システム
    
    User->>Session: ログイン
    Session->>Session: セッション状態（メモリのみ）
    Note over Session: ✅ 正常動作中
    
    Note over Session,System: 🔥 システム障害発生
    Session->>X: アクター停止・状態消失
    
    Note over Session,System: 🔄 システム復旧
    System->>Session: アクター再起動
    Session->>Session: ❌ セッション状態なし
    User->>Session: API操作試行
    Session-->>User: ❌ 認証エラー（再ログイン必要）
```

#### 解決：永続化セッション
```mermaid
sequenceDiagram
    participant User as ユーザー
    participant Session as UserSessionActor<br/>(PersistentActor)
    participant EventStore as イベントストア
    participant System as システム
    
    User->>Session: ログイン
    Session->>EventStore: UserLoggedIn Event
    Session->>Session: セッション状態管理
    Note over Session: ✅ 正常動作中
    
    Note over Session,System: 🔥 システム障害発生
    Session->>X: アクター停止
    
    Note over Session,System: 🔄 システム復旧
    System->>Session: アクター再起動
    Session->>EventStore: イベント再生
    EventStore-->>Session: セッション状態復元
    User->>Session: API操作試行
    Session-->>User: ✅ 認証OK（シームレス継続）
```

### セッション管理イベント設計

```scala
// UserSessionActor Events
sealed trait UserSessionEvent
case class UserLoggedIn(userAccountId: UserAccountId, sessionId: String, loginTime: Instant) extends UserSessionEvent
case class UserLoggedOut(userAccountId: UserAccountId, sessionId: String, logoutTime: Instant) extends UserSessionEvent
case class SessionExtended(sessionId: String, newExpirationTime: Instant) extends UserSessionEvent
case class SessionExpired(sessionId: String, expiredTime: Instant) extends UserSessionEvent

// UserSessionActor State
case class UserSessionState(
  userAccountId: UserAccountId,
  sessionId: Option[String] = None,
  isLoggedIn: Boolean = false,
  loginTime: Option[Instant] = None,
  lastActivity: Option[Instant] = None,
  expirationTime: Option[Instant] = None
)
```

### 障害復旧戦略

1. **自動復旧**: アクター再起動時にイベント再生で状態復元
2. **セッション有効期限**: 古いセッションの自動無効化
3. **ハートビート**: 定期的なセッション延長メカニズム
4. **冪等性**: 同じセッションIDでの重複ログインを適切に処理

### 改善されたアーキテクチャの利点

✅ **障害耐性**: システム障害後もセッション状態を保持  
✅ **ユーザビリティ**: 再ログイン不要でシームレス継続  
✅ **監査性**: 全てのセッション活動が記録される  
✅ **セキュリティ**: セッション有効期限の厳密な管理  

このアーキテクチャにより、スケーラブルで可監査性、障害耐性の高い書庫管理システムが実現されています。