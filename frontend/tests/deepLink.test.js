// 外部跳转深链解析纯逻辑测试
import { describe, it, expect } from "vitest";
import { parseDeepLink, pickExecutionRecord } from "../src/composables/deepLink.js";

describe("parseDeepLink（解析 URL query 参数）", () => {
  it("解析 scriptId + traceId", () => {
    expect(parseDeepLink("?scriptId=vip_discount&traceId=A1B2C3")).toEqual({
      scriptId: "vip_discount", traceId: "A1B2C3"
    });
  });

  it("只带 scriptId", () => {
    expect(parseDeepLink("?scriptId=points_double")).toEqual({
      scriptId: "points_double", traceId: ""
    });
  });

  it("空串 / 无关参数返回空", () => {
    expect(parseDeepLink("")).toEqual({ scriptId: "", traceId: "" });
    expect(parseDeepLink("?foo=bar")).toEqual({ scriptId: "", traceId: "" });
  });

  it("URL 编码与空白处理", () => {
    expect(parseDeepLink("?scriptId=%20vip%20&traceId=x%2Fy")).toEqual({
      scriptId: "vip", traceId: "x/y"
    });
  });

  it("带前导 ? 或不带都支持", () => {
    expect(parseDeepLink("scriptId=abc").scriptId).toBe("abc");
  });
});

describe("pickExecutionRecord（按 traceId 选记录，找不到回退最新）", () => {
  const records = [
    { traceId: "T3", success: true },
    { traceId: "T2", success: false },
    { traceId: "T1", success: true }
  ];

  it("带 traceId 命中", () => {
    expect(pickExecutionRecord(records, "T2").traceId).toBe("T2");
  });

  it("带 traceId 找不到 → 回退最新（第一条）", () => {
    expect(pickExecutionRecord(records, "NOT_EXIST").traceId).toBe("T3");
  });

  it("不带 traceId → 最新（第一条）", () => {
    expect(pickExecutionRecord(records, "").traceId).toBe("T3");
  });

  it("空列表返回 null", () => {
    expect(pickExecutionRecord([], "T1")).toBeNull();
    expect(pickExecutionRecord(null, "T1")).toBeNull();
  });
});
