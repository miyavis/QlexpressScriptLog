package com.example.rulelog.util;

import com.example.rulelog.exception.BadRequestException;

/**
 * 请求入参校验（P1）：集中处理脚本名/内容/limit 的边界，避免 Controller 散落判断。
 * 校验失败抛 BadRequestException，由 GlobalExceptionHandler 转成 400。
 */
public final class RequestValidator {

    public static final int MAX_CONTENT_LENGTH = 100_000;   // 脚本上限 100KB
    public static final int MAX_NAME_LENGTH = 200;
    public static final int MAX_LIMIT = 200;
    public static final int MAX_PARAM_KEY_LENGTH = 128;     // 参数名上限
    public static final int MAX_PARAM_COUNT = 200;          // 参数个数上限

    private RequestValidator() {
    }

    /** 校验脚本名与内容：非 blank、长度不超限 */
    public static void validateScript(String name, String content) {
        requireText(name, "脚本名称", MAX_NAME_LENGTH);
        requireText(content, "脚本内容", MAX_CONTENT_LENGTH);
    }

    private static void requireText(String value, String label, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new BadRequestException(label + "不能为空");
        }
        if (value.length() > maxLength) {
            throw new BadRequestException(label + "不能超过 " + maxLength + " 个字符");
        }
    }

    /** limit 归一到 1..MAX_LIMIT；非法值（<=0）或超限回落到上限 */
    public static int normalizeLimit(int limit) {
        if (limit <= 0 || limit > MAX_LIMIT) {
            return MAX_LIMIT;
        }
        return limit;
    }

    /** 执行参数校验：null 合法；参数名长度/个数超限、参数名 blank 均拒绝 */
    public static void validateParams(java.util.Map<String, Object> params) {
        if (params == null || params.isEmpty()) return;
        if (params.size() > MAX_PARAM_COUNT) {
            throw new BadRequestException("参数个数不能超过 " + MAX_PARAM_COUNT);
        }
        for (String key : params.keySet()) {
            if (key == null || key.trim().isEmpty()) {
                throw new BadRequestException("参数名不能为空");
            }
            if (key.length() > MAX_PARAM_KEY_LENGTH) {
                throw new BadRequestException("参数名「" + key + "」过长（上限 " + MAX_PARAM_KEY_LENGTH + "）");
            }
        }
    }
}
