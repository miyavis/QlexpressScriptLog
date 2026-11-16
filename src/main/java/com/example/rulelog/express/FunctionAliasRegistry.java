package com.example.rulelog.express;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 中文函数别名注册表。
 *
 * 记录 QLExpress 脚本中使用的中文函数名 与 Java Service 方法签名的对应关系，
 * 用于在执行日志中显示"与脚本中定义一致的中文"，而不是英文的 Service/方法名。
 *
 * 例如：
 *   脚本里写：查询用户等级(user)
 *   对应 Java：UserService.queryLevel
 *   日志里显示：查询用户等级  而非  UserService.queryLevel
 */
public class FunctionAliasRegistry {

    /** 一条函数注册项：中文函数名 + Java 签名 + 参数提示（供画布做函数下拉与参数占位符） */
    private static final class Entry {
        final String chineseName;
        final String serviceName;
        final String methodName;
        final String paramHint;

        Entry(String chineseName, String serviceName, String methodName, String paramHint) {
            this.chineseName = chineseName;
            this.serviceName = serviceName;
            this.methodName = methodName;
            this.paramHint = paramHint == null ? "" : paramHint;
        }
    }

    /** key: "ServiceName.methodName"，value: 注册项（LinkedHashMap 保持注册顺序，注册发生在启动期，之后只读） */
    private final Map<String, Entry> javaMethodToChinese = new java.util.LinkedHashMap<>();

    public void register(String chineseName, String serviceName, String methodName) {
        register(chineseName, serviceName, methodName, null);
    }

    public void register(String chineseName, String serviceName, String methodName, String paramHint) {
        javaMethodToChinese.put(serviceName + "." + methodName,
                new Entry(chineseName, serviceName, methodName, paramHint));
    }

    /** 根据 Java 方法签名查找脚本中的中文函数名，找不到返回 null */
    public String getChineseName(String serviceName, String methodName) {
        Entry e = javaMethodToChinese.get(serviceName + "." + methodName);
        return e == null ? null : e.chineseName;
    }

    /** 返回所有已注册的中文函数名 */
    public Set<String> getAllChineseNames() {
        Set<String> names = new java.util.HashSet<>();
        for (Entry e : javaMethodToChinese.values()) {
            names.add(e.chineseName);
        }
        return names;
    }

    /** 全部注册项（按注册顺序），供 /api/functions 下发，前端画布据此生成函数下拉与参数提示 */
    public List<Map<String, String>> list() {
        List<Map<String, String>> out = new ArrayList<>();
        for (Entry e : javaMethodToChinese.values()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("chineseName", e.chineseName);
            item.put("serviceName", e.serviceName);
            item.put("methodName", e.methodName);
            item.put("paramHint", e.paramHint);
            out.add(item);
        }
        return out;
    }
}
