/**
 * 行级 diff（纯逻辑，可单测）
 *
 * 场景：历史版本面板里，载入旧版本前先看"当前 ↔ 该版本"差异，
 * 用经典 LCS（最长公共子序列）算法对脚本按行比较：
 *   same   = 两边都有（公共行）
 *   remove = 当前有、版本没有（回滚会删掉的行）
 *   add    = 版本有、当前没有（回滚会加回来的行）
 * 脚本通常几十行，O(n*m) DP 足够。
 */

/** 按行拆分（\n），统一去除结尾空行的影响 */
const toLines = (text) => (typeof text === "string" && text ? text.split("\n") : []);

/** LCS 表 */
const lcsTable = (a, b) => {
  const m = a.length;
  const n = b.length;
  const dp = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0));
  for (let i = m - 1; i >= 0; i--) {
    for (let j = n - 1; j >= 0; j--) {
      dp[i][j] = a[i] === b[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1]);
    }
  }
  return dp;
};

/**
 * 当前内容 ↔ 版本内容 的行级差异。
 * @param current 当前脚本文本（null 视为空 → 版本内容全部为 add）
 * @param version 历史版本脚本文本（null 视为空 → 当前内容全部为 remove）
 * @returns [{ type: "same"|"remove"|"add", text }]
 */
export const diffLines = (current, version) => {
  const a = toLines(current);
  const b = toLines(version);
  if (a.length === 0 && b.length === 0) return [];
  const dp = lcsTable(a, b);
  const rows = [];
  let i = 0;
  let j = 0;
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) {
      rows.push({ type: "same", text: a[i] });
      i++;
      j++;
    } else if (dp[i + 1][j] >= dp[i][j + 1]) {
      rows.push({ type: "remove", text: a[i] });
      i++;
    } else {
      rows.push({ type: "add", text: b[j] });
      j++;
    }
  }
  while (i < a.length) {
    rows.push({ type: "remove", text: a[i] });
    i++;
  }
  while (j < b.length) {
    rows.push({ type: "add", text: b[j] });
    j++;
  }
  return rows;
};

/** diff 行 → 汇总：新增/删除行数、是否有差异 */
export const renderDiffRows = (rows) => {
  const list = Array.isArray(rows) ? rows : [];
  const addCount = list.filter((r) => r.type === "add").length;
  const removeCount = list.filter((r) => r.type === "remove").length;
  return { addCount, removeCount, changed: addCount + removeCount > 0 };
};
