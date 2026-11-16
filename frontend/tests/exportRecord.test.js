// 执行记录导出单元测试（TDD）
// 导出 = 给出可离线分享的文件（文件名 + JSON 正文），发给他人即可复盘一次执行
import { describe, it, expect } from "vitest";
import { buildExecutionExport } from "../src/composables/exportRecord.js";

const record = {
  traceId: "a1b2c3d4e5f60718",
  scriptId: "vip_discount",
  scriptName: "VIP 折扣判定",
  status: "SUCCESS",
  totalDurationMillis: 26,
  createdAt: 1731000000000,
  params: { user: "张三" },
  result: 0.88,
  scriptSnapshot: "等级 = 查询用户等级(user);",
  calls: []
};

describe("buildExecutionExport", () => {
  it("文件名包含 traceId 与时间，扩展名 .json", () => {
    const { filename } = buildExecutionExport(record);
    expect(filename).toContain("a1b2c3d4e5f60718");
    expect(filename).toMatch(/\.json$/);
  });

  it("traceId 里的非法文件名字符被替换", () => {
    const { filename } = buildExecutionExport({ ...record, traceId: "bad/id\\in:trace" });
    expect(filename).not.toMatch(/[/\\:]/);
  });

  it("正文是格式化 JSON，且包含关键字段（traceId/脚本快照/结果）", () => {
    const { body } = buildExecutionExport(record);
    const parsed = JSON.parse(body);
    expect(parsed.traceId).toBe("a1b2c3d4e5f60718");
    expect(parsed.scriptSnapshot).toContain("查询用户等级");
    expect(parsed.result).toBe(0.88);
    // 格式化输出（多行），方便人直接读
    expect(body.split("\n").length).toBeGreaterThan(1);
  });

  it("字段缺失（导出失败记录）也能工作", () => {
    const { body, filename } = buildExecutionExport({ traceId: "t1", status: "ERROR" });
    const parsed = JSON.parse(body);
    expect(parsed.traceId).toBe("t1");
    expect(parsed.status).toBe("ERROR");
    expect(filename).toContain("t1");
  });

  it("补充导出时间与导出工具说明，接收方能看懂来源", () => {
    const { body } = buildExecutionExport(record);
    const parsed = JSON.parse(body);
    expect(parsed.exportedAt).toBeTruthy();
    expect(parsed.exportedFrom).toContain("QLExpress4 规则执行平台");
  });
});
