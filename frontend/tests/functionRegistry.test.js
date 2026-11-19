// 函数注册表辅助函数单元测试（TDD）
// 产品化目标：函数不再写死 3 个，全部来自后端注册表 /api/functions；
// 拖卡后能自动推荐变量名、参数占位、变量补全
import { describe, it, expect } from "vitest";
import {
  normalizeFunctions,
  suggestVarName,
  collectKnownVariables,
  nextParamOnFuncChange,
  mergeFuncOptions,
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

describe("nextParamOnFuncChange（切换函数时的参数迁移）", () => {
  const hints = ["user", "userId", "orderNo"];

  it("参数为空 → 填入新函数的 paramHint", () => {
    expect(nextParamOnFuncChange("", "userId", hints)).toBe("userId");
  });

  it("参数仍是某函数的默认提示值 → 跟随切换为新 hint", () => {
    expect(nextParamOnFuncChange("user", "orderNo", hints)).toBe("orderNo");
  });

  it("参数是用户自选变量（不在提示集中）→ 保留不动", () => {
    expect(nextParamOnFuncChange("会员等级", "orderNo", hints)).toBe("会员等级");
  });

  it("新函数无 hint → 空参保持空串、旧默认值原样保留", () => {
    expect(nextParamOnFuncChange("", "", hints)).toBe("");
    expect(nextParamOnFuncChange("user", "", hints)).toBe("user");
  });

  it("空输入安全", () => {
    expect(nextParamOnFuncChange(null, null, null)).toBe("");
  });
});

describe("mergeFuncOptions（函数下拉选项合并）", () => {
  const registry = [
    { name: "查询用户等级", service: "UserService", method: "queryLevel", paramHint: "user" },
    { name: "计算折扣", service: "DiscountService", method: "calcDiscount", paramHint: "user" }
  ];

  it("注册表函数在前，去重", () => {
    const out = mergeFuncOptions(registry, ["查询用户等级"]);
    expect(out.map((f) => f.name)).toEqual(["查询用户等级", "计算折扣"]);
  });

  it("脚本里用到但注册表没有的函数 → 追加为占位项（保证 select 能如实显示）", () => {
    const out = mergeFuncOptions(registry, ["风控核查"]);
    expect(out.map((f) => f.name)).toEqual(["查询用户等级", "计算折扣", "风控核查"]);
    expect(out[2]).toMatchObject({ name: "风控核查", service: "", method: "", paramHint: "" });
  });

  it("空值/非法输入安全", () => {
    expect(mergeFuncOptions(null, null)).toEqual([]);
    expect(mergeFuncOptions(registry, ["", null, "查询用户等级"])).toEqual(registry);
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
