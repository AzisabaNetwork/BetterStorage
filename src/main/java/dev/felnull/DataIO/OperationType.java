package dev.felnull.DataIO;

public enum OperationType {
    ADD,
    REMOVE,
    UPDATE,
    CLEAR,
    MOVE;

    /**
     * データベースに保存する文字列形式を取得（必要なら小文字にしても可）
     */
    public String toDbString() {
        return this.name(); // 大文字で保存される（例: "ADD"）
    }

    /**
     * 文字列から enum に変換（安全な読み込み用）
     */
    public static OperationType fromDbString(String s) {
        return OperationType.valueOf(s.toUpperCase());
    }
}
