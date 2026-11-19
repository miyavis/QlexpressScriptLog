// 卡片画布纯逻辑单元测试（TDD）
// 这些函数是「卡片 ↔ 行号映射」正确性的根基，边界必须覆盖：
// 带引号参数、prepcall 合并、if/else/end 配对、meta 过期校验
import { describe, it, expect } from "vitest";
import {
  sanitizeField,
  sanitizeExpr,
  newCard,
  genScriptFromCards,
  splitArgsRespectQuotes,
  parseScriptToCards,
  collectUndefinedVars,
  extractExprRefs,
  hashContent,
  isMetaStale,
  validateCardStructure
} from "../src/composables/canvasLogic.js";

/* ---------- sanitizeField / sanitizeExpr ---------- */
describe("sanitizeField（名称类字段）", () => {
  it("替换英文双引号、分号、换行", () => {
    expect(sanitizeField('a"b')).toBe("a”b");
    expect(sanitizeField("a;b")).toBe("a；b");
    expect(sanitizeField("a\nb")).toBe("a b");
    expect(sanitizeField("a\r\nb")).toBe("a b");
  });
  it("null/undefined 安全", () => {
    expect(sanitizeField(null)).toBe("");
    expect(sanitizeField(undefined)).toBe("");
  });
});

describe("sanitizeExpr（表达式字段：允许字符串字面量）", () => {
  it("保留英文双引号", () => {
    expect(sanitizeExpr('"VIP"')).toBe('"VIP"');
  });
  it("替换分号与换行", () => {
    expect(sanitizeExpr('积分 > 100;')).toBe("积分 > 100；");
    expect(sanitizeExpr("等级\n等级")).toBe("等级 等级");
  });
});

/* ---------- newCard ---------- */
describe("newCard", () => {
  it("id 唯一", () => {
    const a = newCard("call");
    const b = newCard("call");
    expect(a.id).not.toBe(b.id);
  });
  it("call 卡参数默认留空（占位提示由注册表 paramHint 动态驱动，不再硬编码 user）", () => {
    expect(newCard("call").param).toBe("");
  });
  it("prepcall 卡片带默认字段", () => {
    const c = newCard("prepcall");
    expect(c.func).toBe("查询用户等级");
    expect(c).toHaveProperty("prepName", "");
    expect(c).toHaveProperty("prepExpr", "");
  });
});

/* ---------- genScriptFromCards ---------- */
describe("genScriptFromCards（生成期行号计数）", () => {
  it("prepcall 卡片生成两行，参数名在两行间共享，区间为 1..2", () => {
    const card = newCard("prepcall");
    card.varName = "积分";
    card.func = "查询用户积分";
    card.prepName = "查询用户";
    card.prepExpr = "user";
    const { script, sourceMap } = genScriptFromCards([card]);
    const lines = script.split("\n");
    expect(lines).toHaveLength(2);
    expect(lines[0]).toBe("查询用户 = user;");
    expect(lines[1]).toBe("积分 = 查询用户积分(查询用户);");
    expect(sourceMap).toHaveLength(1);
    expect(sourceMap[0]).toMatchObject({ cardId: card.id, startLine: 1, endLine: 2, empty: false });
  });

  it("混合卡片行号与区间正确（对齐内置样例 prepcall_demo 的 11 行）", () => {
    const mk = (type, fields) => Object.assign(newCard(type), fields);
    const cards = [
      mk("prepcall", { varName: "积分", func: "查询用户积分", prepName: "查询用户", prepExpr: "user" }),
      mk("log", { title: "积分查询完成", exprRaw: '"积分=" + 积分' }),
      mk("if", { left: "积分", op: "大于", right: "100" }),
      mk("prepcall", { varName: "折扣", func: "计算折扣", prepName: "折扣依据", prepExpr: "user" }),
      mk("return", { expr: "积分 * 折扣" }),
      mk("else"),
      mk("log", { title: "未达门槛", exprRaw: "积分不足100" }),
      mk("return", { expr: "积分" }),
      mk("end")
    ];
    const { script, sourceMap } = genScriptFromCards(cards);
    const lines = script.split("\n");
    expect(lines).toHaveLength(11);
    expect(lines[0]).toBe("查询用户 = user;");
    expect(lines[1]).toBe("积分 = 查询用户积分(查询用户);");
    expect(lines[3]).toBe("如果 (积分 大于 100) 则 {");
    expect(lines[4]).toBe("折扣依据 = user;");
    expect(lines[5]).toBe("折扣 = 计算折扣(折扣依据);");
    expect(lines[7]).toBe("} 否则 {");
    expect(lines[10]).toBe("}");
    // 区间断言
    const byId = (i) => sourceMap.find((m) => m.cardId === cards[i].id);
    expect(byId(0)).toMatchObject({ startLine: 1, endLine: 2 });
    expect(byId(1)).toMatchObject({ startLine: 3, endLine: 3 });
    expect(byId(3)).toMatchObject({ startLine: 5, endLine: 6 });
    expect(byId(8)).toMatchObject({ startLine: 11, endLine: 11 });
  });

  it("空画布返回空脚本", () => {
    const { script, sourceMap } = genScriptFromCards([]);
    expect(script).toBe("");
    expect(sourceMap).toHaveLength(0);
  });

  it("call 卡参数为空时如实生成空参数，不再偷偷补 user", () => {
    const card = newCard("call");
    card.varName = "等级";
    card.func = "查询用户等级";
    const { script } = genScriptFromCards([card]);
    expect(script).toBe("等级 = 查询用户等级();");
  });

  it("原样卡 raw 为空时占位（保持旧行为：不生成行，empty=true）", () => {
    const t = newCard("text");
    t.raw = "";
    const { script, sourceMap } = genScriptFromCards([t]);
    expect(script).toBe("");
    expect(sourceMap[0].empty).toBe(true);
  });
});

/* ---------- splitArgsRespectQuotes ---------- */
describe("splitArgsRespectQuotes", () => {
  it("忽略字符串内的逗号", () => {
    expect(splitArgsRespectQuotes('"标题", "等级=" + 等级')).toEqual(['"标题"', '"等级=" + 等级']);
    expect(splitArgsRespectQuotes('"a,b", c')).toEqual(['"a,b"', "c"]);
  });
});

/* ---------- parseScriptToCards ---------- */
describe("parseScriptToCards（反解析）", () => {
  it("识别全部基础卡片类型", () => {
    const cards = parseScriptToCards(
      [
        "等级 = 查询用户等级(user);",
        "打折积分 = 积分 * 2;",
        '记录日志("标题", "等级=" + 等级);',
        "如果 (积分 大于 100) 则 {",
        "} 否则 {",
        "}",
        "返回 打折积分;"
      ].join("\n")
    );
    expect(cards.map((c) => c.type)).toEqual(["call", "calc", "log", "if", "else", "end", "return"]);
    expect(cards[0]).toMatchObject({ varName: "等级", func: "查询用户等级", param: "user" });
    expect(cards[2]).toMatchObject({ title: "标题", exprRaw: '"等级=" + 等级' });
    expect(cards[3]).toMatchObject({ left: "积分", op: "大于", right: "100" });
  });

  it("识别注册表新增的非内置函数（不再回落计算卡，加载规则如实还原）", () => {
    const cards = parseScriptToCards("风控分 = 风控核查(user);");
    expect(cards).toHaveLength(1);
    expect(cards[0].type).toBe("call");
    expect(cards[0]).toMatchObject({ varName: "风控分", func: "风控核查", param: "user" });
  });

  it("多函数组合表达式不误判为调用卡（保持计算卡保真）", () => {
    const cards = parseScriptToCards("x = foo(a) + bar(b);");
    expect(cards[0].type).toBe("calc");
    expect(cards[0].expr).toBe("foo(a) + bar(b)");
  });

  it("嵌套函数调用回落计算卡（高级语法保真）", () => {
    const cards = parseScriptToCards("x = foo(bar(y));");
    expect(cards[0].type).toBe("calc");
    expect(cards[0].expr).toBe("foo(bar(y))");
  });

  it("round-trip：空参数 call 卡往返无损", () => {
    const card = newCard("call");
    card.varName = "等级";
    card.func = "查询用户等级";
    card.param = "";
    const { script } = genScriptFromCards([card]);
    const reparsed = parseScriptToCards(script);
    expect(reparsed[0]).toMatchObject({ type: "call", varName: "等级", func: "查询用户等级", param: "" });
  });

  it("复合条件（并且/或者）回落为原样卡", () => {
    const cards = parseScriptToCards("如果 (等级 等于 VIP 并且 积分 大于 100) 则 {");
    expect(cards).toHaveLength(1);
    expect(cards[0].type).toBe("text");
    expect(cards[0].raw).toContain("并且");
  });

  it("空行与注释行跳过", () => {
    const cards = parseScriptToCards("\n// 注释\n返回 0;\n");
    expect(cards).toHaveLength(1);
    expect(cards[0].type).toBe("return");
  });

  it("合并启发式：前置参数行 + 服务调用行 → 一张带参卡", () => {
    const cards = parseScriptToCards(
      ["折扣依据 = user;", "折扣 = 计算折扣(折扣依据);"].join("\n")
    );
    expect(cards).toHaveLength(1);
    expect(cards[0].type).toBe("prepcall");
    expect(cards[0]).toMatchObject({ prepName: "折扣依据", prepExpr: "user", varName: "折扣", func: "计算折扣" });
  });

  it("参数名对不上不合并不前（保持 calc + call 两张卡）", () => {
    const cards = parseScriptToCards(
      ["折扣依据 = user;", "折扣 = 计算折扣(user);"].join("\n")
    );
    expect(cards.map((c) => c.type)).toEqual(["calc", "call"]);
  });

  it("round-trip：画布生成的脚本反解析再生成，文本逐字一致", () => {
    const mk = (type, fields) => Object.assign(newCard(type), fields);
    const cards = [
      mk("prepcall", { varName: "查询用户", func: "查询用户积分", prepName: "查询条件", prepExpr: "user" }),
      mk("log", { title: "积分查询完成", exprRaw: '"积分=" + 查询用户' }),
      mk("if", { left: "积分", op: "大于", right: "100" }),
      mk("calc", { varName: "打折积分", expr: "积分 * 2" }),
      mk("return", { expr: "打折积分" }),
      mk("else"),
      mk("text", { raw: "复杂条件输出" }),
      mk("return", { expr: "0" }),
      mk("end")
    ];
    const { script } = genScriptFromCards(cards);
    const reparsed = parseScriptToCards(script);
    const regenerated = genScriptFromCards(reparsed).script;
    expect(regenerated).toBe(script);
  });
});

/* ---------- extractExprRefs / collectUndefinedVars（依赖前置校验） ---------- */
describe("extractExprRefs（提取表达式引用的变量）", () => {
  it("普通变量、点链取根（user.id → user）", () => {
    expect(extractExprRefs("user.id")).toEqual(["user"]);
    expect(extractExprRefs("积分 * 2 + 等级")).toEqual(["积分", "等级"]);
  });
  it("字符串字面量、纯数字不算引用", () => {
    expect(extractExprRefs('"等级=" + 等级')).toEqual(["等级"]);
    expect(extractExprRefs('"VIP"')).toEqual([]);
    expect(extractExprRefs("2 * 3 + 0.5")).toEqual([]);
  });
  it("函数调用名不算引用（后面紧跟括号）", () => {
    expect(extractExprRefs("查询用户等级(user)")).toEqual(["user"]);
    expect(extractExprRefs("user.name.length() > 3")).toEqual(["user"]);
  });
  it("脚本关键字不算引用；裸写未加引号的词视为变量引用", () => {
    expect(extractExprRefs("等级 等于 VIP 并且 积分 大于 100")).toEqual(["等级", "VIP", "积分"]);
  });
});

describe("collectUndefinedVars（依赖缺失前置校验）", () => {
  it("参数引用未定义变量 → 报问题", () => {
    const card = newCard("call");
    card.varName = "等级";
    card.func = "查询用户等级";
    card.param = "userName";
    const issues = collectUndefinedVars([card], ["user"]);
    expect(issues).toHaveLength(1);
    expect(issues[0].cardId).toBe(card.id);
    expect(issues[0].message).toContain("userName");
  });

  it("参数来自脚本入参或前面卡片的产出 → 不报", () => {
    const first = newCard("call");
    first.varName = "等级";
    first.func = "查询用户等级";
    first.param = "user";
    const second = newCard("calc");
    second.varName = "打折积分";
    second.expr = "等级 * 2";
    expect(collectUndefinedVars([first, second], ["user"])).toEqual([]);
  });

  it("顺序敏感：后面的卡片产出不能给前面的卡用", () => {
    const early = newCard("calc");
    early.varName = "a";
    early.expr = "later + 1";
    const later = newCard("calc");
    later.varName = "later";
    later.expr = "2";
    const issues = collectUndefinedVars([early, later], []);
    expect(issues).toHaveLength(1);
    expect(issues[0].cardId).toBe(early.id);
  });

  it("带参取数卡：prepExpr 校验，prepName/varName 为后续卡提供定义", () => {
    const prep = newCard("prepcall");
    prep.varName = "折扣";
    prep.prepName = "折扣依据";
    prep.prepExpr = "user";
    const after = newCard("calc");
    after.varName = "x";
    after.expr = "折扣依据 + 折扣";
    expect(collectUndefinedVars([prep, after], ["user"])).toEqual([]);
  });

  it("如果/返回/日志卡的表达式也校验；字符串内不误报", () => {
    const cond = newCard("if");
    cond.left = "等级";
    cond.op = "大于";
    cond.right = "100";
    const log = newCard("log");
    log.title = "标题";
    log.exprRaw = '"等级=" + 缺失变量';
    const ret = newCard("return");
    ret.expr = "积分";
    const issues = collectUndefinedVars([cond, log, ret], ["等级", "积分"]);
    expect(issues).toHaveLength(1);
    expect(issues[0].cardId).toBe(log.id);
    expect(issues[0].message).toContain("缺失变量");
  });

  it("原样卡（高级语法）不校验；空输入安全", () => {
    const t = newCard("text");
    t.raw = "随便写什么都不报";
    expect(collectUndefinedVars([t], [])).toEqual([]);
    expect(collectUndefinedVars(null, null)).toEqual([]);
  });
});

/* ---------- hashContent / isMetaStale ---------- */
describe("hashContent", () => {
  it("同一内容 hash 稳定，不同内容 hash 不同", () => {
    expect(hashContent("等级 = 1;")).toBe(hashContent("等级 = 1;"));
    expect(hashContent("等级 = 1;")).not.toBe(hashContent("等级 = 2;"));
  });
});

describe("isMetaStale（meta 过期校验）", () => {
  it("无 meta / 无 cards → 不算过期（无映射可用）", () => {
    expect(isMetaStale("脚本", null)).toBe(false);
    expect(isMetaStale("脚本", {})).toBe(false);
    expect(isMetaStale("脚本", { cards: [] })).toBe(false);
  });
  it("有 cards 但没有 contentHash（旧版数据）→ 视为过期，走重新反解析", () => {
    expect(isMetaStale("脚本", { cards: [newCard("calc")] })).toBe(true);
  });
  it("hash 一致 → 不过期；不一致 → 过期", () => {
    const content = "查询用户 = user;";
    const meta = { cards: [newCard("calc")], contentHash: hashContent(content) };
    expect(isMetaStale(content, meta)).toBe(false);
    expect(isMetaStale(content + "\n返回 1;", meta)).toBe(true);
  });
});

/* ---------- validateCardStructure（if/else/end 配对） ---------- */
describe("validateCardStructure", () => {
  it("合法结构：如果→计算→否则→计算→结束，无问题", () => {
    const cards = ["if", "calc", "else", "calc", "end"].map((t) => newCard(t));
    expect(validateCardStructure(cards)).toEqual([]);
  });
  it("嵌套如果：if→if→end→end 合法", () => {
    const cards = ["if", "if", "calc", "end", "end"].map((t) => newCard(t));
    expect(validateCardStructure(cards)).toEqual([]);
  });
  it("结束卡多于如果卡 → 多余的结束卡报问题", () => {
    const cards = ["end", "end"].map((t) => newCard(t));
    const issues = validateCardStructure(cards);
    expect(issues).toHaveLength(2);
    expect(issues.every((i) => i.message.includes("如果"))).toBe(true);
  });
  it("否则卡缺如果卡 → 报问题", () => {
    const cards = ["else", "end"].map((t) => newCard(t));
    const issues = validateCardStructure(cards);
    expect(issues.length).toBeGreaterThanOrEqual(1);
    expect(issues.some((i) => i.cardId === cards[0].id)).toBe(true);
  });
  it("同一如果块出现两个否则 → 第二个否则报问题", () => {
    const cards = ["if", "else", "else", "end"].map((t) => newCard(t));
    const issues = validateCardStructure(cards);
    expect(issues).toHaveLength(1);
    expect(issues[0].cardId).toBe(cards[2].id);
  });
  it("如果块未结束（缺结束卡）→ 如果卡报问题", () => {
    const cards = ["if", "calc"].map((t) => newCard(t));
    const issues = validateCardStructure(cards);
    expect(issues).toHaveLength(1);
    expect(issues[0].cardId).toBe(cards[0].id);
    expect(issues[0].message).toContain("结束");
  });
  it("问题条目包含 cardId、index、message 三个字段", () => {
    const cards = ["end"].map((t) => newCard(t));
    const issues = validateCardStructure(cards);
    expect(issues[0]).toHaveProperty("cardId");
    expect(issues[0]).toHaveProperty("index", 0);
    expect(issues[0]).toHaveProperty("message");
  });
});
