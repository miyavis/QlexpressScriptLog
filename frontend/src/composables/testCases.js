/**
 * 场景测试纯逻辑（无 Vue 依赖，可单测）：
 * 一组用例 = [{id, name, paramsJson}]，批量执行后汇总通过率。
 */

let seq = 0;

/** 新建一个空用例 */
export const newTestCase = () => ({
  id: `case_${Date.now().toString(36)}_${++seq}`,
  name: `用例 ${seq}`,
  paramsJson: "{}"
});

/** 解析用例参数 JSON：空→{}，非法→null */
export const parseCaseParams = (text) => {
  const s = (text || "").trim();
  if (!s) return {};
  try {
    const obj = JSON.parse(s);
    return obj && typeof obj === "object" && !Array.isArray(obj) ? obj : null;
  } catch (e) {
    return null;
  }
};

/** 汇总批量执行结果 */
export const summarizeResults = (results) => {
  const list = Array.isArray(results) ? results : [];
  const total = list.length;
  const passed = list.filter((r) => r && r.ok).length;
  const failed = total - passed;
  return {
    total,
    passed,
    failed,
    passRate: total > 0 ? passed / total : 0
  };
};
