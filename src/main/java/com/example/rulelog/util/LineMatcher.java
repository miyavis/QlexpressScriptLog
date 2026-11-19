package com.example.rulelog.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 行号匹配器：在同一脚本中某中文函数名出现多次时，按出现顺序依次分配行号。
 * 每次 next(name) 返回该函数名下一个尚未消费的匹配行（1-based），用完返回 null。
 *
 * 用于把多次服务调用的日志准确归到各自所在行，而不是全部堆到第一次出现的行。
 */
public final class LineMatcher {

    private final String[] lines;
    /** 每个函数名当前已消费到的行索引（下一个从该索引+1 开始找） */
    private final Map<String, Integer> consumed = new HashMap<>();

    public LineMatcher(String[] lines) {
        this.lines = lines == null ? new String[0] : lines;
    }

    public Integer next(String chineseName) {
        if (chineseName == null || chineseName.isEmpty()) {
            return null;
        }
        int from = consumed.getOrDefault(chineseName, 0); // 已消费到的 0-based 行索引
        for (int i = from; i < lines.length; i++) {
            if (lines[i] != null && lines[i].contains(chineseName)) {
                consumed.put(chineseName, i + 1); // 下次从下一行开始
                return i + 1;
            }
        }
        return null;
    }
}
