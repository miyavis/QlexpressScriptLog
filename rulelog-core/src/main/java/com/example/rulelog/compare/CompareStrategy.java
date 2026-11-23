package com.example.rulelog.compare;

/**
 * 字段值比对策略（纯逻辑，无 Spring）。
 *
 * 支持策略：
 * - equal          相等（null 与 "" 视为一致；数值相等按字符串比较）
 * - notEqual       不等
 * - contains       左值包含右值
 * - gt / gte       大于 / 大于等于（数值）
 * - lt / lte       小于 / 小于等于（数值）
 * - numericTolerance 数值差在容差内
 */
public final class CompareStrategy {

    private CompareStrategy() {
    }

    /** 比对两个值，返回结论（含说明） */
    public static CompareRow.CellResult compare(Object a, Object b, String strategy, double tolerance) {
        if (strategy == null) strategy = "equal";
        boolean ok;
        switch (strategy) {
            case "notEqual":
                ok = !valueEquals(a, b);
                break;
            case "contains":
                ok = String.valueOf(nullToEmpty(a)).contains(String.valueOf(nullToEmpty(b)));
                break;
            case "gt":
                ok = num(a) > num(b);
                break;
            case "gte":
                ok = num(a) >= num(b);
                break;
            case "lt":
                ok = num(a) < num(b);
                break;
            case "lte":
                ok = num(a) <= num(b);
                break;
            case "numericTolerance":
                ok = Math.abs(num(a) - num(b)) <= tolerance;
                break;
            case "equal":
            default:
                ok = valueEquals(a, b);
                break;
        }
        String detail = ok
                ? "一致"
                : fmt(a) + " ≟ " + fmt(b) + "（策略:" + strategy + (tolerance > 0 ? ", 容差:" + tolerance : "") + "）";
        return new CompareRow.CellResult(ok, detail);
    }

    /** null 与空串视为相等；否则字符串化比较（兼容数值与字符串混合） */
    private static boolean valueEquals(Object a, Object b) {
        String sa = nullToEmpty(a);
        String sb = nullToEmpty(b);
        // 数值相等（100 与 100.0 视为相等）
        if (isNumber(a) && isNumber(b)) {
            return Double.compare(num(a), num(b)) == 0;
        }
        return sa.equals(sb);
    }

    private static String nullToEmpty(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static boolean isNumber(Object v) {
        return v instanceof Number || (v instanceof String && ((String) v).trim().matches("[+-]?[0-9]*\\.?[0-9]+"));
    }

    private static double num(Object v) {
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof String) {
            try {
                return Double.parseDouble(((String) v).trim());
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    private static String fmt(Object v) {
        return v == null ? "null" : String.valueOf(v);
    }
}
