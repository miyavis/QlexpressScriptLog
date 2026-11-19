// 执行记录对比纯逻辑测试
import { describe, it, expect } from "vitest";
import { buildExecutionDiff } from "../src/composables/executionDiff.js";

describe("buildExecutionDiff（两次执行对比）", () => {
  const a = {
    traceId: "A", success: true, result: "VIP", durationMs: 100,
    params: { user: "张三" },
    calls: [{ chineseName: "查询用户等级", durationMs: 50 }]
  };
  const b = {
    traceId: "B", success: true, result: "普通", durationMs: 150,
    params: { user: "李四" },
    calls: [{ chineseName: "查询用户等级", durationMs: 90 }]
  };

  it("列出结果/参数/耗时/调用的差异", () => {
    const d = buildExecutionDiff(a, b);
    expect(d).toHaveLength(4); // 返回值 + 入参 + 耗时 + 调用耗时
    expect(d.find((x) => x.field === "返回值")).toMatchObject({ left: "VIP", right: "普通" });
    expect(d.find((x) => x.field === "耗时(ms)")).toBeTruthy();
    expect(d.find((x) => x.field === "调用:查询用户等级")).toBeTruthy();
  });

  it("相同内容返回空", () => {
    expect(buildExecutionDiff(a, a)).toEqual([]);
  });

  it("null 安全", () => {
    expect(buildExecutionDiff(null, b)).toEqual([]);
    expect(buildExecutionDiff(a, null)).toEqual([]);
  });
});
