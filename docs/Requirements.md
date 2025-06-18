    # HandyBookshelf プロジェクト要求分析

    ## 1. プロジェクト概要
    HandyBookshelfは、所有する本の管理・整
    理システムです。本は一意の識別子で管理
    され、ユーザは所有する本の位置を把握で
    きます。

    ## 2. 技術アーキテクチャ
    **言語・フレームワーク:** Scala
    3.7.0、Cats Effect 3、http4s、Tapir
    **モジュール構成:**
    4層アーキテクチャ（util → domain →
    adopter → controller）
    **サーバ:** localhost:8080で動作、API
    + Swagger UI

    ## 3. 現在実装されている機能

    ### 3.1 ドメインモデル
    - **Book** (`domain/Book.scala:4`):
    本の基本情報（BookId、タイトル）
    - **BookId**
    (`domain/BookId.scala:7`): ULIDベース
    の一意識別子、ISBNから生成可能
    - **BookReference**
    (`domain/Book.scala:12`):
    本＋タグ＋デバイス情報
    - **ISBN** (`util/ISBN.scala:6`):
    Iron制約型（10桁または13桁の数字）

    ### 3.2 外部連携
    - **KokkaiToshokanAccessor** (`adopter
    /KokkaiToshokanAccessor.scala:17`):
    国会図書館APIから書籍情報取得

    ### 3.3 API
    - **User API** (`controller/api/endpoi
    nts/UserEndpoints.scala:18,24`):
    GET/POST endpoints
    - 基本的なHTTPサーバ機能のみ実装済み

    ## 4. 要求分析

    ### 4.1 機能要求
    1. **本の一意管理**
       - ✅ 実装済み:
    BookId（ULID）による一意識別
       - ✅ 実装済み: ISBN制約型による検証

    2. **本の位置管理**
       - ❌ 未実装: 物理的な配置場所の管理
       - ❌ 未実装: デジタル端末（Kindle、
    PDF）の管理は部分実装のみ

    3. **外部データ連携**
       - ✅ 実装済み: 国会図書館API連携

    4. **ユーザセッション管理**
       - ❌ 未実装:
    Actorベースのセッション管理
       - ❌ 未実装: ユーザ認証・認可

    ### 4.2 技術要求
    1. **Cats Effect + Actor**
       - ✅ 実装済み: Cats Effect 3
       - ❌ 未実装:
    Actorシステム（Akka/Pekko）

    2. **データ永続化**
       - ❌ 未実装: データベース層
       - ❌ 未実装: データ永続化メカニズム

    ### 4.3 追加実装が必要な領域
    1. **位置管理システム**:
    物理・デジタル配置の追跡
    2. **Actorシステム**:
    ユーザセッション管理
    3. **データ永続化**:
    Repository層とDB接続
    4. **認証・認可**: ユーザ管理機能
    5. **Book CRUD API**:
    本の登録・更新・削除・検索

    現在のコードは基盤となるドメインモデル
    とHTTPサーバが実装済みで、本格的な本管
    理機能の実装準備が整っています。
