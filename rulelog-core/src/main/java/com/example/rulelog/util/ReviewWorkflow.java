package com.example.rulelog.util;

/**
 * 脚本评审状态机（纯逻辑，可单测）：
 *
 *   draft ──SUBMIT──► review ──APPROVE──► published
 *            ▲                    │
 *            └──────REJECT────────┘
 *
 * 状态：draft（草稿）/ review（评审中）/ published（已发布）。
 * 合法转换之外的操作抛 IllegalArgumentException（由全局异常处理器转成 400）。
 */
public final class ReviewWorkflow {

    private ReviewWorkflow() {
    }

    public enum Action {
        SUBMIT, APPROVE, REJECT
    }

    /** 执行状态转换，返回新状态；非法转换抛异常 */
    public static String next(String current, Action action) {
        if (action == null) return null;
        if (current == null) {
            throw new IllegalArgumentException("当前状态为空");
        }
        switch (current) {
            case "draft":
                if (action == Action.SUBMIT) return "review";
                break;
            case "review":
                if (action == Action.APPROVE) return "published";
                if (action == Action.REJECT) return "draft";
                break;
            case "published":
                // 已发布为终态，需先在画布"编辑"回到草稿才能再次评审（由 Controller 处理 reopen）
                break;
            default:
                throw new IllegalArgumentException("未知状态: " + current);
        }
        throw new IllegalArgumentException("非法状态转换: " + current + " + " + action);
    }

    /** 状态的中文显示名 */
    public static String label(String status) {
        if (status == null) return "";
        switch (status) {
            case "draft": return "草稿";
            case "review": return "评审中";
            case "published": return "已发布";
            default: return status;
        }
    }
}
