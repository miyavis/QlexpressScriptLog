package com.example.rulelog.util;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 执行错误摘要器：把 QLExpress / Java 的英文异常翻译成业务人员能看懂的一句话——
 * "第几行 + 错的哪一类 + 出错行内容"，并保留原始异常信息便于开发排查。
 *
 * 分类规则（按优先级匹配）：
 *   NullPointerException            → 空值错误
 *   ClassCastException              → 类型不匹配
 *   ArithmeticException             → 数值计算错误
 *   message 含 not found / undefined → 变量或函数未定义
 *   其余                            → 规则运行错误
 *
 * 行号提取：异常 message 中的 "line N"（QLExpress 报错均带此格式），
 * N 超出脚本行数或提取不到时行号置空。
 */
public final class ErrorSummarizer {

    /** 异常 message 中 "line N" 的匹配（QLExpress 的报错格式） */
    private static final Pattern LINE_PATTERN = Pattern.compile("line\\s+(\\d+)", Pattern.CASE_INSENSITIVE);

    private ErrorSummarizer() {
    }

    /** 错误摘要：lineNo(第几行，可能为 null) / category(中文分类) / detail(摘要+原始信息+出错行内容) */
    public static final class Summary {
        public final Integer lineNo;
        public final String category;
        public final String detail;

        Summary(Integer lineNo, String category, String detail) {
            this.lineNo = lineNo;
            this.category = category;
            this.detail = detail;
        }
    }

    /**
     * @param e     执行抛出的异常
     * @param lines 脚本按行拆分后的内容（用于带出出错行的原文）
     */
    public static Summary summarize(Throwable e, List<String> lines) {
        Integer lineNo = extractLineNo(e, lines);
        String category = describeCategory(e);
        StringBuilder detail = new StringBuilder();
        if (lineNo != null) {
            detail.append("第 ").append(lineNo).append(" 行出错：");
        }
        detail.append(category);
        String raw = rawMessage(e);
        if (!raw.isEmpty()) {
            detail.append("（原始信息：").append(raw).append("）");
        }
        if (lineNo != null && lines != null && lineNo <= lines.size()) {
            detail.append("，出错行内容：").append(lines.get(lineNo - 1).trim());
        }
        return new Summary(lineNo, category, detail.toString());
    }

    private static String rawMessage(Throwable e) {
        String msg = e.getMessage();
        if (msg == null || msg.trim().isEmpty()) {
            msg = e.toString();
        }
        return msg.trim();
    }

    private static Integer extractLineNo(Throwable e, List<String> lines) {
        String msg = e.getMessage();
        if (msg == null || lines == null) {
            return null;
        }
        Matcher m = LINE_PATTERN.matcher(msg);
        if (!m.find()) {
            return null;
        }
        try {
            int n = Integer.parseInt(m.group(1));
            return (n >= 1 && n <= lines.size()) ? n : null;
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    private static String describeCategory(Throwable e) {
        if (e instanceof NullPointerException) {
            return "空值错误";
        }
        if (e instanceof ClassCastException) {
            return "类型不匹配";
        }
        if (e instanceof ArithmeticException) {
            return "数值计算错误";
        }
        String msg = e.getMessage() == null ? "" : e.getMessage();
        String lower = msg.toLowerCase();
        if (lower.contains("not found") || lower.contains("undefined")) {
            return "变量或函数未定义";
        }
        return "规则运行错误";
    }
}
