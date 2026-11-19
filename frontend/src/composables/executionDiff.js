/**
 * 两次执行记录对比（纯函数，可单测）：列出参数/结果/成败/耗时/调用的差异项。
 */

/** 把任意值转成可比较的显示文本 */
const show = (v) => {
  if (v == null) return "—";
  if (typeof v === "object") {
    try { return JSON.stringify(v); } catch (e) { return String(v); }
  }
  return String(v);
};

/**
 * @returns {Array<{field, left, right}>} 差异列表；相同返回空数组
 */
export const buildExecutionDiff = (a, b) => {
  if (!a || !b) return [];
  const out = [];
  const cmp = (field, left, right) => {
    if (show(left) !== show(right)) out.push({ field, left: show(left), right: show(right) });
  };
  cmp("执行状态", a.success ? "成功" : "失败", b.success ? "成功" : "失败");
  cmp("返回值", a.result, b.result);
  cmp("入参", a.params, b.params);
  if (a.durationMs != null && b.durationMs != null) {
    cmp("耗时(ms)", a.durationMs, b.durationMs);
  }
  // 服务调用：按显示名对比耗时
  const callMap = (rec) => {
    const m = {};
    for (const c of rec.calls || []) m[c.chineseName || c.serviceName || "?"] = c.durationMs;
    return m;
  };
  const ca = callMap(a); const cb = callMap(b);
  const callNames = Array.from(new Set([...Object.keys(ca), ...Object.keys(cb)]));
  for (const name of callNames) {
    if (ca[name] !== cb[name]) {
      out.push({ field: `调用:${name}`, left: ca[name] == null ? "未调用" : ca[name] + "ms", right: cb[name] == null ? "未调用" : cb[name] + "ms" });
    }
  }
  return out;
};
