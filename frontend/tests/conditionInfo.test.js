// 条件展示纯逻辑测试（从 store.js condText/condVerdict 抽取）
import { describe, it, expect } from "vitest";
import { buildCondText, findCondVerdict } from "../src/composables/logPresentation.js";

describe("buildCondText（从如果行截取条件文本）", () => {
  it("中文全角括号", () => {
    expect(buildCondText('如果（积分 大于 100）则 {', 1)).toBe("积分 大于 100");
  });
  it("英文括号", () => {
    expect(buildCondText('如果 (等级 等于 "VIP") 则 {', 1)).toBe('等级 等于 "VIP"');
  });
  it("无括号回落", () => {
    expect(buildCondText("如果 积分 大于 100 则 {", 1)).toBe("积分 大于 100");
  });
  it("快照/correctLine 缺失返回空", () => {
    expect(buildCondText("", 1)).toBe("");
    expect(buildCondText("如果 (x) 则 {", null)).toBe("");
  });
});

describe("findCondVerdict（从表达式轨迹找布尔判定）", () => {
  const lineExec = {
    expressions: [
      { type: "OPERATOR", token: "==", value: true, evaluated: true },
      { type: "VARIABLE", token: "等级", value: "VIP", evaluated: true }
    ]
  };
  it("返回已求值的布尔运算结果", () => {
    expect(findCondVerdict({ 2: lineExec }, 2)).toBe(true);
  });
  it("无布尔运算返回 null", () => {
    expect(findCondVerdict({ 2: { expressions: [] } }, 2)).toBeNull();
  });
  it("行不存在返回 null", () => {
    expect(findCondVerdict({}, 2)).toBeNull();
    expect(findCondVerdict(null, 2)).toBeNull();
  });
});
