/**
 * 脚本版本历史（纯逻辑，可单测）
 *
 * 后端约定：脚本每次修改前自动快照（ScriptController → ScriptVersionService），
 * 版本随脚本一起返回（GET /api/scripts/{id} 天然带出，无新增接口）。
 * 前端只做两件事：
 * 1. buildVersionList 把脚本里的版本整成可展示列表（倒序、最多 10 条、过滤残缺项）
 * 2. restoreVersion 生成"载入历史版本"后的编辑器负载——
 *    载入后用户点"保存"即完成回滚；保存动作会先为回滚前那一刻留快照，回滚永远可撤销。
 */

/** 每个脚本最多保留的历史版本数（与后端 ScriptVersionService.MAX_KEEP 一致） */
export const MAX_VERSIONS = 10;

/**
 * 脚本 → 历史版本列表（按 savedAt 倒序，最多 MAX_VERSIONS 条，过滤缺内容的残缺项）
 */
export const buildVersionList = (script) => {
  const raw = script && Array.isArray(script.versions) ? script.versions : [];
  return raw
    .filter((v) => v && typeof v.content === "string" && v.content.trim())
    .sort((a, b) => (b.savedAt || 0) - (a.savedAt || 0))
    .slice(0, MAX_VERSIONS);
};

/**
 * 载入历史版本：生成 PUT /api/scripts/{id} 的请求负载。
 * name/content/defaultParams/meta 取自版本，id 取自当前脚本；非法输入返回 null。
 */
export const restoreVersion = (script, version) => {
  if (!script || !script.id || !version || typeof version.content !== "string" || !version.content.trim()) {
    return null;
  }
  const out = {
    id: script.id,
    name: version.name ? String(version.name) : script.name || "未命名",
    content: version.content,
    defaultParams:
      version.defaultParams && typeof version.defaultParams === "object" && !Array.isArray(version.defaultParams)
        ? version.defaultParams
        : {}
  };
  if (version.meta && typeof version.meta === "object" && !Array.isArray(version.meta)) {
    out.meta = version.meta;
  }
  return out;
};
