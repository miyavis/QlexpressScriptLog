<template>
  <div class="page executions-page">
        <div class="panel-left">
          <h2>执行记录</h2>
          <div class="toolbar">
            <button class="btn-secondary" @click="loadExecutions">刷新</button>
            <select v-model="execStatus" class="search-input narrow" title="按状态筛选">
              <option value="">全部状态</option>
              <option value="ok">只看成功</option>
              <option value="fail">只看失败</option>
            </select>
            <input v-model="execKeyword" class="search-input" placeholder="🔍 traceId / 脚本名" />
            <span class="count">{{ visibleExecutions.length }}/{{ executions.length }} 条记录</span>
          </div>
          <ul class="execution-list">
            <li
              v-for="r in visibleExecutions"
              :key="r.traceId"
              :class="{ selected: selectedTraceId === r.traceId }"
              role="button"
              tabindex="0"
              :aria-selected="selectedTraceId === r.traceId"
              @click="selectExecution(r)"
              @keydown.enter="selectExecution(r)"
              @keydown.space.prevent="selectExecution(r)"
            >
              <div class="exec-item-main">
                <div class="exec-item-trace">{{ r.traceId }}</div>
                <div class="exec-item-info">
                  <span class="script-id">{{ r.scriptId }}</span>
                  <span class="script-name">{{ r.scriptName }}</span>
                </div>
                <div class="exec-item-meta">
                  <button
                    class="compare-pick"
                    :class="{ picked: compareA?.traceId === r.traceId || compareB?.traceId === r.traceId }"
                    :title="compareA?.traceId === r.traceId ? '已选为第一条' : (compareB?.traceId === r.traceId ? '已选为第二条' : '加入对比')"
                    @click.stop="toggleCompare(r)"
                  >{{ compareA?.traceId === r.traceId ? '①' : (compareB?.traceId === r.traceId ? '②' : '⚖') }}</button>
                  <span :class="['badge', r.success ? 'badge-ok' : 'badge-fail']">
                    {{ r.success ? '成功' : '失败' }}
                  </span>
                  <span class="duration">{{ r.durationMs }} ms</span>
                  <span class="time">{{ formatTime(r.startTime) }}</span>
                </div>
              </div>
              <div v-if="!r.success" class="exec-item-error">{{ r.errorMessage }}</div>
            </li>
          </ul>
          <p v-if="executions.length === 0" class="empty">暂无执行记录</p>
          <p v-else-if="visibleExecutions.length === 0" class="empty">没有符合条件的执行记录（可清空筛选）。</p>
        </div>

        <div class="panel-right">
          <!-- 两次执行对比 -->
          <div v-if="compareA || compareB" class="compare-panel detail-card">
            <div class="card-title">
              ⚖ 执行对比
              <button class="btn-secondary sm" @click="clearCompare">清除</button>
            </div>
            <div class="compare-body">
              <div class="compare-row"><span class="compare-label">①</span><code>{{ compareA?.traceId || '（待选）' }}</code></div>
              <div class="compare-row"><span class="compare-label">②</span><code>{{ compareB?.traceId || '（待选）' }}</code></div>
              <div v-if="executionDiff.length" class="diff-list">
                <div v-for="(d, i) in executionDiff" :key="i" class="diff-item">
                  <span class="diff-field">{{ d.field }}</span>
                  <span class="diff-left">{{ d.left }}</span>
                  <span class="diff-arrow">→</span>
                  <span class="diff-right">{{ d.right }}</span>
                </div>
              </div>
              <p v-else-if="compareA && compareB" class="hint">两次执行无差异（结果/参数/调用耗时一致）。</p>
              <p v-else class="hint">在左侧列表点 ⚖ 选两条记录进行对比。</p>
            </div>
          </div>
          <h2 v-if="!selectedRecord">选择一条记录查看详情</h2>
          <div v-if="selectedRecord" class="detail-card">
            <!-- 概览卡 -->
            <div class="overview">
              <div class="card">
                <div class="card-title">
                  概览
                  <span class="detail-title-actions">
                    <button class="btn-secondary sm" :disabled="isRerunning" @click="rerunRecord(selectedRecord)">
                      {{ isRerunning ? "重跑中…" : "用此参数重跑" }}
                    </button>
                    <button class="btn-secondary sm" @click="downloadExecution(selectedRecord)">导出 JSON</button>
                  </span>
                </div>
                <div class="metrics">
                  <div class="metric">
                    <div class="value">{{ selectedRecord.traceId }}</div>
                    <div class="label">traceId</div>
                  </div>
                  <div class="metric">
                    <div class="value" :class="{ ok: selectedRecord.success, fail: !selectedRecord.success }">
                      {{ selectedRecord.success ? '成功' : '失败' }}
                    </div>
                    <div class="label">执行状态</div>
                  </div>
                  <div class="metric">
                    <div class="value">{{ selectedRecord.durationMs }} ms</div>
                    <div class="label">总耗时</div>
                  </div>
                  <div class="metric">
                    <div class="value">{{ selectedRecord.scriptId }}</div>
                    <div class="label">脚本 ID</div>
                  </div>
                </div>
                <div v-if="selectedRecord.traceparent" class="traceparent-line">
                  W3C traceparent：<code>{{ selectedRecord.traceparent }}</code>
                </div>
              </div>

              <!-- 执行参数快照 -->
              <div class="card">
                <div class="card-title">执行参数快照</div>
                <pre class="json-pre">{{ formatJson(selectedRecord.params) }}</pre>
              </div>

              <!-- 脚本返回结果 -->
              <div class="card">
                <div class="card-title">脚本返回结果</div>
                <div class="result-box">
                  <div v-if="selectedRecord.errorType" class="error-label">
                    {{ selectedRecord.errorType }}：{{ selectedRecord.errorMessage }}
                  </div>
                  <pre v-else class="json-pre">{{ formatJson(selectedRecord.result) }}</pre>
                </div>
              </div>
            </div>

            <!-- 服务调用与内部步骤 -->
            <div class="card full">
              <div class="card-title">
                服务调用与内部步骤
                <span class="detail-title-actions">
                  <button class="btn-secondary sm" :class="{ tabon: callViewMode === 'tree' }" @click="callViewMode = 'tree'">树形</button>
                  <button class="btn-secondary sm" :class="{ tabon: callViewMode === 'topo' }" @click="callViewMode = 'topo'">拓扑</button>
                </span>
              </div>
              <div v-if="callViewMode === 'topo'" class="topology-view">
                <div v-if="topologyNodes.length === 0" class="empty">无服务调用记录</div>
                <div v-else class="topology-grid">
                  <div
                    v-for="node in topologyNodes"
                    :key="node.key"
                    class="topo-node"
                    :class="{ dominant: node.dominant, failed: !node.success }"
                    :style="{ marginLeft: (node.depth * 18) + 'px' }"
                    :title="`耗时 ${node.durationMs}ms，占比 ${(node.share * 100).toFixed(0)}%`"
                  >
                    <span class="topo-label">{{ node.label }}</span>
                    <span class="topo-share">{{ (node.share * 100).toFixed(0) }}%</span>
                    <span class="topo-ms">{{ node.durationMs }}ms</span>
                  </div>
                </div>
                <div class="topology-hint">节点按耗时占比着色：<span class="topo-legend dominant">主导(&ge;50%)</span> · <span class="topo-legend failed">失败</span></div>
              </div>
              <div v-else class="calls-tree">
                <div v-if="selectedRecord.calls.length === 0" class="empty">无服务调用记录</div>
                <div
                  v-for="call in selectedRecord.calls"
                  :key="call.serviceName + '-' + call.methodName + '-' + call.startTime"
                  class="call-node"
                >
                  <div class="call-header" @click="toggleCall(call)">
                    <span class="arrow">{{ call.expanded ? '▾' : '▸' }}</span>
                    <span class="call-service">{{ call.chineseName || (call.serviceName + '.' + call.methodName + '()') }}</span>
                    <span class="call-badge" :class="{ ok: call.success, fail: !call.success }">
                      {{ call.success ? '成功' : '失败' }}
                    </span>
                    <span class="call-duration">{{ call.durationMs }} ms</span>
                  </div>
                  <div v-if="call.expanded" class="call-body">
                    <div class="call-section">
                      <div class="section-label">入参</div>
                      <pre class="json-pre small">{{ call.argsJson }}</pre>
                    </div>
                    <div v-if="call.resultJson" class="call-section">
                      <div class="section-label">出参</div>
                      <pre class="json-pre small">{{ call.resultJson }}</pre>
                    </div>
                    <div v-if="call.errorMessage" class="call-section error">
                      <div class="section-label">异常</div>
                      <div class="error-text">{{ call.errorMessage }}</div>
                    </div>
                    <div v-if="call.internalSteps.length > 0" class="call-section">
                      <div class="section-label">内部业务步骤</div>
                      <ul class="steps-list">
                        <li v-for="(step, idx) in call.internalSteps" :key="idx" class="step-item">
                          <span class="step-name">{{ step.stepName }}</span>
                          <span class="step-detail">: {{ step.detail }}</span>
                          <span class="step-time">{{ formatTime(step.timestamp) }}</span>
                        </li>
                      </ul>
                    </div>
                    <div v-if="call.children.length > 0" class="call-section">
                      <div class="section-label">嵌套子调用</div>
                      <div class="children-calls">
                        <div
                          v-for="child in call.children"
                          :key="child.serviceName + '-' + child.methodName + '-' + child.startTime"
                          class="child-call"
                        >
                          <div class="child-header">
                            <span class="child-service">{{ child.chineseName || (child.serviceName + '.' + child.methodName + '()') }}</span>
                            <span class="child-badge" :class="{ ok: child.success, fail: !child.success }">
                              {{ child.success ? '成功' : '失败' }}
                            </span>
                            <span class="child-duration">{{ child.durationMs }} ms</span>
                          </div>
                          <div v-if="child.argsJson" class="child-args">
                            入参：<span>{{ child.argsJson }}</span>
                          </div>
                          <div v-if="child.resultJson" class="child-result">
                            出参：<span>{{ child.resultJson }}</span>
                          </div>
                          <div v-if="child.errorMessage" class="child-error">
                            异常：<span>{{ child.errorMessage }}</span>
                          </div>
                          <div v-if="child.internalSteps.length > 0" class="child-steps">
                            内部步骤：
                            <ul class="steps-list small">
                              <li v-for="(s, idx) in child.internalSteps" :key="idx" class="step-item small">
                                <span class="step-name">{{ s.stepName }}</span>: <span class="step-detail">{{ s.detail }}</span>
                              </li>
                            </ul>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 脚本根层级步骤 -->
            <div v-if="selectedRecord.rootSteps.length > 0" class="card full">
              <div class="card-title">脚本根层级步骤（记录日志函数）</div>
              <ul class="steps-list">
                <li v-for="(step, idx) in selectedRecord.rootSteps" :key="idx" class="step-item">
                  <span class="step-name">{{ step.stepName }}</span>
                  <span class="step-detail">: {{ step.detail }}</span>
                  <span class="step-time">{{ formatTime(step.timestamp) }}</span>
                </li>
              </ul>
            </div>

            <!-- 脚本快照 -->
            <div class="card full">
              <div class="card-title">脚本快照（执行时的版本）</div>
              <pre class="code-pre">{{ selectedRecord.scriptSnapshot }}</pre>
            </div>
          </div>
        </div>
  </div>
</template>

<script setup>
// 状态与动作全部来自 ../store.js（模块级单例，切页不丢状态）
import {
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
  OP_SYMBOLS,
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
  loadExecutions,
  loadScriptExecutions,
  downloadExecution,
  showHistory,
  historyVersions,
  loadVersion,
  diffVerIndex,
  toggleDiff,
  savedContentOfCurrent,
  activeDiffRows,
  activeDiffSummary,
  scriptKeyword,
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
  compareA,
  compareB,
  executionDiff,
  toggleCompare,
  clearCompare,
  toggleLogDetail,
  toggleCall,
  registryFunctions,
  loadFunctionRegistry,
  funcParamHint,
  onFuncChange,
  knownVariables,
  duplicateCard,
  focusCardFirstInput,
  goView
} from "../store.js";
</script>
