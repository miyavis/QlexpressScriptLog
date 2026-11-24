package com.example.rulelog.compare;

import com.example.rulelog.util.ContextResolver;

import java.util.*;

/**
 * 比对引擎内核（无 Spring）：按规则配置取数 → 按 key 对齐 → 逐字段比对。
 *
 * 取数通过 Fetcher 回调注入（平台层接真实函数/脚本，SDK 接任意实现），保持 core 纯逻辑。
 */
public class CompareEngine {

    /** 取数回调：sourceType + sourceName + 已解析参数 → 数据（Map 或 List&lt;Map&gt;） */
    @FunctionalInterface
    public interface Fetcher {
        Object fetch(String sourceType, String sourceName, Map<String, Object> params);
    }

    private final Fetcher fetcher;

    public CompareEngine(Fetcher fetcher) {
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher 不能为空");
    }

    /** 执行比对规则 */
    @SuppressWarnings("unchecked")
    public CompareResult run(CompareRule rule, Map<String, Object> inputContext) {
        long start = System.currentTimeMillis();
        CompareResult result = new CompareResult();
        if (rule == null) return result;

        // 可变上下文（取数节点产出写入此处）
        Map<String, Object> context = new LinkedHashMap<>();
        if (inputContext != null) context.putAll(inputContext);

        // 1. 按依赖拓扑顺序执行取数节点（DAG）
        for (FetchNode node : topologicalOrder(rule.getFetchNodes())) {
            Object data;
            if ("CONTEXT".equalsIgnoreCase(node.getSourceType())) {
                // 直接引用上游取数卡的结果变量（如 ${等级} / ${积分}）
                data = ContextResolver.resolvePath(context, node.getSourceName());
            } else {
                Map<String, Object> params = ContextResolver.resolveParams(node.getParamMapping(), context);
                data = fetcher.fetch(node.getSourceType(), node.getSourceName(), params);
            }
            if (node.getResultKey() != null && !node.getResultKey().trim().isEmpty()) {
                context.put(node.getResultKey(), data);
            }
        }

        // 2. 准备比对节点
        CompareNode cmp = rule.getCompareNode();
        String keyField = cmp.getKeyField();
        List<String> sourceKeys = cmp.getSourceKeys();
        if (keyField == null || sourceKeys == null || sourceKeys.size() < 2) {
            result.setElapsedMillis(System.currentTimeMillis() - start);
            return result;
        }

        // 每个数据源规范化为「key值 → 行(Map)」
        Map<String, Map<String, Map<String, Object>>> sourceRows = new LinkedHashMap<>();
        Set<String> allKeys = new LinkedHashSet<>();
        for (String sk : sourceKeys) {
            Object raw = context.get(sk);
            Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
            for (Map<String, Object> row : toRows(raw)) {
                Object kv = row.get(keyField);
                String key = kv == null ? "" : String.valueOf(kv);
                byKey.put(key, row);
                allKeys.add(key);
            }
            sourceRows.put(sk, byKey);
        }

        // 3. 按 key 逐行比对
        for (String key : allKeys) {
            CompareRow out = new CompareRow();
            out.setKey(key);
            Map<String, Map<String, Object>> values = out.getSourceValues();
            for (String sk : sourceKeys) {
                Map<String, Object> row = sourceRows.get(sk).get(key);
                values.put(sk, row == null ? new LinkedHashMap<>() : row);
            }
            // 逐字段比对
            for (FieldCompare fc : cmp.getFields()) {
                Object baseline = values.get(sourceKeys.get(0)).get(fc.getField());
                CompareRow.CellResult cell = null;
                for (int i = 1; i < sourceKeys.size(); i++) {
                    Object other = values.get(sourceKeys.get(i)).get(fc.getField());
                    CompareRow.CellResult c = CompareStrategy.compare(baseline, other, fc.getStrategy(), fc.getTolerance());
                    if (cell == null || !c.isConsistent()) cell = c;
                }
                if (cell == null) cell = new CompareRow.CellResult(true, "一致");
                out.getCells().put(fc.getField(), cell);
                if (!cell.isConsistent()) out.setConsistent(false);
            }
            result.addRow(out);
        }

        result.summarize();
        result.setElapsedMillis(System.currentTimeMillis() - start);
        return result;
    }

    /**
     * 取数节点依赖拓扑排序（DAG）。
     * 依赖来源：
     * - 显式 dependsOn（节点 ID）
     * - paramMapping 中引用了其他节点的 resultKey（如 ${A}、${A[0].name}）→ 自动推断
     * 出现环时抛出明确异常，避免静默产生错误数据。
     */
    private List<FetchNode> topologicalOrder(List<FetchNode> nodes) {
        if (nodes == null || nodes.isEmpty()) return new ArrayList<>();
        // 无 ID 的节点补一个稳定 ID（拓扑排序需要）
        for (int i = 0; i < nodes.size(); i++) {
            FetchNode n = nodes.get(i);
            if (n.getId() == null || n.getId().trim().isEmpty()) n.setId("__node_" + i);
        }
        Map<String, FetchNode> byId = new LinkedHashMap<>();
        Map<String, FetchNode> byResultKey = new LinkedHashMap<>();
        for (FetchNode n : nodes) {
            if (n.getId() != null) byId.put(n.getId(), n);
            if (n.getResultKey() != null && !n.getResultKey().trim().isEmpty()) {
                byResultKey.put(n.getResultKey(), n);
            }
        }

        // 每个节点的依赖集合（去重，排除自身）
        Map<String, Set<String>> deps = new LinkedHashMap<>();
        for (FetchNode n : nodes) {
            Set<String> d = new LinkedHashSet<>();
            if (n.getDependsOn() != null) {
                for (String id : n.getDependsOn()) {
                    if (id != null && !id.equals(n.getId()) && byId.containsKey(id)) d.add(id);
                }
            }
            if (n.getParamMapping() != null) {
                for (String expr : n.getParamMapping().values()) {
                    if (expr == null) continue;
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\$\\{([^}]+)\\}").matcher(expr);
                    while (m.find()) {
                        String path = m.group(1).trim();
                        // 取路径首段作为结果 Key：A / A[0] / A.name
                        String head = path.split("[\\[.]")[0];
                        FetchNode producer = byResultKey.get(head);
                        if (producer != null && producer.getId() != null && !producer.getId().equals(n.getId())) {
                            d.add(producer.getId());
                        }
                    }
                }
            }
            deps.put(n.getId(), d);
        }

        List<FetchNode> ordered = new ArrayList<>();
        Set<String> emitted = new LinkedHashSet<>();
        while (ordered.size() < nodes.size()) {
            FetchNode next = null;
            for (FetchNode n : nodes) {
                if (emitted.contains(n.getId())) continue;
                Set<String> d = deps.get(n.getId());
                if (d == null || emitted.containsAll(d)) { next = n; break; }
            }
            if (next == null) {
                throw new IllegalArgumentException("取数节点存在循环依赖，无法执行 DAG");
            }
            ordered.add(next);
            emitted.add(next.getId());
        }
        return ordered;
    }

    /** 把取数结果规范化为行列表：Map → 单行；List → 逐元素（Map 或标量包装） */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toRows(Object raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (raw == null) return out;
        if (raw instanceof Map) {
            out.add((Map<String, Object>) raw);
        } else if (raw instanceof Collection) {
            for (Object item : (Collection<Object>) raw) {
                if (item instanceof Map) out.add((Map<String, Object>) item);
                else {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("value", item);
                    out.add(m);
                }
            }
        } else {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("value", raw);
            out.add(m);
        }
        return out;
    }
}
