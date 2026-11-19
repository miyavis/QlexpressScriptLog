import { ref, reactive, computed, watch, nextTick } from "vue";
import {
  CARD_OPS,
  newCard,
  genScriptFromCards,
  parseScriptToCards,
  collectUndefinedVars,
  hashContent,
  isMetaStale,
  validateCardStructure
} from "./composables/canvasLogic.js";
import {
  normalizeFunctions,
  suggestVarName,
  collectKnownVariables,
  nextParamOnFuncChange,
  mergeFuncOptions,
  BUILTIN_FUNCTIONS
} from "./composables/functionRegistry.js";
import { buildExecutionExport } from "./composables/exportRecord.js";
import { buildScriptExport, parseScriptImport, dedupeImportName } from "./composables/scriptTransfer.js";
import { buildVersionList, restoreVersion } from "./composables/versionHistory.js";
import { diffLines, renderDiffRows } from "./composables/diffPreview.js";
import { filterScripts, filterExecutions } from "./composables/listFilters.js";
import { requestJson } from "./composables/apiClient.js";
import { fmtScalar, truncate, callArgs, callResult, rawExprDesc, parseBranchInfo, buildLineAssigns, buildReturnDesc, buildExecSummary, buildCondText, findCondVerdict } from "./composables/logPresentation.js";
import { buildTimeline } from "./composables/executionTimeline.js";
import { buildTopology } from "./composables/callTopology.js";

const API = "/api";

/** 可选 API token：从 localStorage 读（与后端 app.api-token 对应），Demo 默认空 */
const getApiToken = () => {
  try {
    return localStorage.getItem("rulelog-api-token") || "";
  } catch (e) {
    return "";
  }
};

/** 编辑器行高：与 styles/scripts.css .code-area / .gutter-line 的 line-height:22px 保持一致 */
const EDITOR_LINE_HEIGHT = 22;

/* ---------- 视图状态 ---------- */
const view = ref("scripts"); // 'scripts' | 'executions'
const isRunning = ref(false); // 执行中：防重复提交，按钮可据此禁用
const isRerunning = ref(false); // 历史重跑中：防重复提交
const callViewMode = ref("tree"); // 调用视图：'tree' 树形 | 'topo' 拓扑
const topologyNodes = computed(() =>
  selectedRecord.value ? buildTopology(selectedRecord.value.calls) : []
);

/* ---------- 脚本管理状态 ---------- */
const scripts = ref([]);
const selectedScriptId = ref(null);
const editing = ref(false);
const form = reactive({
  id: "",
  name: "",
  content: "",
  defaultParamsJson: "{}",
  environment: "dev"
});
const versionMessage = ref(""); // 本次保存的版本备注（保存后清空）
const currentScript = computed(() => scripts.value.find((s) => s.id === form.id) || null);

/* ---------- 脚本右侧执行日志状态 ---------- */
const scriptExecutions = ref([]);
const selectedLogTraceId = ref(null);
const selectedLogRecord = ref(null);

/* ---------- 执行记录状态 ---------- */
const executions = ref([]);
const selectedTraceId = ref(null);
const selectedRecord = ref(null);

/* ---------- 辅助 ---------- */
const formatTime = (ts) => {
  if (!ts) return "-";
  const d = new Date(ts);
  return d.toLocaleString("zh-CN", { hour12: false });
};

const formatJson = (v) => {
  if (v == null) return "null";
  try {
    return JSON.stringify(v, null, 2);
  } catch (e) {
    return String(v);
  }
};

/** 紧凑单行 JSON（用于默认参数编辑框，避免多行占位） */
const compactJson = (v) => {
  if (v == null) return "{}";
  try {
    return JSON.stringify(v);
  } catch (e) {
    return String(v);
  }
};

/** 选中执行记录的行级日志，按行号排序（包含未走到的分支行，用于显示“未走此分支”） */
const lineLogGroups = computed(() => {
  if (!selectedLogRecord.value || !selectedLogRecord.value.lineExecutions) {
    return [];
  }
  return Object.values(selectedLogRecord.value.lineExecutions)
    .sort((a, b) => a.lineNo - b.lineNo);
});

/* ---------- 执行时间轴（逐步回放） ---------- */
const timelineIndex = ref(-1); // -1 = 未开始；0..n-1 = 当前回放步骤
const executionTimeline = computed(() =>
  selectedLogRecord.value
    ? buildTimeline(selectedLogRecord.value.lineExecutions)
    : []
);
const timelineCurrent = computed(() =>
  timelineIndex.value >= 0 && timelineIndex.value < executionTimeline.value.length
    ? executionTimeline.value[timelineIndex.value] : null
);
// 切换日志记录时自动重置回放进度
watch(selectedLogRecord, () => { timelineIndex.value = -1; });
/** 前进一步（到末尾回到未开始）；后退一步（未开始回到末尾） */
const timelineStep = (dir) => {
  const n = executionTimeline.value.length;
  if (n === 0) return;
  if (dir > 0) {
    timelineIndex.value = timelineIndex.value >= n - 1 ? -1 : timelineIndex.value + 1;
  } else {
    timelineIndex.value = timelineIndex.value <= -1 ? n - 1 : timelineIndex.value - 1;
  }
};

/** 执行摘要：供业务人员快速了解整体逻辑（纯计算在 logPresentation.js） */
const execSummary = computed(() => buildExecSummary(selectedLogRecord.value));

const formatExprValue = (v) => {
  if (v == null) return "";
  if (typeof v === "string") return v;
  return JSON.stringify(v);
};

/* ---------- 业务友好的行级日志辅助函数 ---------- */

/** 脚本入参（显示在日志顶部一次） */
const inputParams = computed(() => {
  const p = selectedLogRecord.value && selectedLogRecord.value.params;
  if (!p || typeof p !== "object") return [];
  return Object.entries(p).map(([k, v]) => `${k} = ${fmtScalar(v)}`);
});

/**
 * 解析脚本的 如果/否则 结构：返回 行号 → 分支('then'|'else') 映射、如果所在行
 * 用于给每行盖上“则分支/否则分支”标签，并说明未执行的分支原因
 */
const branchInfo = computed(() =>
  parseBranchInfo(selectedLogRecord.value ? selectedLogRecord.value.scriptSnapshot : null)
);

const lineBranch = (le) => branchInfo.value.map[le.lineNo] || null;

/** 行头分支标签 */
const branchChip = (le) => {
  const b = lineBranch(le);
  return b === "then" ? "则分支" : b === "else" ? "否则分支" : null;
};

/** 条件文本：从脚本第 ifLine 行截取 如果(...) 里的内容（纯逻辑在 logPresentation.js） */
const condText = computed(() =>
  buildCondText(
    selectedLogRecord.value ? selectedLogRecord.value.scriptSnapshot : "",
    branchInfo.value.ifLine
  )
);

/** 条件判断结果：取如果行里求过值的布尔运算符（纯逻辑在 logPresentation.js） */
const condVerdict = computed(() =>
  selectedLogRecord.value
    ? findCondVerdict(selectedLogRecord.value.lineExecutions, branchInfo.value.ifLine)
    : null
);

/** 未走分支时的说明文字 */
const notTakenText = (le) => {
  const b = lineBranch(le);
  if (!b || le.hasActivity) return null;
  const v = condVerdict.value;
  const ifLine = branchInfo.value.ifLine;
  if (b === "then" && v === false) {
    return `第 ${ifLine} 行条件不成立，未走此分支`;
  }
  if (b === "else" && v !== false) {
    return `第 ${ifLine} 行条件成立，未走此分支`;
  }
  return null;
};

/** 行级日志业务描述函数已从 composables/logPresentation.js 抽取，这里保留别名以兼容既有导出名 */
const lineAssigns = buildLineAssigns;
const leReturn = buildReturnDesc;
// callArgs / callResult / rawExprDesc / fmtScalar / truncate 直接从 logPresentation 导入使用


/* ---------- 脚本行级日志状态 ---------- */
const hoverLineNo = ref(null);
const highlightLineNo = ref(null);
/** 左侧悬停行时，右侧高亮对应的调用 startTime */
const highlightedCallStartTime = ref(null);
/** 执行摘要是否展开 */
const execSummaryExpanded = ref(false);
/** 已展开的调用 key 集合（startTime 的字符串形式） */
const expandedCallKeys = ref(new Set());
/** 顶层调用列表（按执行时间顺序） */
const topLevelCalls = computed(() => {
  if (!selectedLogRecord.value || !selectedLogRecord.value.calls) {
    return [];
  }
  return selectedLogRecord.value.calls;
});

/** 切换调用展开/折叠 */
const toggleCallExpand = (startTime) => {
  const key = String(startTime);
  if (expandedCallKeys.value.has(key)) {
    expandedCallKeys.value.delete(key);
  } else {
    expandedCallKeys.value.add(key);
  }
};

const isCallExpanded = (startTime) => {
  return expandedCallKeys.value.has(String(startTime));
};

/** 右侧点击调用时，高亮左侧对应行号 */
const highlightLine = (lineNo) => {
  highlightLineNo.value = lineNo;
};

/** 左侧悬停行时，根据行号查找对应的调用并高亮右侧 */
const highlightCallByLine = (lineNo) => {
  if (!lineNo || !selectedLogRecord.value || !selectedLogRecord.value.calls) {
    highlightedCallStartTime.value = null;
    return;
  }
  const findCall = (calls) => {
    for (const c of calls) {
      if (c.lineNo === lineNo) {
        return c;
      }
      if (c.children && c.children.length) {
        const found = findCall(c.children);
        if (found) return found;
      }
    }
    return null;
  };
  const found = findCall(selectedLogRecord.value.calls);
  highlightedCallStartTime.value = found ? String(found.startTime) : null;
};

/** 脚本内容按行拆分，用于左侧行号渲染 */
const scriptLines = computed(() => {
  if (!form.content) return [];
  return form.content.split("\n");
});

const onEditorEnter = () => {
  hoverLineNo.value = 1;
};
const onEditorMouseMove = (e) => {
  const textarea = e.target;
  if (!textarea) return;
  const rect = textarea.getBoundingClientRect();
  const lineHeight = EDITOR_LINE_HEIGHT;
  const relativeY = e.clientY - rect.top - parseFloat(getComputedStyle(textarea).paddingTop || '10');
  const lineNo = Math.floor(relativeY / lineHeight) + 1;
  if (lineNo >= 1 && lineNo <= scriptLines.value.length) {
    hoverLineNo.value = lineNo;
  }
};

/* ---------- 卡片画布 ---------- */
const canvasMode = ref(false);
const canvasCards = ref([]);
const dragPayload = ref(null);
const hoverCardId = ref(null);
const logHoverCardId = ref(null);
const selectedCardId = ref(null);

const CARD_TYPES = [
  { type: "call", label: "取数卡", icon: "🔎", hint: "变量 = 中文函数(参数)" },
  { type: "prepcall", label: "取数卡·带参", icon: "🧰", hint: "先算参数再调服务（生成两行）" },
  { type: "calc", label: "计算卡", icon: "🧮", hint: "变量 = 表达式" },
  { type: "log", label: "打印卡", icon: "📝", hint: "记录日志(标题, 内容)" },
  { type: "if", label: "条件卡", icon: "❓", hint: "如果 (A 运算 B) 则 {" },
  { type: "else", label: "否则卡", icon: "🔀", hint: "} 否则 {" },
  { type: "end", label: "结束卡", icon: "🔚", hint: "}" },
  { type: "return", label: "返回卡", icon: "🎯", hint: "返回 表达式" },
  { type: "decisiontable", label: "决策表", icon: "▦", hint: "表格化规则（First/Collect 命中）" },
  { type: "text", label: "原样卡", icon: "📄", hint: "保留脚本原文（高级语法用）" }
];

/** 卡片 → 中文脚本（纯逻辑在 composables/canvasLogic.js，生成期同步记录行号映射） */
const genCanvas = computed(() => genScriptFromCards(canvasCards.value));

watch(canvasCards, () => {
  if (canvasMode.value) {
    form.content = genCanvas.value.script;
    lastCanvasScript.value = form.content;
  }
}, { deep: true });

const enterCanvas = () => {
  // 从文本模式切换过来：若脚本文本被手工改过（或画布为空），把当前脚本解析成卡片
  if (canvasCards.value.length === 0 || form.content !== lastCanvasScript.value) {
    canvasCards.value = parseScriptToCards(form.content);
  }
  canvasMode.value = true;
  form.content = genCanvas.value.script;
};

/** 把当前生成的/导入的脚本记下来，用于判断“文本是否被手工改过” */
const lastCanvasScript = ref(null);

const addCard = (type) => {
  const card = newCard(type);
  // 拖入即配置：取数类卡片按函数名自动推荐结果变量名，并聚焦第一个输入框
  if ((card.type === "call" || card.type === "prepcall") && !card.varName) {
    card.varName = suggestVarName(card.func);
  }
  // 依赖自动化：参数自动带上当前可用的第一个变量（脚本入参优先），没有候选才留空
  const candidate = knownVariables.value[0] || "";
  if (card.type === "call" && !card.param) {
    card.param = candidate;
  }
  if (card.type === "prepcall" && !card.prepExpr) {
    card.prepExpr = candidate;
  }
  canvasCards.value.push(card);
  focusCardFirstInput(card.id);
};

const removeCard = (idx) => {
  canvasCards.value.splice(idx, 1);
};

const moveCard = (idx, dir) => {
  const target = idx + dir;
  if (target < 0 || target >= canvasCards.value.length) return;
  const cards = canvasCards.value;
  const tmp = cards[idx];
  cards[idx] = cards[target];
  cards[target] = tmp;
};

// HTML5 拖拽：部件 → 画布的同时支持画布内卡片重排
const onPaletteDragStart = (e, type) => {
  dragPayload.value = { kind: "new", type };
  if (e.dataTransfer) e.dataTransfer.setData("text/plain", "new:" + type);
};
const onCardDragStart = (e, idx) => {
  dragPayload.value = { kind: "move", index: idx };
  if (e.dataTransfer) e.dataTransfer.setData("text/plain", "move:" + idx);
};
const onCardDrop = (e, idx) => {
  e.preventDefault();
  e.stopPropagation();
  const p = dragPayload.value;
  dragPayload.value = null;
  if (!p) return;
  if (p.kind === "new") {
    const card = newCard(p.type);
    if ((card.type === "call" || card.type === "prepcall") && !card.varName) {
      card.varName = suggestVarName(card.func);
    }
    canvasCards.value.splice(idx, 0, card);
    focusCardFirstInput(card.id);
  } else if (p.kind === "move") {
    const cards = canvasCards.value;
    const moved = cards.splice(p.index, 1)[0];
    cards.splice(idx > p.index ? idx - 1 : idx, 0, moved);
  }
};

/** 分支缩进深度：如果 → 内部卡片缩进 1 层；结束卡收回一层 */
const cardDepth = computed(() => {
  let depth = 0;
  return canvasCards.value.map((c) => {
    let display;
    if (c.type === "end") {
      depth = Math.max(0, depth - 1);
      display = depth;
    } else if (c.type === "else") {
      display = Math.max(0, depth - 1);
    } else {
      display = depth;
      if (c.type === "if") depth += 1;
    }
    return display;
  });
});

const TYPE_META = {
  call: { icon: "🔎", label: "取数", cls: "t-call" },
  prepcall: { icon: "🧰", label: "取数·带参", cls: "t-prep" },
  calc: { icon: "🧮", label: "计算", cls: "t-calc" },
  log: { icon: "📝", label: "打印", cls: "t-log" },
  if: { icon: "❓", label: "如果", cls: "t-if" },
  else: { icon: "🔀", label: "否则", cls: "t-else" },
  end: { icon: "🔚", label: "结束", cls: "t-end" },
  return: { icon: "🎯", label: "返回", cls: "t-return" },
  decisiontable: { icon: "▦", label: "决策表", cls: "t-decision" },
  text: { icon: "📄", label: "原样", cls: "t-text" }
};
const cardIcon = (type) => (TYPE_META[type] || {}).icon || "▢";
const typeClass = (type) => (TYPE_META[type] || {}).cls || "";
const cardTitle = (c) => {
  if (c.type === "call") return `取数 · ${c.varName || "?"} = ${c.func || "?"}()`;
  if (c.type === "prepcall") return `取数·带参 · ${c.prepName || "?"} → ${c.varName || "?"} = ${c.func || "?"}()`;
  if (c.type === "calc") return `计算 · ${c.varName || "?"}`;
  if (c.type === "log") return `打印 · ${c.title || "?"}`;
  if (c.type === "if") return `如果 · ${c.left || "?"} ${c.op || ""} ${c.right || "?"}`;
  if (c.type === "else") return "否则";
  if (c.type === "end") return "条件块结束";
  if (c.type === "return") return `返回 · ${c.expr || "?"}`;
  if (c.type === "decisiontable") return `决策表 · ${c.resultVar || "结果"}（${(c.rows || []).length} 行）`;
  if (c.type === "text") return `原样 · ${String(c.raw || "").slice(0, 18) || "?"}`;
  return "";
};

/** 卡片 ↔ 行号映射（画布模式专用） */
const cardMapById = (id) => genCanvas.value.sourceMap.find((m) => m.cardId === id) || null;
const cardRangeText = (id) => {
  const m = cardMapById(id);
  if (!m || m.empty) return "";
  return m.startLine === m.endLine ? `第 ${m.startLine} 行` : `第 ${m.startLine}~${m.endLine} 行`;
};
const cardOfLineId = (lineNo) => {
  const m = genCanvas.value.sourceMap.find((x) => !x.empty && lineNo >= x.startLine && lineNo <= x.endLine);
  return m ? m.cardId : null;
};
/** if/else/end 配对 + 依赖缺失两类校验：问题卡片在画布上标红（在执行报错前给出人看得懂的原因） */
const scriptParamKeys = computed(() => {
  try {
    return Object.keys(JSON.parse(form.defaultParamsJson || "{}"));
  } catch (e) {
    return [];
  }
});
const structureIssues = computed(() => validateCardStructure(canvasCards.value));
const undefinedVarIssues = computed(() =>
  collectUndefinedVars(canvasCards.value, scriptParamKeys.value)
);
const issueMap = computed(() => {
  const m = {};
  for (const it of [...structureIssues.value, ...undefinedVarIssues.value]) {
    if (!m[it.cardId]) m[it.cardId] = it.message;
  }
  return m;
});
const cardIssue = (id) => issueMap.value[id] || null;

/** 该卡片是否被选中/悬停（画布上 + 来自日志悬停） */
const cardSelected = (id) => id === selectedCardId.value || id === logHoverCardId.value;
/** 日志行高亮集合：卡片悬停/选中时对应所有行 */
const activeLineSet = computed(() => {
  const s = new Set();
  if (!canvasMode.value) return s;
  for (const id of [hoverCardId.value, selectedCardId.value, logHoverCardId.value]) {
    if (!id) continue;
    const m = cardMapById(id);
    if (m && !m.empty) {
      for (let i = m.startLine; i <= m.endLine; i++) s.add(i);
    }
  }
  return s;
});
const onLogLineEnter = (lineNo) => {
  if (!canvasMode.value) return;
  const id = cardOfLineId(lineNo);
  if (id) logHoverCardId.value = id;
};
const onLogLineLeave = () => {
  logHoverCardId.value = null;
};
/** 日志行头的卡片名标签（画布模式专用；无映射自动不显示） */
const cardByIdMap = computed(() => {
  const m = {};
  for (const c of canvasCards.value) m[c.id] = c;
  return m;
});
const cardChip = (lineNo) => {
  if (!canvasMode.value) return null;
  const id = cardOfLineId(lineNo);
  if (!id) return null;
  const c = cardByIdMap.value[id];
  if (!c) return null;
  return `${cardIcon(c.type)} ${cardTitle(c)}`;
};
const onLogLineClick = (lineNo) => {
  if (!canvasMode.value) return;
  const id = cardOfLineId(lineNo);
  if (id) {
    selectedCardId.value = id;
    // 视觉定位：长画布上把命中的卡片滚进视野
    nextTick(() => {
      const el = document.querySelector(`[data-card-id="${id}"]`);
      if (el) el.scrollIntoView({ behavior: "smooth", block: "nearest" });
    });
  }
};

/* ---------- 脚本管理 API ---------- */
const loadScripts = async () => {
  try {
    scripts.value = await requestJson(`${API}/scripts`, {}, fetch, getApiToken());
  } catch (e) {
    alert("加载脚本列表失败：" + e.message);
  }
};

const selectScript = (s) => {
  selectedScriptId.value = s.id;
  editing.value = true;
  form.id = s.id;
  form.name = s.name || "";
  form.content = s.content || "";
  form.defaultParamsJson = compactJson(s.defaultParams);
  form.environment = s.environment || "dev";
  // 默认展示卡片画布：meta.cards 未过期（contentHash 匹配）时用保存的卡片；
  // 过期（外部手改过内容）或缺失时，以脚本文本为真相重新反解析，避免旧卡片静默覆盖手改内容
  const metaCards = s.meta && Array.isArray(s.meta.cards) ? s.meta.cards : null;
  const useSavedCards = metaCards && !isMetaStale(s.content, s.meta);
  canvasCards.value = useSavedCards
    ? JSON.parse(JSON.stringify(metaCards))
    : parseScriptToCards(s.content || "");
  hoverCardId.value = null;
  logHoverCardId.value = null;
  selectedCardId.value = null;
  canvasMode.value = true;
  // 同步生成结果并记录快照（切到文本模式时保持一致）
  form.content = genCanvas.value.script;
  lastCanvasScript.value = form.content;
  refreshSavedSnapshot();
  // 加载该脚本的执行日志，实现“脚本右侧日志一一对应”
  loadScriptExecutions(s.id);
};

const newScript = () => {
  selectedScriptId.value = null;
  editing.value = true;
  form.id = "";
  form.name = "";
  form.content = "";
  form.defaultParamsJson = "{}";
  form.environment = "dev";
  versionMessage.value = "";
  timelineIndex.value = -1;
  canvasCards.value = [];
  canvasMode.value = true;
  lastCanvasScript.value = "";
  refreshSavedSnapshot();
};

const cancelEdit = () => {
  editing.value = false;
  selectedScriptId.value = null;
  selectedLogTraceId.value = null;
  selectedLogRecord.value = null;
  scriptExecutions.value = [];
  expandedCallKeys.value = new Set();
  canvasMode.value = false;
  canvasCards.value = [];
  hoverCardId.value = null;
  logHoverCardId.value = null;
  selectedCardId.value = null;
  form.id = form.name = form.content = form.defaultParamsJson = "";
  form.environment = "dev";
  versionMessage.value = "";
  timelineIndex.value = -1;
};

/** 保存快照：检测“有未保存的修改”（执行前提醒，避免执行记录与脚本版本错位） */
const savedSnapshot = ref({ name: "", content: "", paramsJson: "", environment: "dev" });
const refreshSavedSnapshot = () => {
  savedSnapshot.value = {
    name: form.name, content: form.content, paramsJson: form.defaultParamsJson,
    environment: form.environment
  };
};
const hasUnsavedChanges = computed(() =>
  editing.value && !!form.id && (
    form.name !== savedSnapshot.value.name ||
    form.content !== savedSnapshot.value.content ||
    form.defaultParamsJson !== savedSnapshot.value.paramsJson ||
    form.environment !== savedSnapshot.value.environment
  )
);

const saveScript = async (keepOpen = false) => {
  let defaultParams = {};
  try {
    defaultParams = JSON.parse(form.defaultParamsJson || "{}");
  } catch (e) {
    alert("默认参数不是合法的 JSON");
    return;
  }

  const payload = {
    id: form.id || null,
    name: form.name,
    content: form.content,
    defaultParams,
    versionMessage: versionMessage.value || null,
    environment: form.environment || "dev"
  };
  // meta 是 content 的派生视图（单一事实源）：无论哪种模式都把卡片+映射+hash 一起保存。
  // 文本模式保存时反解析当前文本生成卡片，避免 meta 丢失后打开靠“猜”恢复。
  const cardsForMeta = canvasMode.value
    ? JSON.parse(JSON.stringify(canvasCards.value))
    : parseScriptToCards(form.content);
  payload.meta = {
    cards: cardsForMeta,
    cardSourceMap: genScriptFromCards(cardsForMeta).sourceMap,
    contentHash: hashContent(form.content)
  };

  // 保存前静态校验：语法错误与未定义变量前移，不让有问题的脚本入库
  try {
    const check = await requestJson(`${API}/executions/check`, {
      method: "POST",
      body: JSON.stringify({ content: form.content, paramKeys: Object.keys(defaultParams) })
    }, fetch, getApiToken());
    if (check && check.valid === false) {
      if (!confirm("静态检查发现问题：" + check.message + "\n\n仍要保存吗？（建议先修正）")) {
        return;
      }
    }
  } catch (e) {
    console.warn("静态校验服务不可用，跳过：" + e.message);
  }

  try {
    if (form.id) {
      await requestJson(`${API}/scripts/${form.id}`, {
        method: "PUT", body: JSON.stringify(payload)
      }, fetch, getApiToken());
    } else {
      await requestJson(`${API}/scripts`, {
        method: "POST", body: JSON.stringify(payload)
      }, fetch, getApiToken());
    }
    refreshSavedSnapshot();
    versionMessage.value = "";
    await loadScripts();
    if (!keepOpen) {
      cancelEdit();
    }
  } catch (e) {
    alert("保存脚本失败：" + e.message);
  }
};

const deleteScript = async (id) => {
  if (!confirm("确定删除该脚本吗？")) return;
  try {
    await requestJson(`${API}/scripts/${id}`, { method: "DELETE" }, fetch, getApiToken());
    await loadScripts();
    if (selectedScriptId.value === id) {
      cancelEdit();
    }
  } catch (e) {
    alert("删除失败：" + e.message);
  }
};

const runScript = async () => {
  if (isRunning.value) return; // 防重复提交
  if (!form.id) {
    alert("请先选择或新建一个脚本");
    return;
  }
  // 未保存提醒：未保存就执行会导致执行记录与脚本版本错位，事后排查会困惑
  if (hasUnsavedChanges.value) {
    if (!confirm("当前有未保存的修改。点击“确定”先保存再执行；点击“取消”放弃本次执行。")) {
      return;
    }
    await saveScript(true);
  }
  let params = {};
  try {
    params = JSON.parse(form.defaultParamsJson || "{}");
  } catch (e) {
    alert("默认参数不是合法的 JSON");
    return;
  }
  const scriptId = form.id;
  isRunning.value = true;
  try {
    const record = await requestJson(`${API}/executions/run?scriptId=${encodeURIComponent(scriptId)}`, {
      method: "POST", body: JSON.stringify(params)
    }, fetch, getApiToken());
    // 竞态保护：执行期间用户切到别的脚本，就别把旧结果显示到当前页
    if (form.id !== scriptId) return;
    // 执行完成后直接显示到右侧日志框（不跳转页面）
    selectedLogRecord.value = record;
    selectedLogTraceId.value = record.traceId;
    // 刷新该脚本的执行记录列表
    await loadScriptExecutions(scriptId);
  } catch (e) {
    alert("执行脚本失败：" + e.message);
  } finally {
    isRunning.value = false;
  }
};

/* ---------- 执行记录 API ---------- */
const loadExecutions = async () => {
  try {
    // 后端上限 200（与 max-execution-records 一致），一次拉全，避免固定 100 漏掉更早记录
    executions.value = await requestJson(`${API}/executions?limit=200`, {}, fetch, getApiToken());
  } catch (e) {
    alert("加载执行记录失败：" + e.message);
  }
};

/** 加载指定脚本的执行日志（供脚本页右侧一一对应展示）；带竞态保护，旧请求结果不会覆盖新脚本 */
let scriptLogRequestSeq = 0;
const loadScriptExecutions = async (scriptId) => {
  if (!scriptId) return;
  const seq = ++scriptLogRequestSeq;
  try {
    const list = await requestJson(
      `${API}/executions/script/${encodeURIComponent(scriptId)}?limit=50`, {}, fetch, getApiToken());
    // 竞态：期间用户切到别的脚本（或又发了新请求），丢弃这次旧结果
    if (seq !== scriptLogRequestSeq || form.id !== scriptId) return;
    scriptExecutions.value = list;
    // 如果之前选中的日志不在新列表中，清空选中
    if (selectedLogTraceId.value && !scriptExecutions.value.some(r => r.traceId === selectedLogTraceId.value)) {
      selectedLogTraceId.value = null;
      selectedLogRecord.value = null;
    }
  } catch (e) {
    console.warn("加载脚本执行日志失败：" + e.message);
  }
};

/** 导出执行记录为 JSON 文件（traceId 随文件走，发给他人即可离线复盘） */
const downloadExecution = (record) => {
  if (!record) return;
  try {
    const { filename, body } = buildExecutionExport(record);
    const blob = new Blob([body], { type: "application/json;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  } catch (e) {
    alert("导出失败：" + e);
  }
};

/* ---------- 版本历史（每次保存自动快照"改之前"的版本，可载入回滚） ---------- */
const showHistory = ref(false);

/** 当前编辑脚本的历史版本列表（新→旧，最多 10 条） */
const historyVersions = computed(() => {
  const cur = scripts.value.find((s) => s.id === form.id);
  return cur ? buildVersionList(cur) : [];
});

/** 审计日志（最近 N 条） */
const auditLogs = ref([]);
const showAudit = ref(false);
const toggleAudit = () => {
  showAudit.value = !showAudit.value;
  if (showAudit.value) loadAudit();
};
const loadAudit = async () => {
  try {
    auditLogs.value = await requestJson(`${API}/audit?limit=50`, {}, fetch, getApiToken());
  } catch (e) {
    console.warn("审计日志加载失败：" + e.message);
  }
};

/** 评审状态流转：submit / approve / reject */
const reviewAction = async (action) => {
  if (!form.id) return;
  const labels = { submit: "提交评审", approve: "通过评审并发布", reject: "驳回评审" };
  if (!confirm(`确定「${labels[action] || action}」吗？`)) return;
  try {
    await requestJson(`${API}/scripts/${encodeURIComponent(form.id)}/review?action=${action}`,
      { method: "POST" }, fetch, getApiToken());
    await loadScripts();
    alert(`已${labels[action] || action}。`);
  } catch (e) {
    alert("评审操作失败：" + e.message);
  }
};

/** 切换/新建分支 */
const switchBranch = async () => {
  if (!form.id) return;
  const name = prompt("输入分支名（新名字即创建新分支）：", (currentScript.value && currentScript.value.branch) || "main");
  if (!name || !name.trim()) return;
  try {
    await requestJson(`${API}/scripts/${encodeURIComponent(form.id)}/branch?name=${encodeURIComponent(name.trim())}`,
      { method: "POST" }, fetch, getApiToken());
    await loadScripts();
    alert(`已切换到分支「${name.trim()}」。`);
  } catch (e) {
    alert("切换分支失败：" + e.message);
  }
};

/** 发布指定版本（标记为已发布），刷新脚本列表 */
const publishVersion = async (ver) => {
  const no = ver && ver.versionNo;
  if (!no || !form.id) return;
  if (!confirm(`将 v${no} 标记为已发布版本？`)) return;
  try {
    await requestJson(`${API}/scripts/${encodeURIComponent(form.id)}/publish?versionNo=${no}`,
      { method: "POST" }, fetch, getApiToken());
    await loadScripts();
    alert(`v${no} 已发布。`);
  } catch (e) {
    alert("发布失败：" + e.message);
  }
};

/** 载入历史版本到编辑器；载入后点"保存"即完成回滚（保存动作会先为当前版本留快照，回滚可撤销） */
const loadVersion = (ver) => {
  const cur = scripts.value.find((s) => s.id === form.id);
  const payload = restoreVersion(cur, ver);
  if (!payload) {
    alert("该版本内容为空，无法载入");
    return;
  }
  if (hasUnsavedChanges.value && !confirm("当前有未保存的修改，载入历史版本会覆盖编辑器内容，继续吗？")) {
    return;
  }
  form.name = payload.name;
  form.content = payload.content;
  form.defaultParamsJson = JSON.stringify(payload.defaultParams, null, 2);
  // 画布卡片：版本自带 meta 且 hash 匹配（布局未过期）→ 原样恢复画布；否则按内容重新反解析
  const verMeta = payload.meta;
  if (verMeta && Array.isArray(verMeta.cards) && !isMetaStale(payload.content, verMeta)) {
    canvasCards.value = JSON.parse(JSON.stringify(verMeta.cards));
    canvasMode.value = true;
    lastCanvasScript.value = payload.content;
  } else {
    canvasCards.value = parseScriptToCards(payload.content);
    canvasMode.value = false;
  }
  showHistory.value = false;
  alert("已载入历史版本。检查无误后点击「保存」完成回滚；如不想要这次回滚，历史里也保留了回滚前的版本。");
};

/* ---------- 版本对比（载入前先看"当前 ↔ 该版本"差异） ---------- */
const diffVerIndex = ref(-1); // 当前展开 diff 的版本下标（historyVersions 的下标）

/** 展开哪一版的 diff；再点一次收起 */
const toggleDiff = (idx) => {
  diffVerIndex.value = diffVerIndex.value === idx ? -1 : idx;
};

/** 对比基准 = 最近一次保存的脚本文本（不含编辑器里未保存的改动） */
const savedContentOfCurrent = computed(() => {
  const cur = scripts.value.find((s) => s.id === form.id);
  return cur ? cur.content : "";
});

/** 当前展开版本的 diff 行 */
const activeDiffRows = computed(() => {
  const ver = historyVersions.value[diffVerIndex.value];
  if (!ver) return [];
  return diffLines(savedContentOfCurrent.value, ver.content);
});

const activeDiffSummary = computed(() => renderDiffRows(activeDiffRows.value));

/* ---------- 脚本搜索 / 执行记录筛选 ---------- */
const scriptKeyword = ref("");
const envFilter = ref("all"); // all | dev | staging | prod
const visibleScripts = computed(() => {
  const byKeyword = filterScripts(scripts.value, scriptKeyword.value);
  if (envFilter.value === "all") return byKeyword;
  return byKeyword.filter((s) => (s.environment || "dev") === envFilter.value);
});

const execKeyword = ref("");
const execStatus = ref(""); // "" 全部 | "ok" 成功 | "fail" 失败
const visibleExecutions = computed(() =>
  filterExecutions(executions.value, { status: execStatus.value, keyword: execKeyword.value })
);

/** 导出当前编辑中的脚本为 JSON 文件（含画布 meta，对方导入后画布布局原样可用） */
const exportCurrentScript = () => {
  if (!form.content) {
    alert("请先填写脚本内容再导出");
    return;
  }
  try {
    let defaultParams = {};
    try {
      defaultParams = JSON.parse(form.defaultParamsJson || "{}");
    } catch (e) {
      /* 参数 JSON 不合法时按空参数导出，不阻塞分享 */
    }
    // meta 派生方式与保存一致：画布模式用当前卡片，文本模式反解析当前文本
    const cardsForMeta = canvasMode.value
      ? JSON.parse(JSON.stringify(canvasCards.value))
      : parseScriptToCards(form.content);
    const meta = {
      cards: cardsForMeta,
      cardSourceMap: genScriptFromCards(cardsForMeta).sourceMap,
      contentHash: hashContent(form.content)
    };
    const { filename, body } = buildScriptExport({
      name: form.name,
      content: form.content,
      defaultParams,
      meta
    });
    const blob = new Blob([body], { type: "application/json;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  } catch (e) {
    alert("导出失败：" + e);
  }
};

/** 导入脚本：读 JSON 文件 → POST /api/scripts（id 由后端生成）→ 刷新列表并选中新脚本 */
const importScriptFromFile = async (ev) => {
  const file = ev.target.files && ev.target.files[0];
  ev.target.value = ""; // 允许重复导入同一文件
  if (!file) return;
  try {
    const text = await file.text();
    // 导入文件大小上限（与后端 100KB 脚本上限对齐，留一点余量给元数据）
    if (text.length > 200_000) {
      alert("导入文件过大（超过 200KB），请检查内容");
      return;
    }
    const req = parseScriptImport(text);
    // 重名自动改名：打折规则 → 打折规则（导入）→ 打折规则（导入 2）…避免和已有脚本混淆
    req.name = dedupeImportName(req.name, scripts.value.map((s) => s.name));
    const created = await requestJson(`${API}/scripts`, {
      method: "POST", body: JSON.stringify(req)
    }, fetch, getApiToken());
    await loadScripts();
    if (created && created.id) {
      const fresh = scripts.value.find((s) => s.id === created.id);
      if (fresh) selectScript(fresh);
    }
    alert(`已导入脚本「${created.name}」`);
  } catch (e) {
    alert(String(e.message || e));
  }
};

const selectExecution = (r) => {
  selectedTraceId.value = r.traceId;
  selectedRecord.value = r;
};

/** 用历史记录的参数快照重新执行（生成新记录），完成后刷新列表并选中新记录 */
const rerunRecord = async (record) => {
  if (isRerunning.value || !record) return;
  isRerunning.value = true;
  try {
    const fresh = await requestJson(
      `${API}/executions/rerun?traceId=${encodeURIComponent(record.traceId)}`,
      { method: "POST" }, fetch, getApiToken()
    );
    await loadExecutions();
    if (fresh && fresh.traceId) {
      const found = executions.value.find((r) => r.traceId === fresh.traceId);
      selectExecution(found || fresh);
    }
    alert("已用该记录的参数重新执行，结果见详情。");
  } catch (e) {
    alert("重跑失败：" + e.message);
  } finally {
    isRerunning.value = false;
  }
};

/** 切换脚本右侧日志详情展开/折叠 */
const toggleLogDetail = (r) => {
  if (selectedLogTraceId.value === r.traceId) {
    // 已展开则折叠
    selectedLogTraceId.value = null;
    selectedLogRecord.value = null;
  } else {
    selectedLogTraceId.value = r.traceId;
    selectedLogRecord.value = r;
  }
};

/* 展开/折叠 Service 调用节点 */
const toggleCall = (call) => {
  call.expanded = !call.expanded;
};


/* ---------- 函数注册表（产品化：可用函数由后端注册表下发，不再写死在前端） ---------- */
const registryFunctions = ref([...BUILTIN_FUNCTIONS]);

const loadFunctionRegistry = async () => {
  try {
    const fns = normalizeFunctions(await requestJson(`${API}/functions`, {}, fetch, getApiToken()));
    if (fns.length) {
      registryFunctions.value = fns;
    }
  } catch (e) {
    console.warn("函数注册表拉取失败，使用内置函数兜底：" + e.message);
  }
};

/** 函数的参数提示（作参数输入框占位符），未找到返回空串 */
const funcParamHint = (name) => {
  const f = registryFunctions.value.find((x) => x.name === name);
  return f ? f.paramHint : "";
};

/** 换函数时自动推荐结果变量名（只在变量名为空时接管），并把参数跟随切换：
 *  空参或仍是某函数默认提示值 → 换成新函数的 paramHint；用户自选变量不动 */
const onFuncChange = (card) => {
  if (!card.varName && card.func) {
    card.varName = suggestVarName(card.func);
  }
  if (card.type === "call") {
    const hints = registryFunctions.value.map((f) => f.paramHint).filter(Boolean);
    card.param = nextParamOnFuncChange(card.param, funcParamHint(card.func), hints);
  }
};

/** 函数下拉选项：注册表 + 画布上已使用的函数（保证 select 加载规则后如实显示） */
const funcOptions = computed(() =>
  mergeFuncOptions(
    registryFunctions.value,
    canvasCards.value.filter((c) => c.type === "call" || c.type === "prepcall").map((c) => c.func)
  )
);

/** 表达式/值输入的变量补全候选：入参键 + 画布上已定义的变量 */
const knownVariables = computed(() =>
  collectKnownVariables(canvasCards.value, scriptParamKeys.value)
);

/** 决策表：加一行（列数与 conditions 对齐） */
const addDecisionRow = (card) => {
  if (!card) return;
  card.rows.push({ cells: (card.conditions || []).map(() => ""), result: "" });
};
/** 决策表：加一列（条件 + 所有行补一个空格子） */
const addDecisionColumn = (card) => {
  if (!card) return;
  card.conditions.push("新条件");
  for (const row of card.rows || []) {
    row.cells.push("");
  }
};

/** 复制卡片（含内容），插到当前卡片之后 */
const duplicateCard = (idx) => {
  const src = canvasCards.value[idx];
  if (!src) return;
  const copy = { ...JSON.parse(JSON.stringify(src)), id: newCard("text").id };
  canvasCards.value.splice(idx + 1, 0, copy);
};

/** 新增卡片后自动聚焦第一个输入框，拖入即配置 */
const focusCardFirstInput = (cardId) => {
  nextTick(() => {
    const el = document.querySelector(`[data-card-id="${cardId}"] .card-input`);
    if (el) el.focus();
  });
};

/* ---------- 页面切换（布局壳用） ---------- */
const goView = (v) => { view.value = v; };

export {
  API,
  view,
  scripts,
  selectedScriptId,
  editing,
  form,
  scriptExecutions,
  selectedLogTraceId,
  selectedLogRecord,
  executions,
  selectedTraceId,
  selectedRecord,
  formatTime,
  formatJson,
  compactJson,
  lineLogGroups,
  execSummary,
  executionTimeline,
  timelineIndex,
  timelineCurrent,
  timelineStep,
  truncate,
  formatExprValue,
  fmtScalar,
  inputParams,
  branchInfo,
  lineBranch,
  branchChip,
  condText,
  condVerdict,
  notTakenText,
  lineAssigns,
  leReturn,
  callArgs,
  callResult,
  rawExprDesc,
  hoverLineNo,
  highlightLineNo,
  highlightedCallStartTime,
  execSummaryExpanded,
  expandedCallKeys,
  topLevelCalls,
  toggleCallExpand,
  isCallExpanded,
  highlightLine,
  highlightCallByLine,
  scriptLines,
  onEditorEnter,
  onEditorMouseMove,
  canvasMode,
  canvasCards,
  dragPayload,
  hoverCardId,
  logHoverCardId,
  selectedCardId,
  CARD_TYPES,
  genCanvas,
  enterCanvas,
  lastCanvasScript,
  addCard,
  removeCard,
  moveCard,
  onPaletteDragStart,
  onCardDragStart,
  onCardDrop,
  cardDepth,
  TYPE_META,
  cardIcon,
  typeClass,
  cardTitle,
  cardMapById,
  cardRangeText,
  cardOfLineId,
  structureIssues,
  issueMap,
  cardIssue,
  cardSelected,
  activeLineSet,
  onLogLineEnter,
  onLogLineLeave,
  cardByIdMap,
  cardChip,
  onLogLineClick,
  loadScripts,
  selectScript,
  newScript,
  cancelEdit,
  savedSnapshot,
  refreshSavedSnapshot,
  hasUnsavedChanges,
  saveScript,
  deleteScript,
  runScript,
  isRunning,
  loadExecutions,
  loadScriptExecutions,
  downloadExecution,
  showHistory,
  historyVersions,
  loadVersion,
  versionMessage,
  currentScript,
  publishVersion,
  reviewAction,
  switchBranch,
  addDecisionRow,
  addDecisionColumn,
  auditLogs,
  showAudit,
  toggleAudit,
  loadAudit,
  diffVerIndex,
  toggleDiff,
  savedContentOfCurrent,
  activeDiffRows,
  activeDiffSummary,
  scriptKeyword,
  envFilter,
  visibleScripts,
  execKeyword,
  execStatus,
  visibleExecutions,
  exportCurrentScript,
  importScriptFromFile,
  selectExecution,
  rerunRecord,
  isRerunning,
  callViewMode,
  topologyNodes,
  toggleLogDetail,
  toggleCall,
  registryFunctions,
  loadFunctionRegistry,
  funcOptions,
  funcParamHint,
  onFuncChange,
  knownVariables,
  duplicateCard,
  focusCardFirstInput,
  goView
};
