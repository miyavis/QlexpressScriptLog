/**
 * 决策表 → 中文脚本（纯函数，可单测）。
 *
 * 决策表结构：
 *   { strategy: 'first'|'collect', resultVar, conditions:[变量名], rows:[{cells:[运算符+值], result}], defaultValue }
 * - First：嵌套如果/否则，首行命中即赋值并跳出；末尾 else 用默认值
 * - Collect：结果变量初始化为默认值，每命中一行累加结果值（适合计分/折扣叠加场景）
 */

/** 新建一张默认决策表 */
export const emptyDecisionTable = () => ({
  strategy: "first",
  resultVar: "结果",
  conditions: ["条件1", "条件2"],
  rows: [{ cells: ["", ""], result: "" }],
  defaultValue: "0"
});

/** 拼一行的条件表达式：条件i + 运算符值i，用 && 连接 */
const buildCondition = (conditions, cells) => {
  const parts = [];
  for (let i = 0; i < conditions.length; i++) {
    const cell = (cells[i] || "").trim();
    if (!cell) continue;
    const name = (conditions[i] || "").trim();
    if (!name) continue;
    // cell 形如 "> 100" / '== "VIP"' / "大于 100"——直接拼接
    parts.push(`${name} ${cell}`);
  }
  return parts.join(" && ");
};

/**
 * 生成脚本。
 * @param {object} table 决策表
 * @returns {string} 中文脚本文本
 */
export const genDecisionScript = (table) => {
  if (!table || !Array.isArray(table.rows) || table.rows.length === 0) {
    return "// 决策表暂无规则";
  }
  const resultVar = (table.resultVar || "结果").trim() || "结果";
  const defaultValue = table.defaultValue == null || table.defaultValue === "" ? "0" : table.defaultValue;
  const conditions = Array.isArray(table.conditions) ? table.conditions : [];
  const rows = table.rows.filter((r) => r && Array.isArray(r.cells));

  if (table.strategy === "collect") {
    const lines = [`${resultVar} = ${defaultValue};`];
    for (const row of rows) {
      const cond = buildCondition(conditions, row.cells);
      if (!cond) continue;
      lines.push(`如果 (${cond}) 则 {`);
      lines.push(`    ${resultVar} = ${resultVar} + ${row.result || 0};`);
      lines.push("}");
    }
    return lines.join("\n");
  }

  // First：嵌套如果/否则，最后 else 为默认值
  const valid = rows.filter((r) => buildCondition(conditions, r.cells));
  if (valid.length === 0) {
    return `${resultVar} = ${defaultValue};`;
  }
  const lines = [];
  let indent = "";
  for (const row of valid) {
    const cond = buildCondition(conditions, row.cells);
    lines.push(`${indent}如果 (${cond}) 则 {`);
    lines.push(`${indent}    ${resultVar} = ${row.result || 0};`);
    lines.push(`${indent}} 否则 {`);
    indent += "    ";
  }
  // 最内层 else 体 = 默认值，然后逐层闭合（行数与 valid 一致）
  lines.push(`${indent}${resultVar} = ${defaultValue};`);
  for (let i = 0; i < valid.length; i++) {
    indent = indent.slice(0, -4);
    lines.push(`${indent}}`);
  }
  return lines.join("\n");
};
