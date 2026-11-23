/**
 * 外部跳转深链解析（纯函数，可单测）：
 * - parseDeepLink：从 URL query 解析 scriptId / traceId
 * - pickExecutionRecord：按 traceId 选记录，找不到/不传则回退最新（列表按时间倒序，第一条最新）
 */

/** 解析 query 字符串，返回 { scriptId, traceId }（均做 trim + URL 解码） */
export const parseDeepLink = (search) => {
  const empty = { scriptId: "", traceId: "" };
  if (!search) return empty;
  const raw = String(search).replace(/^\?/, "");
  if (!raw) return empty;
  const params = new URLSearchParams(raw);
  const clean = (v) => {
    if (v == null) return "";
    try { return decodeURIComponent(v).trim(); } catch (e) { return v.trim(); }
  };
  return {
    scriptId: clean(params.get("scriptId")),
    traceId: clean(params.get("traceId"))
  };
};

/**
 * 从执行记录列表中挑选要展示的记录。
 * @param {Array} records 按时间倒序（最新在前）的记录列表
 * @param {string} traceId 指定 traceId；空则取最新
 */
export const pickExecutionRecord = (records, traceId) => {
  if (!Array.isArray(records) || records.length === 0) return null;
  if (traceId) {
    const found = records.find((r) => r && r.traceId === traceId);
    if (found) return found;
  }
  return records[0]; // 最新
};
