/**
 * 函数注册表（纯逻辑，可单测）
 *
 * 产品化约定：平台可调用的中文函数不再写死在前端，而是来自后端 /api/functions
 * （由 Express4Engine 注册、FunctionRegistryController 下发）。前端只做三件事：
 * 1. normalizeFunctions 把后端数据整成下拉项（并过滤脚本语句类的 记录日志）
 * 2. suggestVarName 拖入取数卡时按函数名推荐结果变量名，少打字
 * 3. collectKnownVariables 汇总当前画布已定义的变量，给表达式输入做补全
 */

/** 内置兜底：注册表拉取失败时至少保证原有三个函数可用 */
export const BUILTIN_FUNCTIONS = [
  { name: "查询用户等级", service: "UserService", method: "queryLevel", paramHint: "user" },
  { name: "查询用户积分", service: "UserService", method: "queryPoints", paramHint: "user" },
  { name: "计算折扣", service: "DiscountService", method: "calcDiscount", paramHint: "user" }
];

/** 后端注册项 → 前端下拉项；过滤 记录日志（脚本语句，不属于取数卡）与无中文名的条目 */
export const normalizeFunctions = (rawList) => {
  if (!Array.isArray(rawList)) {
    return [];
  }
  return rawList
    .filter((f) => f && f.chineseName && f.chineseName !== "记录日志")
    .map((f) => ({
      name: f.chineseName,
      service: f.serviceName || "",
      method: f.methodName || "",
      paramHint: f.paramHint || "",
      desc: f.desc || "",
      params: Array.isArray(f.params) ? f.params : []
    }));
};

/** 按函数名推荐结果变量名：去掉查询/计算/获取/判断动词前缀 */
export const suggestVarName = (funcName) => {
  const name = String(funcName || "");
  return name.replace(/^(查询|计算|获取|判断)/, "") || name;
};

/** 函数下拉选项合并：注册表在前，脚本里用到但未注册的函数追加为占位项（保证 select 如实显示） */
export const mergeFuncOptions = (registryList, usedFuncs) => {
  const out = [];
  const seen = new Set();
  const push = (f) => {
    if (f && f.name && !seen.has(f.name)) {
      seen.add(f.name);
      out.push(f);
    }
  };
  for (const f of Array.isArray(registryList) ? registryList : []) {
    push(f);
  }
  for (const u of Array.isArray(usedFuncs) ? usedFuncs : []) {
    const name = String(u || "").trim();
    if (name) {
      push({ name, service: "", method: "", paramHint: "" });
    }
  }
  return out;
};

/** 切换函数时的参数迁移：空参或仍是某函数默认提示值 → 换成新函数的 hint；用户自选变量不动 */
export const nextParamOnFuncChange = (param, newHint, knownHints) => {
  const p = String(param == null ? "" : param).trim();
  const hints = (Array.isArray(knownHints) ? knownHints : [])
    .map((h) => String(h == null ? "" : h).trim())
    .filter(Boolean);
  if (p && !hints.includes(p)) {
    return p; // 用户自选的变量/表达式，不接管
  }
  return String(newHint == null ? "" : newHint).trim() || p;
};

/** 汇总当前画布已定义的变量：入参键在前，各卡片结果变量按出现顺序追加、去重 */
export const collectKnownVariables = (cards, paramKeys) => {
  const out = [];
  const seen = new Set();
  const push = (v) => {
    const name = String(v || "").trim();
    if (name && !seen.has(name)) {
      seen.add(name);
      out.push(name);
    }
  };
  for (const k of Array.isArray(paramKeys) ? paramKeys : []) {
    push(k);
  }
  for (const c of Array.isArray(cards) ? cards : []) {
    if (c && (c.type === "call" || c.type === "prepcall" || c.type === "calc")) {
      push(c.varName);
    }
  }
  return out;
};
