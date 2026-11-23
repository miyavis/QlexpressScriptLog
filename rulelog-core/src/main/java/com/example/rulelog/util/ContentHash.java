package com.example.rulelog.util;

/**
 * 内容 hash（FNV-1a 32 位）——与前端 canvasLogic.js 的 hashContent 完全一致。
 *
 * 用于内置示例脚本的 meta.contentHash：前端靠它判断"保存的卡片是否仍匹配脚本文本"，
 * 匹配则直接复用决策表卡（不会被重新解析成普通 if 卡）。
 *
 * 注意：JS 用 charCodeAt（UTF-16 code unit），Java char 同样是 UTF-16 code unit，
 * 因此逐字符运算一致；所有移位/加法在 int（32 位）上溢出，与 JS 的 32 位位运算等价。
 */
public final class ContentHash {

    private ContentHash() {
    }

    public static String hashContent(String content) {
        String str = content == null ? "" : content;
        int hash = 0x811c9dc5;
        for (int i = 0; i < str.length(); i++) {
            hash ^= str.charAt(i);
            hash = hash + (hash << 1) + (hash << 4) + (hash << 7) + (hash << 8) + (hash << 24);
        }
        return String.format("%08x", hash & 0xffffffffL);
    }
}
