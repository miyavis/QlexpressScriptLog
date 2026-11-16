// 版本历史 + 导入重名处理单元测试（TDD）
import { describe, it, expect } from "vitest";
import {
  buildVersionList,
  restoreVersion
} from "../src/composables/versionHistory.js";
import { dedupeImportName } from "../src/composables/scriptTransfer.js";

const v = (savedAt, name, content) => ({ savedAt, name, content });

describe("buildVersionList（脚本 → 历史版本列表）", () => {
  it("空脚本/无版本返回空数组", () => {
    expect(buildVersionList(null)).toEqual([]);
    expect(buildVersionList({})).toEqual([]);
    expect(buildVersionList({ versions: null })).toEqual([]);
    expect(buildVersionList({ versions: "bad" })).toEqual([]);
  });

  it("按保存时间倒序展示（最新在前）", () => {
    const script = { versions: [v(200, "新", "c2"), v(100, "旧", "c1")] };
    const list = buildVersionList(script);
    expect(list).toHaveLength(2);
    expect(list[0].savedAt).toBe(200);
  });

  it("过滤缺少内容的残缺版本", () => {
    const script = { versions: [v(1, "ok", "c"), { savedAt: 2, name: "残缺" }] };
    expect(buildVersionList(script)).toHaveLength(1);
    expect(buildVersionList(script)[0].name).toBe("ok");
  });

  it("最多展示 10 条", () => {
    const versions = [];
    for (let i = 0; i < 15; i++) versions.push(v(i, "v" + i, "c" + i));
    expect(buildVersionList({ versions })).toHaveLength(10);
  });
});

describe("restoreVersion（载入历史版本 → 编辑器表单负载）", () => {
  it("name/content/defaultParams/meta 取自版本，id 取自当前脚本", () => {
    const script = { id: "script_1", name: "现名", content: "现内容" };
    const ver = {
      savedAt: 123,
      name: "旧名",
      content: "旧内容",
      defaultParams: { user: "李四" },
      meta: { cards: [1] }
    };
    const r = restoreVersion(script, ver);
    expect(r.id).toBe("script_1");
    expect(r.name).toBe("旧名");
    expect(r.content).toBe("旧内容");
    expect(r.defaultParams).toEqual({ user: "李四" });
    expect(r.meta).toEqual({ cards: [1] });
  });

  it("版本缺 meta 时返回 undefined（画布按文本重新反解析）", () => {
    const r = restoreVersion({ id: "a" }, v(1, "n", "c"));
    expect(r.meta).toBeUndefined();
  });

  it("版本缺 defaultParams 时归一为空对象", () => {
    const r = restoreVersion({ id: "a" }, v(1, "n", "c"));
    expect(r.defaultParams).toEqual({});
  });

  it("非法输入返回 null", () => {
    expect(restoreVersion(null, v(1, "n", "c"))).toBeNull();
    expect(restoreVersion({ id: "a" }, null)).toBeNull();
  });
});

describe("dedupeImportName（导入重名自动改名）", () => {
  it("不重名时原样返回", () => {
    expect(dedupeImportName("打折规则", ["其他脚本"])).toBe("打折规则");
    expect(dedupeImportName("打折规则", [])).toBe("打折规则");
  });

  it("重名时追加（导入）后缀", () => {
    expect(dedupeImportName("打折规则", ["打折规则"])).toBe("打折规则（导入）");
  });

  it("（导入）后缀也重名时递增序号", () => {
    const names = ["打折规则", "打折规则（导入）", "打折规则（导入 2）"];
    expect(dedupeImportName("打折规则", names)).toBe("打折规则（导入 3）");
  });

  it("空名称用导入的脚本兜底", () => {
    expect(dedupeImportName("", [])).toBe("导入的脚本");
    expect(dedupeImportName("导入的脚本", ["导入的脚本"])).toBe("导入的脚本（导入）");
  });
});
