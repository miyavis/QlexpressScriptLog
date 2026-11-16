/**
 * 脚本导出/导入（纯逻辑，可单测）
 * 一份规则脚本 = 一份可分享的 JSON 文件：包含名称、脚本文本、默认参数、画布 meta。
 * 导入时 id 由后端重新生成（POST /api/scripts），所以导出文件不携带内部 id/时间戳。
 */

/** 文件名安全化：替换文件系统非法字符 */
const safeName = (s) => String(s == null ? "" : s).replace(/[/\\:*?"<>|]/g, "_");

/** 时间格式化：20261116-153045 */
const stamp = () => {
  const d = new Date();
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`;
};

/**
 * 构建脚本导出内容：{ filename, body }
 * 正文只保留可分享的部分：name / content / defaultParams / meta（画布布局）。
 */
export const buildScriptExport = (script) => {
  const s = script || {};
  const name = s.name ? String(s.name) : "未命名";
  const filename = `规则脚本_${safeName(name)}_${stamp()}.json`;
  const payload = {
    name,
    content: s.content || "",
    ...(s.defaultParams && typeof s.defaultParams === "object" ? { defaultParams: s.defaultParams } : {}),
    ...(s.meta && typeof s.meta === "object" ? { meta: s.meta } : {})
  };
  const body = JSON.stringify(
    {
      exportedAt: new Date().toISOString(),
      exportedFrom: "QLExpress4 规则执行平台（规则脚本导出）",
      script: payload
    },
    null,
    2
  );
  return { filename, body };
};

/**
 * 解析导入文件文本 → POST /api/scripts 的请求体（id 不传，由后端生成）。
 * 兼容两种格式：带 exportedAt/script 包装的导出文件、裸脚本对象。
 * 非法输入抛中文错误，前端直接提示给业务人员。
 */
export const parseScriptImport = (text) => {
  let obj;
  try {
    obj = JSON.parse(text);
  } catch (e) {
    throw new Error("导入失败：文件不是合法的 JSON 格式");
  }
  const raw = obj && typeof obj === "object" && obj.script && typeof obj.script === "object" ? obj.script : obj;
  if (!raw || typeof raw !== "object" || typeof raw.content !== "string" || !raw.content.trim()) {
    throw new Error("导入失败：文件缺少脚本内容（content）");
  }
  const req = {
    name: raw.name ? String(raw.name) : "导入的脚本",
    content: raw.content,
    defaultParams: raw.defaultParams && typeof raw.defaultParams === "object" && !Array.isArray(raw.defaultParams)
      ? raw.defaultParams
      : {}
  };
  if (raw.meta && typeof raw.meta === "object" && !Array.isArray(raw.meta)) {
    req.meta = raw.meta;
  }
  return req;
};
