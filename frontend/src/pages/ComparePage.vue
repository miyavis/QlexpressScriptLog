<template>
  <div class="compare-page">
    <!-- 顶部：规则名 / 说明 / 操作 -->
    <header class="compare-top">
      <div class="compare-top-left">
        <label class="cmp-name-field">规则名称<input v-model="draft.name" class="card-input" placeholder="例如：双源订单对账" /></label>
        <label class="cmp-desc-field">说明<input v-model="draft.description" class="card-input" placeholder="这个比对规则做什么" /></label>
      </div>
      <div class="compare-top-actions">
        <button class="btn-secondary" @click="newCompareRule()">＋ 新建</button>
        <button class="btn-primary" :disabled="!draft" @click="saveCompareRule()">💾 保存</button>
        <button class="btn-secondary danger" :disabled="!selectedCompareRuleId" @click="confirmDelete">🗑 删除</button>
        <button class="btn-primary run" :disabled="!selectedCompareRuleId || isRunningCompare" @click="runCompare()">
          {{ isRunningCompare ? '比对中…' : '▶ 运行比对' }}
        </button>
      </div>
    </header>

    <div class="compare-body">
      <!-- 左侧：规则列表 -->
      <aside class="cmp-rule-list">
        <div class="cmp-list-head">比对规则 <span class="nav-badge">{{ compareRules.length }}</span></div>
        <div v-for="r in compareRules" :key="r.id" class="cmp-rule-item"
             :class="{ active: r.id === selectedCompareRuleId }" @click="selectCompareRule(r.id)">
          <div class="cmp-rule-name">{{ r.name }}</div>
          <div class="cmp-rule-meta">{{ (r.fetchNodes || []).length }} 个取数 · {{ r.compareNode?.fields?.length || 0 }} 个比对字段</div>
        </div>
        <p v-if="!compareRules.length" class="cmp-empty">暂无规则，点「＋ 新建」</p>
      </aside>

      <!-- 中间：画布配置 -->
      <section class="cmp-canvas" v-if="draft">
        <div class="cmp-section-head">
          <span>① 取数节点</span>
          <button class="btn-secondary sm" @click="addFetchNode()">＋ 添加取数节点</button>
        </div>
        <p class="cmp-hint">每个节点取一份数据（脚本返回值 / 内置数据 / 函数调用），结果存入「结果 Key」供比对节点引用。拖动节点可调整顺序，点数据池项可复制引用表达式。</p>

        <div class="cmp-data-pool">
          <div class="cmp-pool-head">📦 可用数据池（点击复制引用）</div>
          <div class="cmp-pool-items">
            <button v-for="p in dataPool" :key="p.expr" class="cmp-pool-chip" :title="p.hint" @click="copyExpr(p.expr)">
              {{ p.expr }}
            </button>
          </div>
        </div>

        <div v-for="(node, idx) in draft.fetchNodes" :key="node.id" class="cmp-node-card"
             draggable="true" @dragstart="dragIdx = idx" @dragover.prevent @drop="dropNode(idx)">
          <div class="cmp-node-head">
            <span class="cmp-drag-handle" title="拖动排序">⠿</span>
            <span class="cmp-node-title">取数 {{ idx + 1 }}</span>
            <input v-model="node.name" class="card-input cmp-node-name" placeholder="节点名称" />
            <button class="card-btn danger" @click="removeFetchNode(node.id)" title="删除节点">✕</button>
          </div>
          <div class="cmp-node-grid">
            <label>取数方式
              <select v-model="node.sourceType" class="card-input">
                <option value="CONST">内置数据（JSON）</option>
                <option value="CONTEXT">上游结果（变量名）</option>
                <option value="SCRIPT">执行脚本</option>
                <option value="FUNCTION">调用函数</option>
                <option value="DERIVE">加工（QL脚本）</option>
              </select>
            </label>
            <label>结果 Key
              <input v-model="node.resultKey" class="card-input" placeholder="如 A / B" />
            </label>
            <label class="cmp-wide" v-if="node.sourceType === 'SCRIPT'">选择脚本
              <select v-model="node.sourceName" class="card-input">
                <option value="">请选择脚本</option>
                <option v-for="s in compareScriptSources" :key="s.id" :value="s.id">{{ s.name }}</option>
              </select>
            </label>
            <label class="cmp-wide" v-else-if="node.sourceType === 'FUNCTION'">函数名
              <input v-model="node.sourceName" class="card-input" placeholder="如 查询用户等级" />
            </label>
            <label class="cmp-wide" v-else-if="node.sourceType === 'CONTEXT'">上游变量名
              <input v-model="node.sourceName" class="card-input" placeholder="如 积分 / 等级" />
            </label>
            <label class="cmp-wide" v-else-if="node.sourceType === 'DERIVE'">加工脚本（用 ${A}/${B} 引用上游结果）
              <textarea v-model="node.sourceName" class="card-input cmp-json-area" rows="3" placeholder='返回 a * b;'></textarea>
            </label>
            <label class="cmp-wide" v-else>数据（JSON 数组，每行一条记录）
              <textarea v-model="node.sourceName" class="card-input cmp-json-area" rows="3" placeholder='[{"订单号":"O001","金额":100}]'></textarea>
            </label>
          </div>
          <div class="cmp-mapping">
            <div class="cmp-mapping-head">参数映射（从运行上下文取值，支持 ${路径}）
              <button class="btn-secondary sm" @click="addParamMapping(node)">＋ 参数</button>
            </div>
            <div v-if="nodeDeps(node).length" class="cmp-node-deps">依赖：{{ nodeDeps(node).join('、') }}（由参数引用自动推断，按依赖顺序执行）</div>
            <div v-for="(val, key) in node.paramMapping" :key="key" class="cmp-mapping-row">
              <input :value="key" class="card-input" placeholder="参数名" @change="renameParamMapping(node, key, $event.target.value)" />
              <span class="cmp-arrow">←</span>
              <input v-model="node.paramMapping[key]" class="card-input" placeholder="${user}" />
              <button class="card-btn danger" @click="removeParamMapping(node, key)">✕</button>
            </div>
          </div>
        </div>

        <div class="cmp-section-head cmp-second">
          <span>② 比对节点</span>
        </div>
        <div class="cmp-compare-node">
          <div class="cmp-node-grid">
            <label>关联字段（按此字段对齐各数据源）
              <input v-model="draft.compareNode.keyField" class="card-input" placeholder="如 订单号" />
            </label>
            <label>参与数据源（结果 Key，逗号分隔）
              <input :value="(draft.compareNode.sourceKeys || []).join(', ')"
                     class="card-input" placeholder="A, B"
                     @change="draft.compareNode.sourceKeys = $event.target.value.split(',').map(s => s.trim()).filter(Boolean)" />
            </label>
          </div>
          <div class="cmp-fields-head">字段比对
            <button class="btn-secondary sm" @click="addFieldCompare()">＋ 比对字段</button>
          </div>
          <table class="cmp-fields-table">
            <thead><tr><th>字段名</th><th>中文名</th><th>比对策略</th><th>容差</th><th></th></tr></thead>
            <tbody>
              <tr v-for="(f, i) in draft.compareNode.fields" :key="i">
                <td><input v-model="f.field" class="card-input" placeholder="如 金额" /></td>
                <td><input v-model="f.label" class="card-input" placeholder="如 金额" /></td>
                <td>
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
                </td>
                <td><input v-model.number="f.tolerance" type="number" class="card-input" /></td>
                <td><button class="card-btn danger" @click="removeFieldCompare(i)">✕</button></td>
              </tr>
            </tbody>
          </table>
          <p v-if="!draft.compareNode.fields.length" class="cmp-empty">点「＋ 比对字段」添加要比对的字段</p>
        </div>
      </section>

      <!-- 右侧：运行与结果 -->
      <section class="cmp-result-panel">
        <div class="cmp-section-head">③ 运行上下文（JSON）</div>
        <textarea v-model="compareContextJson" class="card-input cmp-context-area" rows="4" spellcheck="false"></textarea>
        <button class="btn-primary run" :disabled="!selectedCompareRuleId || isRunningCompare" @click="runCompare()">
          {{ isRunningCompare ? '比对中…' : '▶ 运行比对' }}
        </button>

        <div class="cmp-date-range">
          <label>起 <input type="datetime-local" v-model="compareFrom" @change="loadCompareRecords()" /></label>
          <label>止 <input type="datetime-local" v-model="compareTo" @change="loadCompareRecords()" /></label>
          <button class="btn-secondary sm" @click="compareFrom=''; compareTo=''; loadCompareRecords()">清除</button>
        </div>
        <div class="cmp-history" v-if="compareRecords.length">
          <select class="card-input" :value="''" @change="onHistoryChange($event.target.value)">
            <option value="">📜 历史记录（点选回看）</option>
            <option v-for="rec in compareRecords" :key="rec.id" :value="rec.id">
              {{ new Date(rec.savedAt).toLocaleString() }} · {{ rec.summary }}
            </option>
          </select>
          <button v-if="selectedHistoryRecord" class="btn-secondary sm" @click="rerunCompareRecord(selectedHistoryRecord)">
            ↻ 重跑此记录
          </button>
        </div>

        <div class="cmp-schedules">
          <div class="cmp-section-head">⏰ 定时任务（无需 cron）</div>
          <div class="cmp-sched-add">
            <select v-model="scheduleInterval" class="card-input">
              <option :value="5">每 5 分钟</option>
              <option :value="15">每 15 分钟</option>
              <option :value="60">每小时</option>
              <option :value="1440">每天</option>
            </select>
            <button class="btn-secondary sm" @click="addCompareSchedule(Number(scheduleInterval))">＋ 添加</button>
          </div>
          <div v-for="s in compareSchedules" :key="s.id" class="cmp-sched-row">
            <span class="cmp-sched-name" :class="{ off: !s.enabled }">{{ s.name }}</span>
            <span class="cmp-sched-time" v-if="s.lastRunAt">上次 {{ new Date(s.lastRunAt).toLocaleString() }}</span>
            <button class="card-btn" @click="runCompareScheduleNow(s.id)" title="立即运行一次">▶</button>
            <button class="card-btn" @click="toggleCompareSchedule(s)">{{ s.enabled ? '停用' : '启用' }}</button>
            <button class="card-btn danger" @click="deleteCompareSchedule(s.id)">✕</button>
          </div>
          <p v-if="!compareSchedules.length" class="cmp-empty">暂无定时任务</p>
        </div>

        <template v-if="compareResult">
          <label class="cmp-only-diff"><input type="checkbox" v-model="onlyDifferences" /> 只看差异</label>
          <div class="cmp-result-summary" :class="compareResult.inconsistentRows ? 'bad' : 'good'">
            <div>总行数 <b>{{ compareResult.totalRows }}</b></div>
            <div>一致 <b>{{ compareResult.consistentRows }}</b></div>
            <div>不一致 <b>{{ compareResult.inconsistentRows }}</b></div>
            <div>不一致字段 <b>{{ compareResult.inconsistentCells }}</b></div>
            <div>耗时 <b>{{ compareResult.elapsedMillis }}ms</b></div>
          </div>
          <div class="cmp-result-table-wrap">
            <table class="cmp-result-table">
              <thead>
                <tr>
                  <th>Key</th>
                  <th v-for="f in draft.compareNode.fields" :key="f.field">{{ f.label || f.field }}</th>
                  <th>结论</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, i) in visibleRows" :key="i" :class="{ bad: !row.consistent }">
                  <td class="cmp-key-cell">{{ row.key }}</td>
                  <td v-for="f in draft.compareNode.fields" :key="f.field" class="cmp-cell">
                    <div v-for="(vals, sk) in row.sourceValues" :key="sk" class="cmp-cell-val">
                      <span class="cmp-src-tag">{{ sk }}</span>{{ fmt(vals[f.field]) }}
                      <span :class="row.cells[f.field] && !row.cells[f.field].consistent ? 'bad-txt' : 'good-txt'">
                        {{ row.cells[f.field] ? (row.cells[f.field].consistent ? '✓' : '✗') : '' }}
                      </span>
                    </div>
                  </td>
                  <td><span :class="row.consistent ? 'badge-good' : 'badge-bad'">{{ row.consistent ? '一致' : '不一致' }}</span></td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="cmp-drill" v-if="Object.keys(compareResult.scriptTraceIds || {}).length">
            <div class="cmp-drill-title">取数执行日志（点开下钻）</div>
            <a v-for="(tid, sid) in compareResult.scriptTraceIds" :key="sid"
               class="cmp-drill-link" :href="'/?scriptId=' + encodeURIComponent(sid) + '&traceId=' + encodeURIComponent(tid)"
               target="_blank" rel="noopener">
              {{ sid }} → {{ tid }}
            </a>
          </div>
          <p v-if="onlyDifferences && !visibleRows.length" class="cmp-empty">本次比对没有差异 🎉</p>
        </template>
        <p v-else class="cmp-empty">配置好规则后点「运行比对」，结果按 Key 对齐展示</p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from "vue";
import {
  compareRules, selectedCompareRuleId, compareDraft, compareResult, isRunningCompare,
  compareContextJson, compareScriptSources, compareRecords, compareFrom, compareTo, onlyDifferences,
  showCompareRecord, rerunCompareRecord, loadCompareRules, loadCompareRecords, selectCompareRule,
  compareSchedules, addCompareSchedule, deleteCompareSchedule, toggleCompareSchedule, runCompareScheduleNow,
  newCompareRule, saveCompareRule, deleteCompareRule, runCompare,
  addFetchNode, removeFetchNode, moveFetchNode, addFieldCompare, removeFieldCompare,
  addParamMapping, removeParamMapping, renameParamMapping, notify
} from "../store.js";

const draft = computed(() => compareDraft.value);
const dragIdx = ref(null);
const selectedHistoryRecord = ref(null);
const scheduleInterval = ref(60);
const onHistoryChange = (id) => {
  const rec = compareRecords.value.find((r) => r.id === id) || null;
  selectedHistoryRecord.value = rec;
  if (rec) showCompareRecord(rec);
};
const dropNode = (toIdx) => {
  if (dragIdx.value !== null) moveFetchNode(dragIdx.value, toIdx);
  dragIdx.value = null;
};

/** 数据池：运行上下文顶层字段 + 各取数节点结果 Key */
const dataPool = computed(() => {
  const out = [];
  try {
    const ctx = JSON.parse(compareContextJson.value || "{}");
    for (const k of Object.keys(ctx || {})) out.push({ expr: "${" + k + "}", hint: "运行上下文字段 " + k });
  } catch (e) { /* 上下文尚未填好 */ }
  for (const n of draft.value?.fetchNodes || []) {
    if (n.resultKey) out.push({ expr: "${" + n.resultKey + "}", hint: n.name + " 的取数结果" });
  }
  return out;
});

/** 从 paramMapping 的 ${...} 推断依赖的取数节点名 */
const nodeDeps = (node) => {
  const keys = new Set();
  for (const v of Object.values(node.paramMapping || {})) {
    const m = String(v || '').matchAll(/\$\{([^}]+)\}/g);
    for (const x of m) {
      const head = x[1].trim().split(/[\[.]/)[0];
      const producer = (draft.value?.fetchNodes || []).find(n => n.resultKey === head && n.id !== node.id);
      if (producer) keys.add(producer.name || producer.resultKey);
    }
  }
  for (const id of node.dependsOn || []) {
    const producer = (draft.value?.fetchNodes || []).find(n => n.id === id && n.id !== node.id);
    if (producer) keys.add(producer.name || producer.resultKey);
  }
  return [...keys];
};

const copyExpr = async (expr) => {
  try {
    await navigator.clipboard.writeText(expr);
    notify("已复制 " + expr + "，粘贴到参数映射即可引用", "success");
  } catch (e) {
    notify("复制失败，请手动输入 " + expr, "warning");
  }
};
const visibleRows = computed(() => {
  const rows = compareResult.value?.rows || [];
  return onlyDifferences.value ? rows.filter((r) => !r.consistent) : rows;
});

const confirmDelete = () => {
  if (selectedCompareRuleId.value && confirm("确定删除这条比对规则？")) {
    deleteCompareRule(selectedCompareRuleId.value);
  }
};

const fmt = (v) => (v === null || v === undefined) ? "—" : (typeof v === "object" ? JSON.stringify(v) : String(v));
</script>
