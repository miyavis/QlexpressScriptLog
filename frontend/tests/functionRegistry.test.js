// 函数注册表辅助函数单元测试（TDD）
// 产品化目标：函数不再写死 3 个，全部来自后端注册表 /api/functions；
// 拖卡后能自动推荐变量名、参数占位、变量补全
import { describe, it, expect } from "vitest";
import {
  normalizeFunctions,
  suggestVarName,
  collectKnownVariables,
  BUILTIN_FUNCTIONS
} from "../src/composables/functionRegistry.js";

describe("normalizeFunctions（后端注册表 → 前端可用函数列表）", () => {
  it("映射字段：chineseName→name，serviceName/methodName→归属，paramHint 透传", () => {
    const raw = [
      { chineseName: "查询用户等级", serviceName: "UserService", methodName: "queryLevel", paramHint: "user" },
      { chineseName: "记录日志", serviceName: "StepLogger", methodName: "log", paramHint: "步骤名, 内容" }
    ];
    const fns = normalizeFunctions(raw);
    expect(fns).toHaveLength(1);
    expect(fns[0]).toEqual({ name: "查询用户等级", service: "UserService", method: "queryLevel", paramHint: "user" });
  });

  it("过滤掉记录日志（脚本语句，不属于取数卡）和无中文名的条目", () => {
    const raw = [
      { chineseName: "", serviceName: "A", methodName: "a", paramHint: "x" },
      { chineseName: "记录日志", serviceName: "StepLogger", methodName: "log", paramHint: "" }
    ];
    expect(normalizeFunctions(raw)).toHaveLength(0);
  });

  it("raw 为空/非法时返回空数组", () => {
    expect(normalizeFunctions(null)).toEqual([]);
    expect(normalizeFunctions("bad")).toEqual([]);
  });
});

describe("suggestVarName（按函数名推荐结果变量名）", () => {
  it("去掉查询/计算/获取/判断动词前缀", () => {
    expect(suggestVarName("查询用户等级")).toBe("用户等级");
    expect(suggestVarName("计算折扣")).toBe("折扣");
    expect(suggestVarName("获取风控结果")).toBe("风控结果");
  });
  it("无动词前缀时原样返回", () => {
    expect(suggestVarName("用户等级")).toBe("用户等级");
  });
});

describe("collectKnownVariables（表达式输入的变量补全）", () => {
  it("入参键在前，各卡片变量按顺序追加并去重", () => {
    const cards = [
      { type: "prepcall", varName: "积分", prepName: "查询用户" },
      { type: "calc", varName: "打折积分" },
      { type: "call", varName: "等级" },
      { type: "log" }, // 无变量名，跳过
      { type: "if" }
    ];
    const vars = collectKnownVariables(cards, ["user"]);
    expect(vars).toEqual(["user", "积分", "打折积分", "等级"]);
  });

  it("重复变量名只出现一次", () => {
    const cards = [
      { type: "call", varName: "等级" },
      { type: "calc", varName: "等级" }
    ];
    expect(collectKnownVariables(cards, [])).toEqual(["等级"]);
  });

  it("空输入安全", () => {
    expect(collectKnownVariables([], [])).toEqual([]);
    expect(collectKnownVariables(null, null)).toEqual([]);
  });
});

describe("BUILTIN_FUNCTIONS（注册表拉取失败时的兜底）", () => {
  it("包含平台内置三个业务函数，参数提示为 user", () => {
    expect(BUILTIN_FUNCTIONS.map((f) => f.name)).toContain("查询用户等级");
    expect(BUILTIN_FUNCTIONS.map((f) => f.name)).toContain("查询用户积分");
    expect(BUILTIN_FUNCTIONS.map((f) => f.name)).toContain("计算折扣");
    expect(BUILTIN_FUNCTIONS.every((f) => f.paramHint === "user")).toBe(true);
  });
});
