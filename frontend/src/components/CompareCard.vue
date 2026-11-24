<template>
  <div class="cc-root">
    <!-- 数据池：点选复制引用，和独立比对页一致 -->
    <div class="cc-pool">
      <span class="cc-pool-title">可用数据（点选复制）</span>
      <button v-for="p in dataPool" :key="p.expr" class="cc-pool-chip" :title="p.hint" @click="copyExpr(p.expr)">{{ p.expr }}</button>
    </div>

    <!-- 取数节点 -->
    <div class="cc-section-head">① 取数节点
      <button class="btn-secondary sm" @click="openCompareLibrary">📚 规则库</button>
      <button class="btn-secondary sm" @click="addFetch">＋ 取数</button>
    </div>
    <div v-for="(n, i) in rule.fetchNodes" :key="n.id" class="cc-node"
         draggable="true" @dragstart="dragIdx = i" @dragover.prevent @drop="dropNode(i)">
      <div class="cc-node-head">
        <span class="cc-drag" title="拖动排序">⠿</span>
        <span class="cc-node-title">取数{{ i + 1 }}</span>
        <input v-model="n.name" class="card-input" placeholder="名称" />
        <input v-model="n.resultKey" class="card-input" placeholder="结果Key" />
        <button class="card-btn danger" @click="rule.fetchNodes.splice(i,1)">✕</button>
      </div>
      <div class="cc-node-row">
        <select v-model="n.sourceType" class="card-input">
          <option value="CONST">内置数据</option>
          <option value="CONTEXT">上游结果</option>
          <option value="SCRIPT">执行脚本</option>
          <option value="FUNCTION">调用函数</option>
          <option value="DERIVE">加工（QL脚本）</option>
        </select>
        <textarea v-if="n.sourceType === 'CONST' || n.sourceType === 'DERIVE'" v-model="n.sourceName" class="card-input cc-json" rows="2" :placeholder="n.sourceType === 'DERIVE' ? '返回 a * b;' : '订单数据 JSON'"></textarea>
        <select v-else-if="n.sourceType === 'CONTEXT'" v-model="n.sourceName" class="card-input">
          <option value="">请选择上游变量</option>
          <option v-for="v in sourceNameOptions(n)" :key="v" :value="v">{{ v }}</option>
        </select>
        <input v-else v-model="n.sourceName" class="card-input"
               :placeholder="n.sourceType === 'SCRIPT' ? '脚本ID' : '中文函数名'" />
      </div>
      <div class="cc-map-row" v-for="(val, key) in n.paramMapping" :key="key">
        <input :value="key" class="card-input" @change="renameMap(n, key, $event.target.value)" placeholder="参数名" />
        <span>←</span>
        <input v-model="n.paramMapping[key]" class="card-input" placeholder="${A}" />
        <button class="card-btn danger" @click="delete n.paramMapping[key]">✕</button>
      </div>
      <button class="btn-secondary sm" @click="addParam(n)">＋ 参数映射</button>
      <span v-if="depsOf(n).length" class="cc-deps">依赖 {{ depsOf(n).join('、') }}</span>
    </div>

    <!-- 比对节点 -->
    <div class="cc-section-head">② 比对节点</div>
    <div class="cc-compare">
      <div class="cc-node-row">
        <label>关联字段<input v-model="rule.compareNode.keyField" class="card-input" placeholder="订单号" /></label>
        <label>数据源Key<input :value="(rule.compareNode.sourceKeys||[]).join(',')" class="card-input" placeholder="A,B"
                             @change="rule.compareNode.sourceKeys = $event.target.value.split(',').map(s=>s.trim()).filter(Boolean)" /></label>
      </div>
      <div v-for="(f, i) in rule.compareNode.fields" :key="i" class="cc-field-row">
        <input v-model="f.field" class="card-input" placeholder="字段" />
        <input v-model="f.label" class="card-input" placeholder="中文名" />
        <select v-model="f.strategy" class="card-input">
          <option value="equal">相等</option>
          <option value="notEqual">不等</option>
          <option value="contains">包含</option>
          <option value="gt">大于</option>
          <option value="gte">大于等于</option>
          <option value="lt">小于</option>
          <option value="lte">小于等于</option>
          <option value="numericTolerance">数值容差</option>
        </select>
        <input v-model.number="f.tolerance" type="number" class="card-input" placeholder="容差" />
        <button class="card-btn danger" @click="rule.compareNode.fields.splice(i,1)">✕</button>
      </div>
      <button class="btn-secondary sm" @click="rule.compareNode.fields.push({field:'',label:'',strategy:'equal',tolerance:0})">＋ 比对字段</button>
    </div>

    <!-- 运行 -->
    <div class="cc-section-head">③ 运行上下文</div>
    <textarea v-model="card.contextJson" class="card-input cc-json" rows="2" placeholder='{"user":"张三"}'></textarea>
    <div class="cc-run-row">
      <button class="btn-primary sm" :disabled="running" @click="run">{{ running ? '比对中…' : '▶ 运行比对' }}</button>
      <label class="cc-only-diff"><input type="checkbox" v-model="onlyDiff" /> 只看差异</label>
    </div>

    <template v-if="result">
      <div class="cc-summary" :class="result.inconsistentRows ? 'bad' : 'good'">
        {{ result.totalRows }} 行 · 一致 {{ result.consistentRows }} · 不一致 {{ result.inconsistentRows }} · {{ result.elapsedMillis }}ms
      </div>
      <table class="cc-table">
        <thead><tr><th>Key</th><th v-for="f in rule.compareNode.fields" :key="f.field">{{ f.label || f.field }}</th><th>结论</th></tr></thead>
        <tbody>
          <tr v-for="(row, i) in shownRows" :key="i" :class="{bad:!row.consistent}">
            <td>{{ row.key }}</td>
            <td v-for="f in rule.compareNode.fields" :key="f.field">
              <div v-for="(vals, sk) in row.sourceValues" :key="sk" class="cc-cell-val">
                <span class="cc-src">{{ sk }}</span>{{ fmt(vals[f.field]) }}
                <span :class="row.cells[f.field] && !row.cells[f.field].consistent ? 'bad-txt' : 'good-txt'">
                  {{ row.cells[f.field] ? (row.cells[f.field].consistent ? '✓' : '✗') : '' }}
                </span>
              </div>
            </td>
            <td><span :class="row.consistent ? 'badge-good' : 'badge-bad'">{{ row.consistent ? '一致' : '不一致' }}</span></td>
          </tr>
        </tbody>
      </table>
      <p v-if="onlyDiff && !shownRows.length" class="cc-empty">没有差异 🎉</p>
    </template>
  </div>
</template>

<script setup>
import { computed, ref } from "vue";
import { openCompareLibrary } from "../store.js";

const props = defineProps({
  card: { type: Object, required: true },
  allCards: { type: Array, default: () => [] }
});

// 卡片内的规则配置（持久化到 meta.cards）
if (!props.card.compareRule) {
  props.card.compareRule = {
    name: props.card.name || "比对",
    fetchNodes: [],
    compareNode: { keyField: "", sourceKeys: [], fields: [] }
  };
}
if (props.card.contextJson == null) props.card.contextJson = "{}";

const rule = computed(() => props.card.compareRule);
const result = ref(null);
const running = ref(false);
const onlyDiff = ref(false);
const dragIdx = ref(null);
const dropNode = (to) => {
  if (dragIdx.value === null || dragIdx.value === to) return;
  const nodes = rule.value.fetchNodes;
  if (dragIdx.value < 0 || to < 0 || dragIdx.value >= nodes.length || to >= nodes.length) return;
  const [moved] = nodes.splice(dragIdx.value, 1);
  nodes.splice(to, 0, moved);
  dragIdx.value = null;
};

const shownRows = computed(() => {
  const rows = result.value?.rows || [];
  return onlyDiff.value ? rows.filter(r => !r.consistent) : rows;
});

/** 本卡之前的取数卡产出的变量名（真正的数据联动来源） */
const upstreamVars = computed(() => {
  const idx = props.allCards.findIndex(c => c.id === props.card.id);
  const before = idx < 0 ? props.allCards : props.allCards.slice(0, idx);
  const names = [];
  for (const c of before) {
    if (c.varName && ["call", "prepcall", "calc"].includes(c.type)) names.push(c.varName);
    if (c.type === "decisiontable" && c.resultVar) names.push(c.resultVar);
  }
  return [...new Set(names)];
});

const dataPool = computed(() => {
  const out = [];
  try {
    for (const k of Object.keys(JSON.parse(props.card.contextJson || "{}") || {})) {
      out.push({ expr: "${" + k + "}", hint: "运行上下文字段 " + k });
    }
  } catch (e) { /* ignore */ }
  for (const v of upstreamVars.value) out.push({ expr: "${" + v + "}", hint: "上游取数卡结果 " + v });
  for (const n of rule.value.fetchNodes) {
    if (n.resultKey) out.push({ expr: "${" + n.resultKey + "}", hint: (n.name || n.resultKey) + " 的取数结果" });
  }
  const seen = new Set();
  return out.filter(x => seen.has(x.expr) ? false : (seen.add(x.expr), true));
});

/** 上游结果节点变量名下拉：保留当前值（即使上游卡被改名也能看出失联） */
const sourceNameOptions = (n) => {
  const opts = [...upstreamVars.value];
  if (n.sourceName && !opts.includes(n.sourceName)) opts.unshift(n.sourceName);
  return opts;
};

const copyExpr = async (expr) => {
  try { await navigator.clipboard.writeText(expr); } catch (e) { /* 剪贴板不可用时忽略 */ }
};

const depsOf = (node) => {
  const names = new Set();
  for (const v of Object.values(node.paramMapping || {})) {
    for (const m of String(v || '').matchAll(/\$\{([^}]+)\}/g)) {
      const head = m[1].trim().split(/[\[.]/)[0];
      const p = rule.value.fetchNodes.find(n => n.resultKey === head && n.id !== node.id);
      if (p) names.add(p.name || p.resultKey);
    }
  }
  return [...names];
};

const addFetch = () => {
  const i = rule.value.fetchNodes.length;
  rule.value.fetchNodes.push({
    id: "cf_" + Date.now() + "_" + i, name: "取数" + (i + 1), sourceType: "CONST",
    sourceName: "[]", resultKey: "SRC" + (i + 1), paramMapping: {}, dependsOn: []
  });
};
const addParam = (n) => {
  if (!n.paramMapping) n.paramMapping = {};
  n.paramMapping["参数" + (Object.keys(n.paramMapping).length + 1)] = "";
};
const renameMap = (n, oldKey, newKey) => {
  if (!newKey || newKey === oldKey) return;
  const v = n.paramMapping[oldKey];
  delete n.paramMapping[oldKey];
  n.paramMapping[newKey] = v;
};

const run = async () => {
  let context;
  try { context = JSON.parse(props.card.contextJson || "{}"); }
  catch (e) { alert("运行上下文不是合法 JSON"); return; }
  running.value = true;
  result.value = null;
  try {
    const resp = await fetch("/api/compare-rules/run", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ rule: rule.value, context })
    });
    const data = await resp.json();
    if (!resp.ok || data.error) { alert(data.error || "比对失败"); return; }
    result.value = data;
  } catch (e) {
    alert("比对执行失败：" + e.message);
  } finally {
    running.value = false;
  }
};

const fmt = (v) => (v === null || v === undefined) ? "—" : (typeof v === "object" ? JSON.stringify(v) : String(v));
</script>

<style scoped>
.cc-root { width: 100%; font-size: 11px; }
.cc-pool { display: flex; flex-wrap: wrap; gap: 4px; align-items: center; margin-bottom: 6px; }
.cc-pool-title { font-size: 10px; color: var(--ink-500); }
.cc-pool-chip { font-family: var(--font-mono); font-size: 10px; background: var(--accent-soft); border: 1px solid var(--accent-border); border-radius: 4px; padding: 1px 5px; cursor: pointer; }
.cc-section-head { display: flex; align-items: center; gap: 6px; font-weight: 700; color: var(--ink-700); margin: 7px 0 4px; }
.cc-section-head button { margin-left: auto; }
.cc-node, .cc-compare { border: 1px solid var(--ink-200); border-radius: 7px; padding: 6px; margin-bottom: 5px; background: var(--ink-50); }
.cc-node-head { display: flex; gap: 5px; align-items: center; margin-bottom: 5px; }
.cc-drag { cursor: grab; color: var(--ink-400); user-select: none; }
.cc-node-title { color: var(--accent); font-weight: 700; white-space: nowrap; }
.cc-node-head .card-input { width: 90px; }
.cc-node-head .card-btn { margin-left: auto; }
.cc-node-row { display: flex; gap: 5px; flex-wrap: wrap; margin-bottom: 5px; }
.cc-node-row label { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 90px; font-size: 10px; color: var(--ink-500); }
.cc-node-row .card-input { min-width: 80px; }
.cc-json { font-family: var(--font-mono); width: 100%; resize: vertical; }
.cc-map-row { display: flex; gap: 4px; align-items: center; margin-bottom: 4px; }
.cc-map-row .card-input { flex: 1; min-width: 0; }
.cc-deps { font-size: 9.5px; color: var(--accent); margin-left: 6px; }
.cc-field-row { display: flex; gap: 4px; margin-bottom: 4px; }
.cc-field-row .card-input { min-width: 0; flex: 1; }
.cc-run-row { display: flex; align-items: center; gap: 10px; margin: 6px 0; }
.cc-only-diff { display: flex; gap: 4px; align-items: center; font-size: 10.5px; }
.cc-only-diff input { width: auto; }
.cc-summary { padding: 5px 8px; border-radius: 6px; margin-bottom: 6px; }
.cc-summary.good { background: var(--ok-soft); color: var(--ok-ink); }
.cc-summary.bad { background: var(--bad-soft); color: var(--bad-ink); }
.cc-table { width: 100%; border-collapse: collapse; font-size: 10px; }
.cc-table th, .cc-table td { text-align: left; padding: 3px 4px; border-bottom: 1px solid var(--ink-200); vertical-align: top; }
.cc-table tr.bad { background: var(--bad-soft); }
.cc-cell-val { white-space: nowrap; }
.cc-src { display: inline-block; font-size: 9px; color: var(--ink-400); background: var(--ink-100); border-radius: 3px; padding: 0 3px; margin-right: 3px; }
.bad-txt { color: var(--bad-text); font-weight: 700; }
.good-txt { color: var(--ok); font-weight: 700; }
.badge-good, .badge-bad { font-size: 9px; font-weight: 700; padding: 1px 5px; border-radius: 9px; }
.badge-good { background: var(--ok-soft); color: var(--ok-ink); }
.badge-bad { background: var(--bad-soft); color: var(--bad-ink); }
.cc-empty { color: var(--ink-400); }
</style>
