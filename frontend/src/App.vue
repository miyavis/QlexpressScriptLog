<template>
  <div class="app">
    <!-- 布局壳：左侧菜单 + 右侧内容区（页面组件见 pages/） -->
    <aside class="side-nav">
      <div class="brand">
        <div class="brand-icon">🎴</div>
        <div>
          <div class="brand-name">规则执行平台</div>
          <div class="brand-sub">QLExpress4 · 中文脚本</div>
        </div>
      </div>
      <nav class="nav-menu">
        <button :class="{ active: view === 'scripts' }" @click="goView('scripts')"><span class="nav-ico">📜</span>脚本管理<span class="nav-badge">{{ scripts.length }}</span></button>
        <button :class="{ active: view === 'executions' }" @click="goView('executions')"><span class="nav-ico">📊</span>执行记录<span class="nav-badge">{{ executions.length }}</span></button>
        <button :class="{ active: view === 'help' }" @click="goView('help')"><span class="nav-ico">❓</span>使用帮助</button>
      </nav>
      <div class="nav-foot">JDK 8 · Spring Boot 2.7.18<br />Vite 4 · Vue 3.4</div>
    </aside>
    <div class="app-body">
      <main class="app-main">
        <ScriptsPage v-if="view === 'scripts'" />
        <ExecutionsPage v-else-if="view === 'executions'" />
        <HelpPage v-else />
      </main>
      <footer class="app-footer">
        后端 Spring Boot 2.7.18 + QLExpress 4.1.3 (JDK 8) · 前端 Vite 4 + Vue 3.4 · traceId 贯穿，实现脚本与日志一一对应
      </footer>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from "vue";
import { view, goView, scripts, executions, loadScripts, loadExecutions, loadFunctionRegistry } from "./store.js";
import ScriptsPage from "./pages/ScriptsPage.vue";
import ExecutionsPage from "./pages/ExecutionsPage.vue";
import HelpPage from "./pages/HelpPage.vue";

// 页面初始化：壳挂载后统一拉数据（store 里不再有生命周期）
onMounted(() => {
  loadScripts();
  loadExecutions();
  loadFunctionRegistry();
});
</script>

<style>
/* ---------- 布局：左侧菜单 + 内容区 ---------- */
.app { display: flex; min-height: 100vh; }
.side-nav {
  width: 220px; flex-shrink: 0; background: #111827; color: #e5e7eb;
  display: flex; flex-direction: column; padding: 18px 12px;
  position: sticky; top: 0; height: 100vh; box-sizing: border-box;
}
.brand { display: flex; gap: 10px; align-items: center; padding: 4px 8px 16px; border-bottom: 1px solid #1f2937; margin-bottom: 14px; }
.brand-icon { font-size: 26px; }
.brand-name { font-size: 15px; font-weight: 700; color: #fff; letter-spacing: 0.5px; }
.brand-sub { font-size: 11px; color: #9ca3af; margin-top: 2px; }
.nav-menu { display: flex; flex-direction: column; gap: 4px; }
.nav-menu button {
  display: flex; align-items: center; gap: 10px; padding: 10px 12px; border-radius: 8px;
  background: transparent; color: #9ca3af; font-weight: 600; font-size: 14px; text-align: left;
  transition: background 0.15s, color 0.15s;
}
.nav-menu button:hover { background: #1f2937; color: #e5e7eb; }
.nav-menu button.active { background: #2563eb; color: #fff; }
.nav-ico { font-size: 16px; }
.nav-badge { margin-left: auto; font-size: 11px; background: #374151; color: #d1d5db; border-radius: 10px; padding: 1px 8px; font-weight: 600; }
.nav-menu button.active .nav-badge { background: rgba(255,255,255,0.2); color: #fff; }
.nav-foot { margin-top: auto; font-size: 11px; color: #6b7280; line-height: 1.7; padding: 0 8px; }
.app-body { flex: 1; display: flex; flex-direction: column; min-width: 0; max-width: 1500px; }
.app-main { display: flex; gap: 16px; padding: 18px 20px; flex: 1; }

/* ---------- 帮助页 ---------- */
.help-page { flex-direction: column; gap: 14px; }
.help-card {
  border: 1px solid #e5e7eb; border-radius: 10px; background: #fff; padding: 16px 20px;
}
.help-card h2 { margin-bottom: 6px; }
.help-table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 13px; }
.help-table th, .help-table td { border: 1px solid #e5e7eb; padding: 7px 12px; text-align: left; }
.help-table th { background: #f9fafb; font-size: 12px; color: #6b7280; }
.help-table .fn-name { font-weight: 600; white-space: nowrap; }
.help-card .ul { padding-left: 18px; margin-top: 6px; }
.page { display: flex; gap: 16px; width: 100%; }
.scripts-page { flex-direction: column; gap: 14px; }

/* ---------- 顶部脚本列表条 ---------- */
.script-strip { border: 1px solid #e5e7eb; border-radius: 10px; background: #fff; padding: 14px 16px; }
.strip-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.strip-head h2 { margin: 0; }
.strip-head .toolbar { margin-bottom: 0; }
.script-list.horiz { list-style: none; display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 10px; }
.script-list.horiz li {
  border: 1px solid #e5e7eb; border-radius: 8px; padding: 10px 12px; cursor: pointer;
  display: flex; justify-content: space-between; align-items: center; gap: 8px;
  transition: background 0.15s, border-color 0.15s;
}
.script-list.horiz li:hover { background: #f9fafb; border-color: #c7d2fe; }
.script-list.horiz li.selected { background: #eef4ff; border-color: #2563eb; border-left: 3px solid #2563eb; }
.script-item-info { min-width: 0; }
.script-item-info .script-item-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.btn-delete.sm { padding: 4px 8px; font-size: 11px; border-radius: 6px; flex: none; }
.panel-left { flex: 0 0 340px; }
.panel-right { flex: 1; min-width: 0; }

/* ---------- 通用 ---------- */
h2 { font-size: 16px; margin-bottom: 10px; color: #111827; }
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; align-items: center; }
.btn-primary { background: #2563eb; color: #fff; padding: 7px 14px; border-radius: 6px; font-weight: 600; font-size: 14px; }
.btn-primary:hover { background: #1d4ed8; }
.btn-success { background: #059669; color: #fff; padding: 7px 14px; border-radius: 6px; font-weight: 600; font-size: 14px; }
.btn-success:hover { background: #047857; }
.btn-delete { background: #dc2626; color: #fff; padding: 6px 12px; border-radius: 6px; font-size: 13px; }
.btn-delete:hover { background: #b91c1c; }
.btn-secondary { background: #e5e7eb; color: #374151; padding: 7px 14px; border-radius: 6px; font-weight: 600; font-size: 14px; }
.btn-secondary:hover { background: #d1d5db; }
.empty { color: #9ca3af; font-size: 14px; padding: 20px 0; text-align: center; }
.full { width: 100%; }
.hint { font-size: 12px; color: #6b7280; margin-top: 4px; }

/* ---------- 脚本列表 ---------- */
.script-list { list-style: none; border: 1px solid #e5e7eb; border-radius: 8px; overflow: hidden; }
.script-list li {
  border-bottom: 1px solid #e5e7eb; padding: 12px 14px; cursor: pointer; display: flex; justify-content: space-between; align-items: center;
  transition: background 0.15s;
}
.script-list li:hover { background: #f9fafb; }
.script-list li.selected { background: #dbeafe; border-left: 3px solid #2563eb; }
.script-item-name { font-weight: 600; color: #111827; }
.script-item-id { font-size: 12px; color: #6b7280; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; }

/* ---------- 编辑器 ---------- */
.editor-card { border: 1px solid #e5e7eb; border-radius: 8px; padding: 16px; background: #fff; }
.form-row { margin-bottom: 12px; }
.form-row.full { width: 100%; }
.form-row label { display: block; font-size: 13px; color: #374151; margin-bottom: 6px; font-weight: 600; }
input { width: 100%; padding: 8px 10px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; }
input:readonly { background: #f3f4f6; color: #6b7280; }
.code-area { width: 100%; min-height: 220px; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; font-size: 13px; resize: vertical; }
.form-actions { display: flex; gap: 10px; margin-top: 8px; }

/* ---------- 执行记录列表 ---------- */
.execution-list { list-style: none; border: 1px solid #e5e7eb; border-radius: 8px; overflow: hidden; }
.execution-list li {
  border-bottom: 1px solid #e5e7eb; padding: 12px 14px; cursor: pointer; transition: background 0.15s;
}
.execution-list li:hover { background: #f9fafb; }
.execution-list li.selected { background: #dbeafe; border-left: 3px solid #2563eb; }
.exec-item-main { display: flex; justify-content: space-between; align-items: start; gap: 8px; }
.exec-item-trace { font-family: ui-monospace, monospace; font-size: 12px; color: #2563eb; font-weight: 700; word-break: break-all; }
.exec-item-info { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.script-id { font-weight: 600; color: #111827; font-size: 13px; }
.script-name { font-size: 12px; color: #6b7280; }
.exec-item-meta { display: flex; gap: 10px; font-size: 12px; color: #6b7280; white-space: nowrap; }
.badge { padding: 2px 8px; border-radius: 10px; font-size: 11px; font-weight: 700; }
.badge-ok { background: #dcfce7; color: #166534; }
.badge-fail { background: #fee2e2; color: #991b1b; }
.duration { font-family: ui-monospace, monospace; }
.exec-item-error { font-size: 12px; color: #dc2626; margin-top: 6px; word-break: break-all; }

/* ---------- 详情卡 ---------- */
.detail-card { border: 1px solid #e5e7eb; border-radius: 8px; padding: 16px; background: #fff; }
.card { margin-bottom: 16px; border: 1px solid #e5e7eb; border-radius: 8px; overflow: hidden; background: #fff; }
.card.full { width: 100%; }
.card-title { background: #f9fafb; padding: 10px 14px; border-bottom: 1px solid #e5e7eb; font-weight: 700; font-size: 14px; color: #111827; }
.card-body { padding: 14px; }

/* 概览 */
.overview { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-bottom: 16px; }
.metrics { display: flex; gap: 12px; }
.metric { flex: 1; background: #f9fafb; border: 1px solid #e5e7eb; border-radius: 8px; padding: 10px 12px; }
.metric .value { font-size: 15px; font-weight: 700; color: #111827; word-break: break-all; }
.metric .value.ok { color: #166534; }
.metric .value.fail { color: #991b1b; }
.metric .label { font-size: 12px; color: #6b7280; margin-top: 4px; }

.json-pre { background: #f3f4f6; border: 1px solid #e5e7eb; border-radius: 6px; padding: 10px 12px; font-family: ui-monospace, monospace; font-size: 12px; color: #1f2937; overflow: auto; white-space: pre-wrap; word-wrap: break-word; max-height: 260px; }
.json-pre.small { max-height: 140px; font-size: 11.5px; padding: 8px 10px; }
.code-pre { background: #1e1e1e; color: #d4d4d4; border-radius: 6px; padding: 12px 14px; font-family: ui-monospace, monospace; font-size: 13px; overflow: auto; white-space: pre-wrap; }

.result-box { padding: 12px 14px; }
.error-label { color: #991b1b; font-size: 13px; }

/* 调用树 */
.calls-tree { padding: 10px 14px; }
.call-node { border: 1px solid #e5e7eb; border-radius: 6px; margin-bottom: 8px; overflow: hidden; background: #fff; }
.call-header {
  display: flex; align-items: center; gap: 8px; padding: 8px 12px; cursor: pointer; background: #f9fafb;
  border-bottom: 1px solid #e5e7eb; user-select: none;
}
.call-header:hover { background: #f3f4f6; }
.arrow { font-size: 12px; color: #6b7280; min-width: 14px; }
.call-service { font-weight: 700; color: #111827; font-size: 13px; }
.call-method { color: #6b7280; font-size: 12px; font-family: ui-monospace, monospace; }
.call-badge { padding: 1px 6px; border-radius: 10px; font-size: 11px; font-weight: 700; margin-left: auto; }
.call-badge.ok { background: #dcfce7; color: #166534; }
.call-badge.fail { background: #fee2e2; color: #991b1b; }
.call-duration { font-family: ui-monospace, monospace; font-size: 11px; color: #9ca3af; margin-left: 8px; }
.call-body { padding: 10px 14px 14px; background: #fff; }
.call-section { margin-bottom: 10px; }
.section-label { font-size: 12px; font-weight: 700; color: #374151; margin-bottom: 4px; display: flex; gap: 6px; align-items: center; }
.call-section.error { color: #991b1b; }
.error-text { font-size: 12px; font-family: ui-monospace, monospace; white-space: pre-wrap; }

.steps-list { list-style: none; }
.step-item { display: flex; justify-content: space-between; gap: 8px; padding: 5px 0; font-size: 12.5px; border-bottom: 1px solid #f3f4f6; }
.step-item.small { font-size: 11.5px; padding: 3px 0; }
.step-name { font-weight: 600; color: #111827; }
.step-detail { color: #4b5563; white-space: pre-wrap; max-width: 420px; overflow: hidden; text-overflow: ellipsis; }
.step-time { font-family: ui-monospace, monospace; font-size: 11px; color: #9ca3af; white-space: nowrap; }

.children-calls { margin-top: 8px; padding-left: 16px; border-left: 2px solid #e5e7eb; }
.child-call { margin-bottom: 8px; padding: 6px 10px; background: #f9fafb; border-radius: 6px; border: 1px solid #e5e7eb; }
.child-header { display: flex; gap: 8px; margin-bottom: 4px; }
.child-service { font-weight: 700; color: #111827; font-size: 12px; }
.child-method { color: #6b7280; font-size: 11px; font-family: ui-monospace, monospace; }
.child-badge { padding: 1px 5px; border-radius: 10px; font-size: 10px; font-weight: 700; margin-left: auto; }
.child-badge.ok { background: #dcfce7; color: #166534; }
.child-badge.fail { background: #fee2e2; color: #991b1b; }
.child-duration { font-family: ui-monospace, monospace; font-size: 10px; color: #9ca3af; margin-left: 6px; }
.child-args, .child-result, .child-error { font-size: 11.5px; margin: 2px 0; color: #4b5563; }
.child-error { color: #991b1b; font-family: ui-monospace, monospace; }

.count { font-size: 12px; color: #6b7280; }

/* ---------- 脚本右侧执行日志 ---------- */
.log-card { border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; margin-top: 16px; }
.log-header { display: flex; justify-content: space-between; align-items: center; padding: 10px 14px; border-bottom: 1px solid #e5e7eb; background: #f9fafb; border-radius: 8px 8px 0 0; }
.log-header-btns { display: flex; gap: 6px; }
.overview .card-title { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.log-header h3 { font-size: 14px; color: #111827; margin: 0; }
.log-list { list-style: none; max-height: 420px; overflow-y: auto; }
.log-list li { border-bottom: 1px solid #f3f4f6; padding: 10px 14px; cursor: pointer; transition: background 0.15s; }
.log-list li:hover { background: #f9fafb; }
.log-list li.selected { background: #dbeafe; border-left: 3px solid #2563eb; }
.log-item-main { display: flex; justify-content: space-between; align-items: start; gap: 8px; margin-bottom: 4px; }
.log-item-trace { font-family: ui-monospace, monospace; font-size: 11.5px; color: #2563eb; font-weight: 700; word-break: break-all; }
.log-item-meta { display: flex; gap: 8px; font-size: 11.5px; color: #6b7280; white-space: nowrap; }
.log-item-error { font-size: 11.5px; color: #dc2626; margin-top: 4px; word-break: break-all; }
.log-detail { margin-top: 8px; border: 1px solid #e5e7eb; border-radius: 6px; background: #fafbfc; overflow: hidden; }
.call-node.compact { font-size: 12px; }

/* ---------- 行级日志 ---------- */
.line-log-list { display: flex; flex-direction: column; gap: 6px; }
.line-log-item { border: 1px solid #e5e7eb; border-radius: 6px; overflow: hidden; background: #fff; }
.line-log-header {
  display: flex; gap: 10px; padding: 6px 12px; background: #f9fafb; border-bottom: 1px solid #f3f4f6;
  align-items: center;
}
.line-no { font-family: ui-monospace, monospace; font-size: 11px; color: #6b7280; font-weight: 700; min-width: 44px; }
.line-code { font-family: ui-monospace, monospace; font-size: 11.5px; color: #374151; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.line-log-calls, .line-log-steps { padding: 4px 12px 6px; }
.line-log-result { padding: 4px 12px 8px; font-size: 11px; color: #6b7280; font-family: ui-monospace, monospace; }
.line-call { display: flex; gap: 8px; align-items: center; font-size: 11.5px; margin-bottom: 2px; }
.line-call-name { font-weight: 600; color: #111827; }
.line-call-badge { padding: 1px 5px; border-radius: 10px; font-size: 10px; font-weight: 700; }
.line-call-badge.ok { background: #dcfce7; color: #166534; }
.line-call-badge.fail { background: #fee2e2; color: #991b1b; }
.line-call-dur { font-family: ui-monospace, monospace; font-size: 10px; color: #9ca3af; margin-left: auto; }
.line-call-args, .line-call-result { font-size: 10.5px; color: #4b5563; margin-top: 1px; margin-left: 18px; }
.line-step { display: flex; gap: 6px; font-size: 11.5px; margin-bottom: 2px; }
.line-step-name { font-weight: 600; color: #111827; }
.line-step-detail { color: #4b5563; }
.line-step-time { font-family: ui-monospace, monospace; font-size: 10px; color: #9ca3af; margin-left: auto; }

/* ---------- 脚本右侧执行日志（左右并排布局） ---------- */
.editor-log-wrap { display: flex; gap: 12px; margin-top: 8px; }
.editor-side { flex: 1; min-width: 0; }
.log-side { flex: 0 0 420px; min-width: 300px; display: flex; flex-direction: column; }

.code-editor { position: relative; }
.code-gutter {
  position: absolute;
  left: 0; top: 0; bottom: 0;
  width: 34px;
  display: flex; flex-direction: column;
  font-family: ui-monospace, monospace;
  font-size: 12px;
  color: #9ca3af;
  user-select: none;
  pointer-events: none;
  border-right: 1px solid #e5e7eb;
}
.gutter-line {
  height: 22px;
  display: flex; align-items: center; justify-content: center;
  color: #9ca3af;
}
.gutter-line.active { color: #2563eb; font-weight: 700; background: #dbeafe; }
.gutter-line.highlight { background: #fef3c7; color: #92400e; font-weight: 700; }

.callHighlight { background: #fef3c7; border-left: 3px solid #d97706; }

.code-area {
  padding-left: 44px !important;
  resize: vertical !important;
  line-height: 22px;
}

.log-header { display: flex; justify-content: space-between; align-items: center; padding: 8px 12px; border-bottom: 1px solid #e5e7eb; background: #f9fafb; }
.log-header h3 { font-size: 14px; color: #111827; margin: 0; }
.btn-secondary.sm { padding: 4px 10px; font-size: 12px; }
.log-empty { color: #9ca3af; font-size: 13px; padding: 12px; text-align: center; }
.log-line-list { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 4px; padding: 8px 12px; }
.log-line-item {
  display: flex; flex-direction: column; gap: 4px; padding: 6px 8px; border: 1px solid #e5e7eb; border-radius: 6px; background: #fff;
}
.log-line-item.active { border-color: #2563eb; background: #f5f9ff; }
.card-chip { font-size: 10px; padding: 1px 7px; border-radius: 8px; background: #eef4ff; color: #2563eb; border: 1px solid #dbe7ff; white-space: nowrap; max-width: 220px; overflow: hidden; text-overflow: ellipsis; flex: none; }
.log-line-header { display: flex; align-items: center; gap: 6px; }
.log-line-no { font-family: ui-monospace, monospace; font-size: 11px; color: #6b7280; font-weight: 700; min-width: 56px; }
.log-line-code { font-family: ui-monospace, monospace; font-size: 11.5px; color: #374151; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-right: 8px; }
.line-status { margin-left: auto; }
.log-line-body { display: flex; flex-direction: column; gap: 2px; }
.log-call { display: flex; gap: 6px; align-items: center; font-size: 11px; }
.log-call-name { font-weight: 600; color: #111827; }
.log-call-badge { padding: 1px 5px; border-radius: 10px; font-size: 9px; font-weight: 700; }
.log-call-badge.ok { background: #dcfce7; color: #166534; }
.log-call-badge.fail { background: #fee2e2; color: #991b1b; }
.log-call-dur { font-family: ui-monospace, monospace; font-size: 9px; color: #9ca3af; margin-left: auto; }
.log-step { display: flex; gap: 4px; font-size: 11px; }
.log-step-name { font-weight: 600; color: #111827; }
.log-step-detail { color: #4b5563; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 260px; }
.log-line-none { font-size: 10.5px; color: #9ca3af; font-style: italic; }
/* 脚本根层级步骤（记录日志函数） */
.root-steps { margin-top: 8px; padding: 6px 10px; background: #fafbfc; border: 1px solid #e5e7eb; border-radius: 6px; }
.root-steps-title { font-size: 12px; font-weight: 700; color: #374151; margin-bottom: 6px; }
.root-step { display: flex; gap: 4px; font-size: 11px; margin-bottom: 3px; }
.root-step-name { font-weight: 600; color: #111827; }
.root-step-detail { color: #4b5563; white-space: pre-wrap; max-width: 500px; overflow: hidden; text-overflow: ellipsis; }

/* QLExpress 表达式轨迹样式 */
.expr-group { margin-top: 6px; padding: 6px 10px; background: #f0f9ff; border-radius: 6px; border: 1px solid #bae6fd; }
.expr-title { font-size: 11px; font-weight: 700; color: #1e40af; margin-bottom: 4px; }
.expr-item { display: flex; gap: 5px; align-items: center; font-size: 11.5px; margin-bottom: 2px; padding: 2px 6px; border-radius: 4px; }
.expr-item:hover { background: #e0f2fe; }
.expr-icon { font-size: 12px; min-width: 18px; text-align: center; }
.expr-desc { font-weight: 600; color: #111827; flex: 1; }
.expr-eval { font-size: 10px; padding: 1px 6px; border-radius: 10px; font-weight: 700; }
.expr-eval.ok { background: #dcfce7; color: #166534; }
.expr-eval.fail { background: #fee2e2; color: #991b1b; }

.call-group { margin-top: 6px; padding: 4px 8px; background: #f9fafb; border-radius: 4px; border: 1px solid #e5e7eb; }
.call-group-title { font-size: 11px; font-weight: 700; color: #374151; margin-bottom: 4px; }

.step-group { margin-top: 6px; padding: 4px 8px; background: #fafbfc; border-radius: 4px; border: 1px solid #e5e7eb; }
.step-group .call-group-title { font-size: 11px; font-weight: 700; color: #374151; margin-bottom: 4px; }

.log-call { display: flex; gap: 6px; align-items: center; font-size: 11px; cursor: pointer; }
.log-call:hover { background: #f3f4f6; border-radius: 4px; }
.log-call-arrow { font-size: 10px; color: #9ca3af; min-width: 12px; }
.log-call-name { font-weight: 600; color: #111827; }
.log-call-badge { padding: 1px 5px; border-radius: 10px; font-size: 9px; font-weight: 700; }
.log-call-badge.ok { background: #dcfce7; color: #166534; }
.log-call-badge.fail { background: #fee2e2; color: #991b1b; }
.log-call-dur { font-family: ui-monospace, monospace; font-size: 9px; color: #9ca3af; margin-left: auto; }
.log-call-detail { margin-left: 20px; margin-top: 2px; padding-left: 8px; border-left: 2px solid #e5e7eb; }
.log-step { display: flex; gap: 4px; font-size: 11px; }
.log-step-indent { margin-left: 14px; font-size: 10.5px; }
.log-step-name { font-weight: 600; color: #111827; }
.log-step-detail { color: #4b5563; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 260px; }
.log-child-call { display: flex; gap: 6px; align-items: center; font-size: 10.5px; margin-top: 2px; }
.log-child-arrow { color: #9ca3af; min-width: 16px; }
.log-child-name { font-weight: 600; color: #111827; }
.log-line-none { font-size: 10.5px; color: #9ca3af; font-style: italic; }

/* 右侧调用行号标签 */
.log-call-line { background: #dbeafe; color: #2563eb; padding: 1px 6px; border-radius: 10px; font-size: 10px; font-weight: 700; margin-left: 6px; }

/* 左侧行号 gutter */
.code-editor { position: relative; }
.code-gutter {
  position: absolute;
  left: 0; top: 0; bottom: 0;
  width: 34px;
  display: flex; flex-direction: column;
  font-family: ui-monospace, monospace;
  font-size: 12px;
  color: #9ca3af;
  user-select: none;
  pointer-events: none;
  border-right: 1px solid #e5e7eb;
}
.gutter-line {
  height: 22px;
  display: flex; align-items: center; justify-content: center;
  color: #9ca3af;
}
.gutter-line.active { color: #2563eb; font-weight: 700; background: #dbeafe; }
.gutter-line.highlight { background: #fef3c7; color: #92400e; font-weight: 700; }

.code-area {
  padding-left: 44px !important;
  resize: vertical !important;
  line-height: 22px;
}

/* ---------- 执行摘要卡片 ---------- */
.exec-summary {
  margin-bottom: 10px;
  padding: 10px 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}
.exec-summary-title {
  font-size: 12px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 8px;
}
.exec-metrics {
  display: flex;
  gap: 12px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.exec-metric {
  flex: 1;
  min-width: 90px;
  padding: 6px 8px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  text-align: center;
}
.exec-metric-value {
  font-size: 14px;
  font-weight: 700;
  color: #1e293b;
}
.exec-metric-label {
  font-size: 10px;
  color: #64748b;
  margin-top: 2px;
}
.exec-metric-value.exec-status.ok {
  color: #166534;
}
.exec-metric-value.exec-status.fail {
  color: #dc2626;
}
.exec-detail {
  margin-top: 6px;
  font-size: 12px;
  color: #475569;
}
.exec-detail-label {
  font-weight: 600;
  color: #334155;
  margin-right: 6px;
}
.exec-detail-chip {
  display: inline-block;
  padding: 2px 8px;
  background: #e2e8f0;
  border-radius: 12px;
  font-size: 11px;
  color: #334155;
  margin-right: 6px;
  margin-bottom: 4px;
}
.exec-error {
  margin-top: 6px;
  padding: 6px 10px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 6px;
  color: #991b1b;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.exec-error-icon {
  font-size: 14px;
}
.exec-result {
  margin-top: 6px;
  padding: 6px 10px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 6px;
  color: #166534;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.exec-result-label {
  font-weight: 600;
  color: #166534;
  margin-right: 6px;
}
.exec-result-value {
  font-family: ui-monospace, monospace;
}

/* 行状态标签 */
.line-status {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 10px;
  font-weight: 700;
  white-space: nowrap;
}
.line-status.ok {
  background: #dcfce7;
  color: #166534;
}
.line-status.skip {
  background: #f1f5f9;
  color: #64748b;
}
.line-status.fail {
  background: #fee2e2;
  color: #dc2626;
}

.app-footer { padding: 16px 0; font-size: 12px; color: #9ca3af; text-align: center; border-top: 1px solid #e5e7eb; margin-top: auto; }

/* 执行摘要折叠状态 */
.exec-summary.collapsed .exec-metrics,
.exec-summary.collapsed .exec-detail,
.exec-summary.collapsed .exec-error,
.exec-summary.collapsed .exec-result {
  display: none;
}
.exec-summary.collapsed {
  padding-bottom: 0;
}
.exec-summary-title {
  cursor: pointer;
  user-select: none;
}
.exec-summary-title:hover {
  color: #2563eb;
}

/* ---------- 行级业务分类日志 ---------- */
.biz-item {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 12.5px;
  line-height: 20px;
  padding: 4px 10px;
  margin: 2px 0;
  background: #f8fafc;
  border-radius: 6px;
  color: #334155;
}
.biz-item .biz-icon { flex: none; }
.biz-item > span:last-child { flex: 1; min-width: 0; }
.biz-item > span > b { color: #1e293b; }

.biz-item.params { background: #f5f3ff; color: #4c1d95; }
.biz-item.cond { background: #fff7ed; color: #7c2d12; }
.biz-item.cond b.pass { color: #166534; }
.biz-item.cond b.reject { color: #b45309; }
.biz-item.assign { background: #f0f9ff; color: #0c5176; }
.biz-item.callctx { background: #eff6ff; cursor: pointer; }
.biz-item.callctx:hover { background: #e0ecff; }
.biz-item.callctx.active { background: #dbeafe; outline: 2px solid #2563eb; }
.biz-item.print { background: #ecfdf5; color: #14532d; }
.biz-item.ret { background: #fffbeb; color: #92400e; font-weight: 600; }
.biz-item.notaken { background: transparent; color: #94a3b8; font-size: 12px; }
.biz-item .failtext { color: #dc2626; }

.call-steps {
  margin: 4px 0 0;
  padding-left: 20px;
  color: #475569;
  font-size: 11.5px;
  list-style: disc;
  width: 100%;
}
.call-steps li { margin: 1px 0; }

.branch-chip {
  flex: none;
  font-size: 10px;
  padding: 1px 7px;
  border-radius: 8px;
  background: #e0e7ff;
  color: #3730a3;
  white-space: nowrap;
}

.raw-trace { margin: 4px 0 2px 10px; width: 100%; font-size: 11px; color: #6b7280; }
.raw-trace summary { cursor: pointer; user-select: none; }
.raw-trace summary:hover { color: #2563eb; }
.raw-expr { padding: 1px 0 1px 14px; font-family: ui-monospace, monospace; }
.raw-expr.skip { color: #b3bac4; }

/* ---------- 编辑卡片：左(ID/名称两行) + 右(默认参数) ---------- */
.meta-grid {
  display: grid;
  grid-template-columns: minmax(280px, 380px) 1fr;
  gap: 16px;
  align-items: stretch;
  padding-bottom: 12px;
  border-bottom: 1px dashed #e2e8f0;
  margin-bottom: 12px;
}
.meta-grid .form-row { margin-bottom: 0; }
.meta-left {
  display: flex; flex-direction: column; justify-content: center; gap: 10px;
  padding: 10px 14px; background: #f8fafc; border: 1px solid #eef0f4; border-radius: 8px;
}
.form-row.inline { display: flex; align-items: center; gap: 10px; }
.form-row.inline label { width: 68px; margin: 0; flex: none; font-size: 12.5px; color: #64748b; }
.params-col label { margin-bottom: 6px; }
.params-area { height: 40px; min-height: 40px; line-height: 19px; }
.params-col .hint { margin-top: 4px; font-size: 11px; }
@media (max-width: 1100px) {
  .meta-grid { grid-template-columns: 1fr; }
}

/* ---------- 编辑器/日志区域铺满剩余高度 ---------- */
.scripts-page { flex: 1; min-height: 0; }
.panel-right { display: flex; flex-direction: column; flex: 1; min-height: 0; width: 100%; }
.editor-card { display: flex; flex-direction: column; flex: 1; min-height: 0; }
.editor-log-wrap { flex: 1; min-height: 0; }
.editor-side { display: flex; flex-direction: column; min-height: 0; }
.editor-side .form-row.full { flex: 1; display: flex; flex-direction: column; min-height: 0; margin-bottom: 0; }
.editor-side .form-row.full label { flex: none; }
.editor-side .code-editor { flex: 1; display: flex; min-height: 0; }
.editor-side .code-editor .code-area {
  height: 100% !important;
  flex: 1;
  resize: none !important;
  min-height: 280px;
}
.log-side { min-height: 0; }
.log-card { flex: 1; min-height: 0; display: flex; flex-direction: column; margin-top: 0; }
.log-card .call-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  max-height: none;
}

/* ---------- 卡片画布 ---------- */
.side-toolbar { display: flex; gap: 8px; align-items: center; margin-bottom: 8px; }
.search-input {
  padding: 6px 10px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 13px;
  min-width: 170px; outline: none;
}
.search-input:focus { border-color: #2563eb; }
.search-input.narrow { min-width: 110px; }

/* 版本对比 */
.version-actions { display: flex; gap: 6px; flex-shrink: 0; }
.diff-block { display: block !important; flex-direction: column; padding: 8px; background: #fff; border: 1px solid #e5e7eb; border-radius: 6px; }
.diff-head { display: flex; justify-content: space-between; align-items: center; font-size: 12px; color: #374151; margin-bottom: 6px; flex-wrap: wrap; gap: 6px; }
.diff-add-n { color: #16a34a; font-weight: 600; margin-right: 6px; }
.diff-remove-n { color: #dc2626; font-weight: 600; }
.diff-legend { color: #9ca3af; }
.diff-legend .lg { font-style: normal; font-weight: 600; padding: 0 2px; }
.lg-add { color: #16a34a; }
.lg-remove { color: #dc2626; }
.diff-body {
  max-height: 240px; overflow-y: auto; font-family: Consolas, "Courier New", monospace;
  font-size: 12px; border: 1px solid #f3f4f6; border-radius: 4px;
}
.diff-row { padding: 1px 10px; white-space: pre-wrap; color: #374151; }
.diff-row.add { background: #ecfdf5; color: #047857; }
.diff-row.add::before { content: "＋ "; }
.diff-row.remove { background: #fef2f2; color: #b91c1c; text-decoration: line-through; }
.diff-row.remove::before { content: "－ "; }
.diff-row.same { color: #6b7280; }
.mode-btn:disabled { opacity: 0.45; cursor: not-allowed; }
.version-panel {
  border: 1px solid #e5e7eb; border-radius: 8px; padding: 10px 12px; margin-bottom: 10px;
  background: #fafbfc;
}
.version-head { display: flex; justify-content: space-between; align-items: center; font-size: 12.5px; font-weight: 600; color: #374151; margin-bottom: 6px; }
.version-list { list-style: none; max-height: 220px; overflow-y: auto; }
.version-list li {
  display: flex; justify-content: space-between; align-items: center; gap: 10px;
  padding: 6px 4px; border-bottom: 1px dashed #e5e7eb;
}
.version-list li:last-child { border-bottom: none; }
.version-info { min-width: 0; }
.version-name { font-size: 13px; color: #111827; font-weight: 500; }
.version-time { font-size: 11px; color: #9ca3af; font-weight: 400; margin-left: 6px; }
.version-preview {
  font-size: 11.5px; color: #6b7280; white-space: nowrap; overflow: hidden;
  text-overflow: ellipsis; max-width: 420px; font-family: Consolas, "Courier New", monospace;
}
.mode-btn { padding: 5px 12px; border: 1px solid #d1d5db; border-radius: 6px; background: #fff; cursor: pointer; font-size: 12.5px; color: #374151; }
.mode-btn:hover { border-color: #2563eb; color: #2563eb; }
.mode-btn.tabon { background: #2563eb; border-color: #2563eb; color: #fff; font-weight: 600; }
.inline-hint { margin-left: 4px; flex: 1; min-width: 0; }

.canvas-layout { flex: 1; min-height: 0; display: flex; gap: 12px; }
.palette { flex: 0 0 190px; border: 1px solid #e5e7eb; border-radius: 8px; background: #f9fafb; padding: 10px; overflow-y: auto; }
.palette-title { font-size: 12px; font-weight: 700; color: #374151; margin-bottom: 8px; }
.palette-item { display: flex; gap: 8px; align-items: flex-start; padding: 8px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; cursor: grab; margin-bottom: 8px; }
.palette-item:hover { border-color: #2563eb; box-shadow: 0 1px 4px rgba(37, 99, 235, 0.15); }
.palette-icon { font-size: 16px; }
.palette-item b { font-size: 12.5px; color: #111827; }
.palette-hint { font-size: 10.5px; color: #9ca3af; }
.palette-tip { font-size: 10.5px; color: #9ca3af; line-height: 1.5; }

.canvas { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 8px; overflow-y: auto; padding: 4px; }
.canvas-title { font-size: 11.5px; color: #6b7280; }
.canvas-card { border: 1px solid #e2e8f0; border-left-width: 4px; border-radius: 8px; background: #fff; padding: 8px 10px; cursor: grab; transition: border-color 0.15s, background 0.15s; }
.canvas-card.t-call { border-left-color: #3b82f6; }
.canvas-card.t-prep { border-left-color: #0ea5e9; background: #f6fdff; }
.canvas-card.t-calc { border-left-color: #8b5cf6; }
.canvas-card.t-log { border-left-color: #10b981; }
.canvas-card.t-if, .canvas-card.t-else, .canvas-card.t-end { border-left-color: #f59e0b; }
.canvas-card.t-return { border-left-color: #6366f1; }
.canvas-card.t-text { border-left-color: #94a3b8; background: #fafafa; }
.canvas-card:hover { background: #f6f9ff; }
.canvas-card.sel { border-color: #2563eb; background: #eef4ff; box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.18); }
.canvas-card.invalid { border-color: #dc2626; background: #fef2f2; }
.card-issue { color: #dc2626; font-size: 11px; margin-top: 4px; }
.card-head { display: flex; gap: 6px; align-items: center; }
.card-icon { font-size: 14px; }
.card-name { font-size: 12.5px; font-weight: 600; color: #1f2937; flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.card-lines { font-size: 10px; font-family: ui-monospace, monospace; color: #94a3b8; white-space: nowrap; }
.card-btn { border: 1px solid #e5e7eb; background: #fff; border-radius: 4px; width: 22px; height: 22px; cursor: pointer; color: #6b7280; line-height: 1; flex: none; }
.card-btn:hover { border-color: #2563eb; color: #2563eb; }
.card-btn.danger:hover { border-color: #dc2626; color: #dc2626; }
.card-btn:disabled { opacity: 0.35; cursor: default; }
.card-fields { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 6px; }
.card-fields label { display: flex; gap: 4px; align-items: center; font-size: 11px; color: #64748b; }
.card-input { padding: 3px 6px; border: 1px solid #d1d5db; border-radius: 4px; font-size: 12px; width: 110px; }
.card-input.wide { width: 220px; }
.card-fields select.card-input { width: auto; min-width: 110px; }
.card-none { font-size: 11px; color: #9ca3af; font-style: italic; }
.canvas-dropzone { border: 1px dashed #cbd5e1; border-radius: 8px; padding: 10px; text-align: center; font-size: 11.5px; color: #94a3b8; }

.gen-preview { margin-top: 8px; border: 1px solid #e5e7eb; border-radius: 8px; background: #f8fafc; padding: 6px 10px; flex: none; }
.gen-preview summary { cursor: pointer; font-size: 11.5px; color: #6b7280; user-select: none; }
.gen-preview summary:hover { color: #2563eb; }
.gen-script { max-height: 200px; overflow: auto; font-size: 12px; color: #374151; margin: 8px 0 4px; font-family: ui-monospace, monospace; }
</style>
