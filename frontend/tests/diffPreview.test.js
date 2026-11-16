// 行级 diff + 列表筛选单元测试（TDD）
import { describe, it, expect } from "vitest";
import { diffLines, renderDiffRows } from "../src/composables/diffPreview.js";
import { filterScripts, filterExecutions } from "../src/composables/listFilters.js";

describe("diffLines（当前版本 ↔ 历史版本 的行级差异）", () => {
  it("内容完全相同 → 全部 same 行", () => {
    const rows = diffLines("a\nb\nc", "a\nb\nc");
    expect(rows).toHaveLength(3);
    expect(rows.every((r) => r.type === "same")).toBe(true);
  });

  it("版本新增行 → add", () => {
    const rows = diffLines("a\nc", "a\nb\nc");
    expect(rows.find((r) => r.text === "b").type).toBe("add");
  });

  it("当前有而版本没有的行 → remove", () => {
    const rows = diffLines("a\nb\nc", "a\nc");
    expect(rows.find((r) => r.text === "b").type).toBe("remove");
  });

  it("混合增删改：相同行保持顺序，改动行分别标出", () => {
    const rows = diffLines("a\n旧\nc", "a\n新\nc\n尾巴");
    const types = rows.map((r) => r.type);
    expect(types).toEqual(["same", "remove", "add", "same", "add"]);
  });

  it("空输入安全", () => {
    expect(diffLines(null, "a")).toEqual([{ type: "add", text: "a" }]);
    expect(diffLines("a", null)).toEqual([{ type: "remove", text: "a" }]);
    expect(diffLines(null, null)).toEqual([]);
  });
});

describe("renderDiffRows（diff → 汇总信息）", () => {
  it("统计新增/删除行数", () => {
    const rows = diffLines("a\nb", "a\nb\nc\nd");
    const summary = renderDiffRows(rows);
    expect(summary.addCount).toBe(2);
    expect(summary.removeCount).toBe(0);
    expect(summary.changed).toBe(true);
  });

  it("无差异时 changed 为 false", () => {
    const summary = renderDiffRows(diffLines("a", "a"));
    expect(summary.changed).toBe(false);
    expect(summary.addCount).toBe(0);
  });
});

describe("filterScripts（脚本列表搜索）", () => {
  const scripts = [
    { id: "script_1", name: "打折规则" },
    { id: "script_2", name: "风控规则" },
    { id: "script_3", name: "导入的脚本" }
  ];

  it("按名称关键字过滤（不区分大小写）", () => {
    expect(filterScripts(scripts, "风控")).toHaveLength(1);
    expect(filterScripts([{ id: "a", name: "ABC" }], "abc")).toHaveLength(1);
  });

  it("按 id 过滤", () => {
    expect(filterScripts(scripts, "script_2")).toHaveLength(1);
  });

  it("空关键字返回全部", () => {
    expect(filterScripts(scripts, "  ")).toHaveLength(3);
  });

  it("非法输入安全", () => {
    expect(filterScripts(null, "x")).toEqual([]);
    expect(filterScripts(scripts, null)).toHaveLength(3);
  });
});

describe("filterExecutions（执行记录筛选）", () => {
  const records = [
    { traceId: "AAA111", scriptName: "打折规则", success: true },
    { traceId: "BBB222", scriptName: "风控规则", success: false },
    { traceId: "CCC333", scriptName: "导入的脚本", success: true }
  ];

  it("按状态过滤：只看失败", () => {
    const out = filterExecutions(records, { status: "fail" });
    expect(out).toHaveLength(1);
    expect(out[0].traceId).toBe("BBB222");
  });

  it("按关键字匹配 traceId 或脚本名", () => {
    expect(filterExecutions(records, { keyword: "bbb" })).toHaveLength(1);
    expect(filterExecutions(records, { keyword: "规则" })).toHaveLength(2);
  });

  it("状态 + 关键字组合", () => {
    const out = filterExecutions(records, { status: "ok", keyword: "导入" });
    expect(out).toHaveLength(1);
    expect(out[0].scriptName).toBe("导入的脚本");
  });

  it("空条件返回全部；非法输入安全", () => {
    expect(filterExecutions(records, {})).toHaveLength(3);
    expect(filterExecutions(records, null)).toHaveLength(3);
    expect(filterExecutions(null, { status: "ok" })).toEqual([]);
  });
});
