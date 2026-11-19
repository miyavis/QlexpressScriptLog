/**
 * 列表筛选（纯逻辑，可单测）
 *
 * 场景：脚本和执行记录多了以后，业务人员需要快速找到目标：
 * 1. filterScripts   脚本列表搜索（名称/ID，不区分大小写）
 * 2. filterExecutions 执行记录筛选（成功/失败状态 + traceId/脚本名关键字）
 */

const norm = (s) => String(s == null ? "" : s).toLowerCase();

/**
 * 脚本列表搜索：关键字匹配名称或 ID；可叠加评审状态/环境筛选。
 * @param reviewStatus 可选 "draft"|"review"|"published"|""（空=全部）
 * @param environment 可选 "dev"|"staging"|"prod"|""（空=全部）
 */
export const filterScripts = (scripts, keyword, reviewStatus, environment) => {
  if (!Array.isArray(scripts)) {
    return [];
  }
  const k = norm(keyword).trim();
  return scripts.filter((s) => {
    if (!s) return false;
    if (k && !(norm(s.name).includes(k) || norm(s.id).includes(k))) return false;
    if (reviewStatus && (s.reviewStatus || "draft") !== reviewStatus) return false;
    if (environment && (s.environment || "dev") !== environment) return false;
    return true;
  });
};

/**
 * 执行记录筛选。
 * @param cond.status "ok" 只看成功 | "fail" 只看失败 | 其他/空 全部
 * @param cond.keyword 匹配 traceId 或脚本名（不区分大小写），空则不筛
 */
export const filterExecutions = (records, cond) => {
  if (!Array.isArray(records)) {
    return [];
  }
  const c = cond || {};
  const k = norm(c.keyword).trim();
  return records.filter((r) => {
    if (!r) return false;
    if (c.status === "ok" && !r.success) return false;
    if (c.status === "fail" && r.success) return false;
    if (k && !(norm(r.traceId).includes(k) || norm(r.scriptName).includes(k))) return false;
    return true;
  });
};
