package com.example.rulelog.param;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 参数校验器（递归，无 Spring）：按 ParamDef 树校验实际参数。
 * 错误信息定位到字段路径（如 users[1].name）。
 */
public final class ParamValidator {

    private ParamValidator() {
    }

    /** 校验参数，返回错误列表（空表示通过） */
    public static List<String> validate(List<ParamDef> defs, Map<String, Object> params) {
        List<String> errors = new ArrayList<>();
        if (defs == null) return errors;
        Map<String, Object> p = params == null ? java.util.Collections.emptyMap() : params;
        for (ParamDef def : defs) {
            validateOne(def, p.get(def.getName()), def.getName(), errors);
        }
        return errors;
    }

    private static void validateOne(ParamDef def, Object value, String path, List<String> errors) {
        // 必填校验
        if (isBlank(value)) {
            if (def.isRequired()) {
                errors.add(def.getLabel() + "（" + path + "）为必填项");
            }
            return; // 空值不再做类型校验
        }
        String type = def.getType() == null ? "string" : def.getType();
        switch (type) {
            case "number":
                if (!(value instanceof Number)) {
                    errors.add(def.getLabel() + "（" + path + "）必须是数字");
                }
                break;
            case "boolean":
                if (!(value instanceof Boolean)) {
                    errors.add(def.getLabel() + "（" + path + "）必须是布尔值");
                }
                break;
            case "object":
                if (value instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) value;
                    for (ParamDef child : def.getFields()) {
                        validateOne(child, map.get(child.getName()),
                                path + "." + child.getName(), errors);
                    }
                } else {
                    errors.add(def.getLabel() + "（" + path + "）必须是对象");
                }
                break;
            case "array":
                if (value instanceof Collection) {
                    Collection<?> coll = (Collection<?>) value;
                    ParamDef element = def.getElement();
                    if (element != null) {
                        int i = 0;
                        for (Object item : coll) {
                            validateOne(element, item, path + "[" + i + "]", errors);
                            i++;
                        }
                    }
                } else {
                    errors.add(def.getLabel() + "（" + path + "）必须是数组");
                }
                break;
            default: // string
                break;
        }
    }

    private static boolean isBlank(Object v) {
        if (v == null) return true;
        if (v instanceof CharSequence) {
            return ((CharSequence) v).toString().trim().isEmpty();
        }
        if (v instanceof Collection) {
            return ((Collection<?>) v).isEmpty();
        }
        if (v instanceof Map) {
            return ((Map<?, ?>) v).isEmpty();
        }
        return false;
    }
}
