/**
 * 行级日志的业务友好展示逻辑（纯函数，无 Vue 依赖，可单测）
 *
 * 从 store.js 抽取：这些函数只依赖传入的 le/call/expression 数据，
 * 不碰任何响应式状态。store.js 内的 computed 只做「取 selectedLogRecord + 调纯函数」的薄包装。
 */

/* ---------- 通用格式化 ---------- */

/** 标量显示：字符串/数字/布尔直接显示，其他 JSON 化 */
export const fmtScalar = (v) => {
  if (v == null) return "";
  if (typeof v === "string") return v;
  if (typeof v === "number" || typeof v === "boolean") return String(v);
  try {
    return JSON.stringify(v);
  } catch (e) {
    return String(v);
  }
};

/** 截断超长文本，带省略号 */
export const truncate = (s, maxLen) => {
  if (!s) return "";
  return s.length > maxLen ? s.substring(0, maxLen) + "…" : s;
};

/** 服务调用参数格式化：argsJson '["张三"]' → '张三' */
export const callArgs = (call) => {
  try {
    const arr = JSON.parse(call.argsJson || "[]");
    return Array.isArray(arr) ? arr.map(fmtScalar).join(", ") : String(call.argsJson || "");
  } catch (e) {
    return String(call.argsJson || "");
  }
};

/**
 * 构建执行摘要：从执行记录聚合调用/步骤/表达式数量、失败数、去重名称（纯函数）。
 * @param {object} record RuleExecutionRecord
 * @returns 摘要对象；record 为空返回 null
 */
export const buildExecSummary = (record) => {
  if (!record) return null;
  let totalCalls = 0;
  let totalSteps = 0;
  let totalExprs = 0;
  let failedCalls = 0;
  const callNames = new Set();
  const stepNames = new Set();
  for (const le of Object.values(record.lineExecutions || {})) {
    totalCalls += le.calls?.length || 0;
    totalSteps += le.steps?.length || 0;
    totalExprs += le.expressions?.length || 0;
    for (const c of le.calls || []) {
      callNames.add(c.chineseName || c.serviceName);
      if (!c.success) failedCalls++;
    }
    for (const s of le.steps || []) {
      stepNames.add(s.stepName);
    }
  }
  return {
    success: record.success,
    durationMs: record.durationMs,
    totalCalls,
    totalSteps,
    totalExprs,
    failedCalls,
    callNames: Array.from(callNames),
    stepNames: Array.from(stepNames),
    result: record.result,
    errorType: record.errorType,
    errorMessage: record.errorMessage
  };
};

/** 服务调用结果格式化：去掉 JSON 引号 */
export const callResult = (call) => {
  if (call.resultJson == null) return "";
  try {
    return fmtScalar(JSON.parse(call.resultJson));
  } catch (e) {
    return call.resultJson;
  }
};

/** 原始轨迹的单条描述（折叠区内使用） */
export const rawExprDesc = (e) => {
  if (!e) return "";
  const labels = {
    IF: "如果", RETURN: "返回", VARIABLE: "变量", OPERATOR: "运算", VALUE: "值",
    FUNCTION: "函数", METHOD: "函数", BLOCK: "代码块"
  };
  let s = `${labels[e.type] || e.type}${e.token ? `【${e.token}】` : ""}`;
  if (e.evaluated && e.value != null) s += ` = ${fmtScalar(e.value)}`;
  if (!e.evaluated) s += "（未执行）";
  return s;
};

/* ---------- 如果/否则结构解析 ---------- */

/** 解析脚本的如果/否则结构：返回 { map: 行号→'then'|'else', ifLine } */
export const parseBranchInfo = (scriptSnapshot) => {
  const info = { map: {}, ifLine: null };
  if (!scriptSnapshot) return info;
  const lines = scriptSnapshot.split("\n");
  let stage = "none"; // none → then → else → none
  let depth = 0;
  for (let i = 0; i < lines.length; i++) {
    const text = lines[i];
    const opens = (text.match(/\{/g) || []).length;
    const closes = (text.match(/\}/g) || []).length;
    if (stage === "none" && text.includes("如果")) {
      info.ifLine = i + 1;
      stage = "then";
      depth = opens - closes;
      continue;
    }
    if (stage === "then") {
      if (text.includes("否则")) {
        stage = "else";
      } else {
        info.map[i + 1] = "then";
      }
      depth += opens - closes;
      if (depth <= 0) stage = "none";
      continue;
    }
    if (stage === "else") {
      info.map[i + 1] = "else";
      depth += opens - closes;
      if (depth <= 0) stage = "none";
    }
  }
  return info;
};

/* ---------- 表达式轨迹 → 业务描述 ---------- */

const OP_SYMBOLS = { "*": "×", "/": "÷", "=": "=", "+": "+", "-": "-" };

/** 赋值行描述：从表达式轨迹还原 "积分 = 380（来源：…）" */
export const buildLineAssigns = (le) => {
  const exprs = (le.expressions || []).filter(e => e && e.evaluated);
  const eq = exprs.find(e => e.type === "OPERATOR" && e.token === "=");
  if (!eq) return [];
  const assigned = exprs.find(e => e.type === "VARIABLE" && e.value == null);
  const parts = [];
  for (const e of exprs) {
    if (e === eq) continue;
    if (assigned && e === assigned) continue;
    if (e.type === "OPERATOR") {
      parts.push(OP_SYMBOLS[e.token] || e.token);
    } else if (e.type === "VARIABLE") {
      parts.push(e.value != null ? `${e.token}(${fmtScalar(e.value)})` : e.token);
    } else if (e.type === "FUNCTION" || e.type === "METHOD") {
      parts.push(`调用「${e.token}」`);
    } else if (e.type === "VALUE") {
      parts.push(fmtScalar(e.value));
    }
    // IF/RETURN/BLOCK 等结构节点不参与
  }
  // 去除连续重复
  const dedup = [];
  for (const p of parts) {
    if (!dedup.length || dedup[dedup.length - 1] !== p) dedup.push(p);
  }
  // 运算符开头（轨迹父节点先出现）→ 还原成中缀形式：a × b
  let src = "";
  if (dedup.length && dedup[0].length <= 1) {
    const op = dedup[0];
    const operands = dedup.slice(1);
    src = operands.length >= 2 ? operands.join(` ${op} `) : operands.join(" ");
  } else {
    src = dedup.join(" · ");
  }
  const name = assigned ? assigned.token : "结果";
  return [`${name} = ${fmtScalar(eq.value)}${src ? `（${src}）` : ""}`];
};

/** 返回行描述：RETURN 表达式 → "返回结果：打折积分 → 760" */
export const buildReturnDesc = (le) => {
  const ret = (le.expressions || []).find(e => e && e.type === "RETURN" && e.evaluated && e.value != null);
  if (!ret) return null;
  const v = (le.expressions || []).find(e => e && e.type === "VARIABLE" && e.value != null && e.token);
  return `返回结果：${v ? v.token + " → " : ""}${fmtScalar(ret.value)}`;
};
