// 执行摘要纯逻辑测试（从 store.js execSummary computed 抽取）
import { describe, it, expect } from "vitest";
import { buildExecSummary } from "../src/composables/logPresentation.js";

describe("buildExecSummary（执行摘要）", () => {
  const record = {
    success: true,
    durationMs: 123,
    result: "VIP",
    errorType: null,
    errorMessage: null,
    lineExecutions: {
      1: { calls: [{ chineseName: "查询用户等级", success: true }], steps: [], expressions: [{}] },
      2: { calls: [{ chineseName: "计算折扣", success: false }], steps: [{ stepName: "步骤A" }, { stepName: "步骤A" }], expressions: [] }
    }
  };

  it("聚合调用/步骤/表达式数量与去重名称", () => {
    const s = buildExecSummary(record);
    expect(s.totalCalls).toBe(2);
    expect(s.totalSteps).toBe(2);
    expect(s.totalExprs).toBe(1);
    expect(s.failedCalls).toBe(1);
    expect(s.callNames).toEqual(["查询用户等级", "计算折扣"]);
    expect(s.stepNames).toEqual(["步骤A"]); // 去重
    expect(s.result).toBe("VIP");
  });

  it("无记录返回 null", () => {
    expect(buildExecSummary(null)).toBeNull();
  });

  it("空 lineExecutions 不报错", () => {
    const s = buildExecSummary({ success: false, lineExecutions: {} });
    expect(s.totalCalls).toBe(0);
    expect(s.callNames).toEqual([]);
  });
});
