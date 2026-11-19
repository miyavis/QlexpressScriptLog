// 场景测试纯逻辑测试（用例结构 + 批量结果汇总）
import { describe, it, expect } from "vitest";
import { newTestCase, summarizeResults, parseCaseParams } from "../src/composables/testCases.js";

describe("newTestCase（新建用例）", () => {
  it("生成带 id 的空用例", () => {
    const a = newTestCase();
    const b = newTestCase();
    expect(a.id).not.toBe(b.id);
    expect(a.name).toBeTruthy();
    expect(a.paramsJson).toBe("{}");
  });
});

describe("parseCaseParams（解析用例参数）", () => {
  it("合法 JSON 返回对象", () => {
    expect(parseCaseParams('{"user":"张三"}')).toEqual({ user: "张三" });
  });
  it("非法/空返回 null", () => {
    expect(parseCaseParams("bad")).toBeNull();
    expect(parseCaseParams("")).toEqual({});
  });
});

describe("summarizeResults（批量结果汇总）", () => {
  it("统计总数/成功/失败", () => {
    const results = [
      { name: "VIP", ok: true, result: "VIP", durationMs: 100 },
      { name: "普通", ok: true, result: "普通", durationMs: 80 },
      { name: "异常", ok: false, error: "语法错误", durationMs: 0 }
    ];
    const s = summarizeResults(results);
    expect(s.total).toBe(3);
    expect(s.passed).toBe(2);
    expect(s.failed).toBe(1);
    expect(s.passRate).toBeCloseTo(0.667, 2);
  });
  it("空结果返回全 0", () => {
    const s = summarizeResults([]);
    expect(s.total).toBe(0);
    expect(s.passRate).toBe(0);
  });
});
