// 行级日志展示纯逻辑单元测试（从 store.js 抽取后 TDD）
import { describe, it, expect } from "vitest";
import {
  fmtScalar,
  truncate,
  callArgs,
  callResult,
  rawExprDesc,
  parseBranchInfo,
  buildLineAssigns,
  buildReturnDesc
} from "../src/composables/logPresentation.js";

describe("fmtScalar（标量显示）", () => {
  it("字符串/数字/布尔直接显示，对象 JSON 化", () => {
    expect(fmtScalar("VIP")).toBe("VIP");
    expect(fmtScalar(380)).toBe("380");
    expect(fmtScalar(true)).toBe("true");
    expect(fmtScalar({ a: 1 })).toBe('{"a":1}');
    expect(fmtScalar(null)).toBe("");
  });
});

describe("truncate / callArgs / callResult", () => {
  it("truncate 超长截断带省略号", () => {
    expect(truncate("abc", 2)).toBe("ab…");
    expect(truncate("ab", 5)).toBe("ab");
  });
  it("callArgs 解析 JSON 数组为逗号分隔", () => {
    expect(callArgs({ argsJson: '["张三", 380]' })).toBe("张三, 380");
    expect(callArgs({ argsJson: "bad" })).toBe("bad");
  });
  it("callResult 去 JSON 引号", () => {
    expect(callResult({ resultJson: '"VIP"' })).toBe("VIP");
    expect(callResult({ resultJson: null })).toBe("");
  });
});

describe("rawExprDesc（原始轨迹描述）", () => {
  it("带 token/值/未执行标记", () => {
    expect(rawExprDesc({ type: "VARIABLE", token: "等级", value: "VIP", evaluated: true })).toBe("变量【等级】 = VIP");
    expect(rawExprDesc({ type: "RETURN", token: "", evaluated: false })).toBe("返回（未执行）");
    expect(rawExprDesc(null)).toBe("");
  });
});

describe("parseBranchInfo（如果/否则结构解析）", () => {
  it("标记 then/else 行与如果行", () => {
    const snap = [
      "等级 = 查询用户等级(user);",
      "如果 (等级 等于 VIP) 则 {",
      "返回 1;",
      "} 否则 {",
      "返回 0;",
      "}"
    ].join("\n");
    const info = parseBranchInfo(snap);
    expect(info.ifLine).toBe(2);
    expect(info.map[3]).toBe("then");
    expect(info.map[5]).toBe("else");
  });
  it("空快照返回空信息", () => {
    expect(parseBranchInfo("")).toEqual({ map: {}, ifLine: null });
    expect(parseBranchInfo(null)).toEqual({ map: {}, ifLine: null });
  });
});

describe("buildLineAssigns / buildReturnDesc（业务描述还原）", () => {
  it("赋值行还原为中缀形式", () => {
    const le = {
      expressions: [
        { type: "OPERATOR", token: "=", value: 760, evaluated: true },
        { type: "VARIABLE", token: "打折积分", value: null, evaluated: true },
        { type: "VARIABLE", token: "积分", value: 380, evaluated: true },
        { type: "OPERATOR", token: "*", evaluated: true },
        { type: "VALUE", value: 2, evaluated: true }
      ]
    };
    const out = buildLineAssigns(le);
    expect(out).toHaveLength(1);
    expect(out[0]).toContain("打折积分 = 760");
    expect(out[0]).toContain("积分(380) · × · 2");
  });
  it("返回行还原", () => {
    const le = {
      expressions: [
        { type: "VARIABLE", token: "打折积分", value: 760, evaluated: true },
        { type: "RETURN", value: 760, evaluated: true }
      ]
    };
    expect(buildReturnDesc(le)).toBe("返回结果：打折积分 → 760");
  });
  it("无赋值/无返回返回空", () => {
    expect(buildLineAssigns({ expressions: [] })).toEqual([]);
    expect(buildReturnDesc({ expressions: [] })).toBeNull();
  });
});
