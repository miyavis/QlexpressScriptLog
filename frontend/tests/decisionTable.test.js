// 决策表纯逻辑测试：表格 → 中文脚本（First 命中即停 / Collect 累加）
import { describe, it, expect } from "vitest";
import { genDecisionScript, emptyDecisionTable } from "../src/composables/decisionTable.js";

describe("emptyDecisionTable（默认决策表）", () => {
  it("默认含两列一行、First 策略", () => {
    const t = emptyDecisionTable();
    expect(t.strategy).toBe("first");
    expect(t.conditions).toHaveLength(2);
    expect(t.rows).toHaveLength(1);
    expect(t.resultVar).toBe("结果");
  });
});

describe("genDecisionScript（First 策略）", () => {
  it("生成嵌套如果/否则，命中即赋值", () => {
    const table = {
      strategy: "first",
      resultVar: "折扣",
      conditions: ["积分", "等级"],
      rows: [
        { cells: ["> 100", '== "VIP"'], result: "0.8" },
        { cells: ["> 50", '== "普通"'], result: "0.9" }
      ],
      defaultValue: "1"
    };
    const script = genDecisionScript(table);
    expect(script).toContain("如果 (积分 > 100 && 等级 == \"VIP\") 则 {");
    expect(script).toContain("折扣 = 0.8;");
    expect(script).toContain("折扣 = 0.9;");
    expect(script).toContain("折扣 = 1;"); // 默认 else
  });
});

describe("genDecisionScript（Collect 策略）", () => {
  it("初始化结果变量，每行命中累加", () => {
    const table = {
      strategy: "collect",
      resultVar: "总分",
      conditions: ["等级"],
      rows: [{ cells: ['== "VIP"'], result: "20" }],
      defaultValue: "0"
    };
    const script = genDecisionScript(table);
    expect(script).toContain("总分 = 0;");
    expect(script).toContain("总分 = 总分 + 20;");
    expect(script).toContain("如果 (等级 == \"VIP\") 则 {");
  });
});

describe("genDecisionScript（边界）", () => {
  it("空行返回注释占位", () => {
    expect(genDecisionScript({ strategy: "first", rows: [], conditions: [], resultVar: "x" }))
      .toContain("// 决策表暂无规则");
  });
});
