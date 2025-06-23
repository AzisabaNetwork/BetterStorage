# BetterStorage

高度で安全なグループ型ストレージ管理プラグイン

## 概要

BetterStorage は、プレイヤーごと・グループごとにインベントリ・お金・タグなどを安全かつ柔軟に管理できる Minecraft サーバー用プラグインです。MariaDBによる永続保存、差分ログによるロールバック、UUIDベースによる高精度な識別が特徴です。

## 特徴

- グループ単位でストレージを管理（共有インベントリ/個人インベントリ）
- UUIDベースの識別（名前変更でも安全）
- インベントリやタグの差分を自動保存
- ロールバック機能搭載（過去の状態に戻せる）
- 他プラグインとの連携を想定したAPI設計
- 別プラグインによるGUI制御が可能（BetterStorageはバックエンド）

## 対応バージョン

- Paper 1.16.5

## 依存関係

- MariaDB
- HikariCP

## インストール

1. `BetterStorage-1.0.6-SNAPSHOT-shaded.jar` を `plugins` フォルダに配置
2. config.yml に MariaDB 接続情報を記入
3. サーバーを起動してテーブルを自動生成

## コマンド

```
/bstorage rollback <groupName/playerName> <yyyy-MM-dd HH:mm:ss>
/bstorage diff <groupName/playerName> <yyyy-MM-dd HH:mm:ss>
/bstorage list <groupName/playerName>
/bstorage help
```

## 差分とロールバックについて

- 保存時に自動で差分ログを記録
- 毎日深夜にロールバック用の完全バックアップを保存
- 差分ログは30日で自動削除

## 開発API

BetterStorageは外部プラグインから `GroupData`, `InventoryData` などのデータ取得や制御が可能です。

## ライセンス

MIT License（予定）

