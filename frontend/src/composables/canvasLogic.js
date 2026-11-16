/**
 * 卡片画布纯逻辑（无 Vue 依赖，可单测）
 *
 * 单一事实源约定：content 是真相，cards / sourceMap 是它的派生视图。
 * 本模块提供：卡片 → 脚本生成（生成期行号计数）、脚本 → 卡片反解析（含带参卡合并）、
 * meta 过期校验（contentHash）、if/else/end 结构校验。
 */

export const CARD_FUNCS = ["查询用户等级", "查询用户积分", "计算折扣"];
export const CARD_OPS = ["等于", "不等于", "大于", "小于", "大于等于", "小于等于"];

let cardSeq = 0;

/** 新建卡片（id 由时间戳 + 递增序号保证唯一） */
export const newCard = (type) => {
  cardSeq += 1;
  const card = { id: `card_${Date.now().toString(36)}_${cardSeq}`, type };
  if (type === "call") Object.assign(card, { varName: "", func: CARD_FUNCS[0], param: "user" });
  // 带参取数卡：一张卡生成两行——参数定义行 + 服务调用行
  if (type === "prepcall") Object.assign(card, { varName: "", func: CARD_FUNCS[0], prepName: "", prepExpr: "" });
  if (type === "calc" || type === "return") card.expr = "";
  if (type === "log") Object.assign(card, { title: "", exprRaw: "" });
  if (type === "if") Object.assign(card, { left: "", op: "大于", right: "" });
  if (type === "text") card.raw = "";
  return card;
};

/** 文本安全化（名称类字段）：生成的脚本里不允许出现英文双引号/分号/换行 */
export const sanitizeField = (v) => String(v == null ? "" : v)
  .replace(/"/g, "”")
  .replace(/;/g, "；")
  .replace(/\r?\n/g, " ");

/** 表达式安全化（表达式字段）：允许字符串字面量（英文引号保留），仍禁止分号/换行 */
export const sanitizeExpr = (v) => String(v == null ? "" : v)
  .replace(/;/g, "；")
  .replace(/\r?\n/g, " ");

/** 卡片 → 中文脚本（生成期同步记录行号映射；一张卡片可生成多行，区间 startLine..endLine） */
export const genScriptFromCards = (cards) => {
  const lines = [];
  const map = [];
  for (const c of cards) {
    const start = lines.length + 1;
    const texts = []; // 本卡片生成的所有行
    if (c.type === "call") {
      texts.push(`${sanitizeField(c.varName || "值")} = ${c.func}(${sanitizeField(c.param || "user")});`);
    } else if (c.type === "prepcall") {
      // 带参取数：参数定义行 + 服务调用行（参数名在两行间共享）
      const pname = sanitizeField(c.prepName || "参数");
      texts.push(`${pname} = ${sanitizeExpr(c.prepExpr || 0)};`);
      texts.push(`${sanitizeField(c.varName || "值")} = ${c.func}(${pname});`);
    } else if (c.type === "calc") {
      texts.push(`${sanitizeField(c.varName || "值")} = ${sanitizeExpr(c.expr || 0)};`);
    } else if (c.type === "log") {
      const rawArg = String(c.exprRaw != null && c.exprRaw !== "" ? c.exprRaw : (c.expr || "")).trim();
      const arg = rawArg ? (rawArg.startsWith('"') ? rawArg : `"${sanitizeField(rawArg)}"`) : '""';
      texts.push(`记录日志("${sanitizeField(c.title || "打印")}", ${arg});`);
    } else if (c.type === "if") {
      texts.push(`如果 (${sanitizeExpr(c.left)} ${c.op || "大于"} ${sanitizeExpr(c.right)}) 则 {`);
    } else if (c.type === "else") {
      texts.push("} 否则 {");
    } else if (c.type === "end") {
      texts.push("}");
    } else if (c.type === "return") {
      texts.push(`返回 ${sanitizeExpr(c.expr || 0)};`);
    } else if (c.type === "text") {
      texts.push(String(c.raw || "").replace(/\r?\n/g, " "));
    }
    for (const t of texts) {
      if (t) {
        lines.push(t);
      }
    }
    map.push({ cardId: c.id, type: c.type, startLine: start, endLine: lines.length });
  }
  map.forEach((m) => { m.empty = m.endLine < m.startLine; });
  return { script: lines.join("\n"), sourceMap: map };
};

/** 顶层逗号拆分（忽略字符串内的逗号）："积分查询完成", "等级=" + 等级 → ["\"积分查询完成\"", "\"等级=\" + 等级"] */
export const splitArgsRespectQuotes = (s) => {
  const out = [];
  let cur = "";
  let inStr = false;
  for (const ch of s) {
    if (ch === '"') inStr = !inStr;
    if (ch === "," && !inStr) {
      out.push(cur);
      cur = "";
    } else {
      cur += ch;
    }
  }
  if (cur.trim() !== "" || out.length > 0) {
    out.push(cur);
  }
  return out.map((x) => x.trim()).filter((x) => x !== "");
};

/**
 * 脚本文本 → 卡片（反解析，供「文本 ↔ 画布」双向切换）
 * 识别不了的行转成“原样卡”保留原文，保证高级语法（并且/或者等）不丢失
 */
export const parseScriptToCards = (content) => {
  const cards = [];
  const lines = (content || "").split(/\r?\n/);
  for (const raw of lines) {
    const line = raw.trim();
    if (!line || line.startsWith("//")) continue;
    let m;
    if (line === "}") {
      cards.push(newCard("end"));
      continue;
    }
    if (line === "} 否则 {") {
      cards.push(newCard("else"));
      continue;
    }
    if ((m = line.match(/^如果\s*[（(](.*)[）)]\s*则\s*\{$/))) {
      const inner = m[1].trim();
      // 含 并且/或者 的复合条件解析不了，回落为原样卡
      const om = /[并且或者]/.test(inner)
        ? null
        : inner.match(/^([^\s]+?)\s*(大于等于|小于等于|不等于|大于|小于|等于)\s*(.*)$/);
      if (om) {
        const card = newCard("if");
        card.left = om[1].trim();
        card.op = om[2];
        card.right = om[3].trim();
        cards.push(card);
      } else {
        const t = newCard("text");
        t.raw = line;
        cards.push(t);
      }
      continue;
    }
    if ((m = line.match(/^返回\s+(.+);$/))) {
      const card = newCard("return");
      card.expr = m[1].trim();
      cards.push(card);
      continue;
    }
    if ((m = line.match(/^记录日志\s*\((.+)\);$/))) {
      const args = splitArgsRespectQuotes(m[1]);
      const card = newCard("log");
      if (args.length >= 2) {
        card.title = args[0].replace(/^"+|"+$/g, "");
        card.exprRaw = args.slice(1).join(", ");
      } else if (args.length === 1) {
        card.title = args[0].replace(/^"+|"+$/g, "");
      }
      cards.push(card);
      continue;
    }
    if ((m = line.match(/^([^=]+?)\s*=\s*(.+?)\(\s*(.*?)\s*\);$/))) {
      const func = m[2].trim();
      if (CARD_FUNCS.includes(func)) {
        const card = newCard("call");
        card.varName = m[1].trim();
        card.func = func;
        card.param = m[3].trim();
        cards.push(card);
        continue;
      }
    }
    if ((m = line.match(/^([^=]+?)\s*=\s*(.+);$/))) {
      const card = newCard("calc");
      card.varName = m[1].trim();
      card.expr = m[2].trim();
      cards.push(card);
      continue;
    }
    const t = newCard("text");
    t.raw = line;
    cards.push(t);
  }
  // 合并启发式：「前置参数行 + 服务调用行」还原为一张带参取数卡（一卡两行）。
  // 条件：计算卡的变量名恰好是紧随其后的服务调用卡使用的参数；合并不上则保持两张卡（无损，仅粒度不同）
  const merged = [];
  for (let i = 0; i < cards.length; i++) {
    const cur = cards[i];
    const next = cards[i + 1];
    if (cur.type === "calc" && next && next.type === "call"
        && cur.varName && next.param === cur.varName) {
      const card = newCard("prepcall");
      card.varName = next.varName;
      card.func = next.func;
      card.prepName = cur.varName;
      card.prepExpr = cur.expr;
      merged.push(card);
      i += 1;
    } else {
      merged.push(cur);
    }
  }
  return merged;
};

/** 内容 hash（FNV-1a 32 位，稳定、不依赖运行环境），用于 meta 过期校验 */
export const hashContent = (content) => {
  const str = String(content == null ? "" : content);
  let hash = 0x811c9dc5;
  for (let i = 0; i < str.length; i++) {
    hash ^= str.charCodeAt(i);
    hash = (hash + ((hash << 1) + (hash << 4) + (hash << 7) + (hash << 8) + (hash << 24))) >>> 0;
  }
  return hash.toString(16).padStart(8, "0");
};

/**
 * meta 过期校验：判断保存的卡片映射是否还匹配当前脚本文本。
 * - 无 cards：不算过期（本来就没有映射可用）
 * - 有 cards 但没有 contentHash：视为过期（旧版数据，安全起见走重新反解析）
 * - 有 hash：与当前内容 hash 对比，不一致即过期
 */
export const isMetaStale = (content, meta) => {
  if (!meta || !Array.isArray(meta.cards) || meta.cards.length === 0) {
    return false;
  }
  if (!meta.contentHash) {
    return true;
  }
  return hashContent(content) !== meta.contentHash;
};

/**
 * if / else / end 配对校验（拖错结构在画布上直接标红，防执行报错）。
 * 返回问题列表：[{ cardId, index, message }]
 */
export const validateCardStructure = (cards) => {
  const issues = [];
  // 每层如果块对应一个 hasElse 标记（嵌套用栈）
  const blockStack = []; // { cardId }，元素为该块对应的如果卡
  const elseSeen = []; // 与 blockStack 平行：该块是否已有否则
  cards.forEach((card, index) => {
    if (card.type === "if") {
      blockStack.push(card.id);
      elseSeen.push(false);
    } else if (card.type === "else") {
      if (blockStack.length === 0) {
        issues.push({ cardId: card.id, index, message: "否则卡前面没有对应的如果卡" });
      } else if (elseSeen[elseSeen.length - 1]) {
        issues.push({ cardId: card.id, index, message: "这个如果块已经有否则卡" });
      } else {
        elseSeen[elseSeen.length - 1] = true;
      }
    } else if (card.type === "end") {
      if (blockStack.length === 0) {
        issues.push({ cardId: card.id, index, message: "结束卡前面没有对应的如果卡" });
      } else {
        blockStack.pop();
        elseSeen.pop();
      }
    }
  });
  // 未闭合的如果块：报在对应的如果卡上
  for (const ifId of blockStack) {
    issues.push({ cardId: ifId, index: cards.findIndex((c) => c.id === ifId), message: "这个如果块缺少结束卡" });
  }
  return issues;
};
