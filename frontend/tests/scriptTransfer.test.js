// 脚本导出/导入单元测试（TDD）
// 场景：业务人员把写好的规则脚本（含画布 meta）导出成 JSON 文件发给别人，
// 对方在脚本列表点"导入"即可得到一份新脚本（id 由后端生成）。
import { describe, it, expect } from "vitest";
import { buildScriptExport, parseScriptImport } from "../src/composables/scriptTransfer.js";

const demoScript = {
  id: "script_100",
  name: "打折规则",
  content: "user = '张三'\n等级 = 查询用户等级(user)\n返回 等级",
  defaultParams: { user: "张三" },
  meta: { cards: [{ id: "c1", type: "prepcall" }], cardSourceMap: {}, contentHash: "abc" },
  createdAt: 1,
  updatedAt: 2
};

describe("buildScriptExport（脚本 → 可分享 JSON）", () => {
  it("正文包含 name/content/defaultParams/meta，且带来源标记", () => {
    const { body } = buildScriptExport(demoScript);
    const obj = JSON.parse(body);
    expect(obj.exportedFrom).toContain("规则执行平台");
    expect(obj.script.name).toBe("打折规则");
    expect(obj.script.content).toContain("返回 等级");
    expect(obj.script.defaultParams).toEqual({ user: "张三" });
    expect(obj.script.meta.cards).toHaveLength(1);
    // 内部 id 与时间戳不入导出文件（导入时反正会重新生成）
    expect(obj.script.id).toBeUndefined();
    expect(obj.script.createdAt).toBeUndefined();
  });

  it("meta 为空时导出正文仍完整（纯文本脚本也能分享）", () => {
    const { body } = buildScriptExport({ ...demoScript, meta: null });
    const obj = JSON.parse(body);
    expect(obj.script.content).toBe(demoScript.content);
    expect(obj.script.meta).toBeUndefined();
  });

  it("文件名含脚本名且时间戳合法", () => {
    const { filename } = buildScriptExport(demoScript);
    expect(filename).toMatch(/^规则脚本_打折规则_\d{8}-\d{6}\.json$/);
  });

  it("脚本名含路径非法字符时文件名安全化", () => {
    const { filename } = buildScriptExport({ ...demoScript, name: 'a/b\\c:d' });
    expect(filename).not.toMatch(/[/\\:]/);
  });
});

describe("parseScriptImport（导入 JSON → 新建脚本请求体）", () => {
  it("environment 随导出/导入往返保留", () => {
    const exported = JSON.parse(buildScriptExport({ ...demoScript, environment: "prod" }).body);
    expect(exported.script.environment).toBe("prod");
    const req = parseScriptImport(JSON.stringify(exported));
    expect(req.environment).toBe("prod");
  });

  it("合法导出文件解析出 name/content/defaultParams/meta", () => {
    const exported = JSON.parse(buildScriptExport(demoScript).body);
    const req = parseScriptImport(JSON.stringify(exported));
    expect(req.name).toBe("打折规则");
    expect(req.content).toBe(demoScript.content);
    expect(req.defaultParams).toEqual({ user: "张三" });
    expect(req.meta).toEqual(demoScript.meta);
    expect(req.id).toBeUndefined(); // id 由后端新生成
  });

  it("兼容不带导出包装的裸脚本对象", () => {
    const req = parseScriptImport(JSON.stringify({
      name: "裸脚本",
      content: "返回 1"
    }));
    expect(req.name).toBe("裸脚本");
    expect(req.content).toBe("返回 1");
    expect(req.defaultParams).toEqual({});
  });

  it("缺少 content 时抛中文错误", () => {
    expect(() => parseScriptImport(JSON.stringify({ name: "x" }))).toThrow(/缺少脚本内容/);
  });

  it("JSON 非法时抛中文错误", () => {
    expect(() => parseScriptImport("not json")).toThrow(/不是合法的/);
  });

  it("defaultParams 不是对象时归一为空对象", () => {
    const req = parseScriptImport(JSON.stringify({ name: "x", content: "返回 1", defaultParams: "bad" }));
    expect(req.defaultParams).toEqual({});
  });

  it("meta 不是对象时丢弃（画布打开时会重新反解析）", () => {
    const req = parseScriptImport(JSON.stringify({ name: "x", content: "返回 1", meta: "bad" }));
    expect(req.meta).toBeUndefined();
  });
});
