# BetterStorage API ドキュメント

このドキュメントは BetterStorage の外部連携用APIについて記述しています。

## 主要クラスと構造

### GroupData
- `String groupName`: グループ名（表示名）
- `String groupUUID`: 内部識別用UUID（固定）
- `boolean isPrivate`: 個人用グループかどうか
- `String ownerPlugin`: このグループを所有するプラグイン名
- `long version`: バージョン（保存・競合検出用）
- `StorageData storageData`: ストレージ全体のデータ

### StorageData
- `Map<String, InventoryData> storageInventory`: ページIDをキーとしたインベントリ情報
- `double bankMoney`: 銀行資金
- `boolean requireBankPermission`: 銀行利用に権限が必要かどうか

### InventoryData
- `String pageId`: インベントリページのID
- `String displayName`: 表示名
- `int rowCount`: 行数（1〜6）
- `boolean requirePermission`: 権限が必要か
- `Map<Integer, ItemStack> itemSlot`: スロット番号とItemStackのマップ
- `List<String> userTags`: タグのリスト
- `long version`: ページ単位でのバージョン番号

---

## DataIO クラスのメソッド一覧

### static GroupData loadGroupData(String groupName)
指定したグループ名のデータをデータベースから読み込みます。

### static GroupData loadGroupData(UUID groupUUID)
UUIDベースでグループデータを読み込みます。

### static void saveGroupData(GroupData groupData)
グループ全体のデータを保存します（全ページ・メンバー・タグ含む）。

### static void saveGroupData(GroupData groupData, long expectedVersion)
指定されたバージョンと一致する場合のみ保存します（楽観的排他制御）。

### static boolean saveInventoryOnly(DatabaseManager db, GroupData groupData, String pageId)
ページ単位でインベントリだけを保存。バージョン整合性チェックも含まれます。

---

## Rollback & DiffLog

### RollbackLogManager
- `saveRollbackLog(GroupData groupData)`: グループ全体の完全スナップショットを保存
- `restoreGroupFromRollback(String groupUUID, LocalDateTime timestamp)`: 指定時点のバックアップから復元
- `getRollbackTimestamps(String groupUUID)`: すべてのバックアップ時刻を取得

### DiffLogManager
- `saveDiffLogs(DatabaseManager db, GroupData groupData)`: 差分ログ（ページごと）を保存
- `restoreGroupFromDiffLog(DatabaseManager db, GroupData groupData, LocalDateTime timestamp)`: 差分ログから復元（ページごと）

---

## タグ検索・ユーザーアクセス判定（予定機能）

### StorageAPI（将来追加予定）
- タグに基づくページフィルタリング
- ユーザーの所属グループ・権限判定
- 他プラグインからのCRUD操作（安全なラッパー）

---

## データベース構造
（詳しくは `TableInitializer` を参照）
- group_table
- group_member_table
- storage_table
- inventory_table
- inventory_item_table
- tag_table
- inventory_item_log
- rollback_log

---

## 注意
- 外部プラグインは `GroupManager.getGroupByUUID(UUID)` または `GroupManager.getGroupByName(String)` を通じて `GroupData` を取得可能。
- `GroupData` を直接変更した場合、`DataIO.saveGroupData()` によって保存が必要。
- 差分保存の際は `saveInventoryOnly` を使うと効率的。

---

## バージョン
BetterStorage 1.0.6-SNAPSHOT 用API仕様書

