/**
 * 执行记录导出（纯逻辑，可单测）
 * 一次执行 = 一份可离线分享的 JSON 文件：traceId 发给别人，对方用文件直接复盘。
 */

/** 文件名安全化：替换文件系统非法字符 */
const safeName = (s) => String(s == null ? "" : s).replace(/[/\\:*?"<>|]/g, "_");

/** 时间格式化：20261116-153045 */
const stamp = (ts) => {
  const d = ts ? new Date(ts) : new Date();
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`;
};

/**
 * 构建导出内容：{ filename, body }
 * 正文在原始记录上补充 exportedAt / exportedFrom，接收方能看懂来源。
 */
export const buildExecutionExport = (record) => {
  const traceId = record && record.traceId ? String(record.traceId) : "unknown";
  const filename = `执行记录_${safeName(traceId)}_${stamp(record && record.createdAt)}.json`;
  const body = JSON.stringify(
    {
      exportedAt: new Date().toISOString(),
      exportedFrom: "QLExpress4 规则执行平台（执行记录导出）",
      ...record
    },
    null,
    2
  );
  return { filename, body };
};
