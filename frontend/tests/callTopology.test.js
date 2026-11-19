// 调用拓扑纯逻辑测试：把 ServiceCallLog 树转成可视化节点（含耗时占比、层级、失败标记）
import { describe, it, expect } from "vitest";
import { buildTopology, flattenCalls } from "../src/composables/callTopology.js";

describe("flattenCalls（扁平化调用树）", () => {
  it("带 depth 递归展开父子调用", () => {
    const calls = [{
      serviceName: "A", methodName: "m1", startTime: 1, durationMs: 100, success: true,
      children: [{ serviceName: "B", methodName: "m2", startTime: 2, durationMs: 30, success: false, children: [] }]
    }];
    const flat = flattenCalls(calls);
    expect(flat).toHaveLength(2);
    expect(flat[0]).toMatchObject({ depth: 0, success: true });
    expect(flat[1]).toMatchObject({ depth: 1, success: false });
  });
  it("空输入安全", () => {
    expect(flattenCalls(null)).toEqual([]);
    expect(flattenCalls([])).toEqual([]);
  });
});

describe("buildTopology（拓扑节点）", () => {
  it("按耗时占比标记主导节点", () => {
    const calls = [
      { serviceName: "A", methodName: "m1", startTime: 1, durationMs: 90, success: true, children: [] },
      { serviceName: "B", methodName: "m2", startTime: 2, durationMs: 10, success: true, children: [] }
    ];
    const nodes = buildTopology(calls);
    expect(nodes).toHaveLength(2);
    expect(nodes[0].share).toBe(0.9);
    expect(nodes[0].dominant).toBe(true);
    expect(nodes[1].dominant).toBe(false);
  });

  it("兼容中文函数名显示", () => {
    const nodes = buildTopology([{
      chineseName: "查询用户等级", serviceName: "UserService", methodName: "queryLevel",
      startTime: 1, durationMs: 5, success: true, children: []
    }]);
    expect(nodes[0].label).toBe("查询用户等级");
  });
});
