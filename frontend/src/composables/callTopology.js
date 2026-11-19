/**
 * 调用拓扑纯逻辑（无 Vue 依赖，可单测）：
 * 把执行记录的 ServiceCallLog 树转成扁平节点列表，供前端画拓扑图/耗时排行。
 */

/**
 * 扁平化调用树，每个节点带 depth（层级）与 parentKey（父节点 key）。
 * @param {Array} calls 调用树（顶层数组）
 * @returns {Array<{node, depth, parentKey}>}
 */
export const flattenCalls = (calls) => {
  const out = [];
  const visit = (list, depth, parentKey) => {
    if (!Array.isArray(list)) return;
    for (const call of list) {
      if (!call) continue;
      const key = `${call.serviceName}.${call.methodName}.${call.startTime}`;
      out.push({ ...call, depth, parentKey, key });
      visit(call.children, depth + 1, key);
    }
  };
  visit(calls, 0, null);
  return out;
};

/**
 * 构建拓扑节点：含显示名、耗时占比、是否主导节点、成败。
 * @param {Array} calls 调用树
 * @returns {Array<{key, label, durationMs, share, dominant, success, depth, parentKey}>}
 */
export const buildTopology = (calls) => {
  const flat = flattenCalls(calls);
  if (!flat.length) return [];
  const totalMs = flat.reduce((sum, n) => sum + (n.durationMs || 0), 0);
  return flat.map((n) => {
    const durationMs = n.durationMs || 0;
    const share = totalMs > 0 ? durationMs / totalMs : 0;
    return {
      key: n.key,
      label: n.chineseName || n.serviceName || "调用",
      method: n.methodName,
      durationMs,
      share,
      dominant: share >= 0.5,
      success: n.success !== false,
      depth: n.depth,
      parentKey: n.parentKey
    };
  });
};
