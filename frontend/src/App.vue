<template>
  <div class="app">
    <header class="app-header">
      <h1>QLExpress4 规则执行平台</h1>
      <span class="tag">中文脚本 · 可观测性 · 事后日志</span>
    </header>

    <nav class="app-nav">
      <button :class="{ active: view === 'scripts' }" @click="view = 'scripts'">脚本管理</button>
      <button :class="{ active: view === 'executions' }" @click="view = 'executions'">执行记录</button>
    </nav>

    <main class="app-main">
      <!-- ========== 脚本管理页 ========== -->
      <section v-if="view === 'scripts'" class="page scripts-page">
        <!-- 顶部：脚本列表（横向卡片条，占满一行） -->
        <div class="script-strip">
          <div class="strip-head">
            <h2>脚本列表</h2>
            <div class="toolbar">
              <button class="btn-primary" @click="newScript">+ 新建脚本</button>
              <button class="btn-secondary" @click="$refs.scriptImportFile.click()">导入脚本</button>
              <input
                ref="scriptImportFile"
                type="file"
                accept=".json,application/json"
                style="display: none"
                @change="importScriptFromFile"
              />
            </div>
          </div>
          <ul class="script-list horiz">
            <li
              v-for="s in scripts"
              :key="s.id"
              :class="{ selected: selectedScriptId === s.id }"
              @click="selectScript(s)"
            >
              <div class="script-item-info">
                <div class="script-item-name">{{ s.name }}</div>
                <div class="script-item-id">{{ s.id }}</div>
              </div>
              <button class="btn-delete sm" @click.stop="deleteScript(s.id)">删除</button>
            </li>
          </ul>
          <p v-if="scripts.length === 0" class="empty">暂无脚本，点击右上角「+ 新建脚本」创建。</p>
        </div>

        <div class="panel-right">
          <div v-if="editing" class="editor-card">
            <div class="meta-grid">
              <div class="meta-left">
                <div class="form-row inline">
                  <label>脚本 ID</label>
                  <input v-model="form.id" readonly />
                </div>
                <div class="form-row inline">
                  <label>脚本名称</label>
                  <input v-model="form.name" placeholder="例如：VIP 折扣判定" />
                </div>
              </div>
              <div class="form-row params-col">
                <label>默认参数（JSON）</label>
                <textarea v-model="form.defaultParamsJson" class="code-area params-area" placeholder='{"user": "张三"}'></textarea>
                <div class="hint">执行时可覆盖；留空为 {}。</div>
              </div>
            </div>

            <!-- 脚本编辑器与执行日志左右并排，每行一一对应 -->
            <div class="editor-log-wrap">
              <!-- 左侧：脚本编辑器（文本模式）或卡片画布（画布模式） -->
              <div class="editor-side">
                <div class="side-toolbar">
                  <button class="mode-btn" :class="{ tabon: !canvasMode }" @click="canvasMode = false">文本编辑</button>
                  <button class="mode-btn" :class="{ tabon: canvasMode }" @click="enterCanvas()">🎴 卡片画布</button>
                  <span v-if="canvasMode" class="hint inline-hint">
                    <template v-if="structureIssues.length">⚠️ 画布结构有 {{ structureIssues.length }} 个问题（红色卡片），请先修正</template>
                    <template v-else>卡片改动自动生成脚本；悬停/点击右侧日志行会高亮对应卡片</template>
                  </span>
                </div>
                <template v-if="!canvasMode">
                  <div class="form-row full tight">
                    <label>脚本内容（QLExpress4 中文脚本）</label>
                    <div class="code-editor">
                      <div class="code-gutter">
                        <div
                          v-for="(line, idx) in scriptLines"
                          :key="idx"
                          class="gutter-line"
                          :class="{ active: hoverLineNo === idx + 1, highlight: highlightLineNo === idx + 1 }"
                          @mouseenter="highlightCallByLine(idx + 1)"
                          @mouseleave="highlightedCallStartTime = null"
                        >{{ idx + 1 }}</div>
                      </div>
                      <textarea
                        v-model="form.content"
                        class="code-area"
                        placeholder="等级 = 查询用户等级(user);..."
                        @mouseenter="onEditorEnter"
                        @mouseleave="hoverLineNo = null"
                        @mousemove="onEditorMouseMove($event)"
                      ></textarea>
                    </div>
                    <div class="hint">支持：如果/则/否则/返回/等于/不等于/大于/小于/并且/或者，以及 查询用户等级、计算折扣、记录日志 等中文函数。</div>
                  </div>
                </template>
                <template v-else>
                  <!-- 卡片画布：左侧部件区 + 右侧纵向卡片流 -->
                  <div class="canvas-layout">
                    <div class="palette">
                      <div class="palette-title">部件</div>
                      <div
                        v-for="t in CARD_TYPES"
                        :key="t.type"
                        class="palette-item"
                        draggable="true"
                        @dragstart="onPaletteDragStart($event, t.type)"
                        @click="addCard(t.type)"
                      >
                        <span class="palette-icon">{{ t.icon }}</span>
                        <span>
                          <b>{{ t.label }}</b>
                          <div class="palette-hint">{{ t.hint }}</div>
                        </span>
                      </div>
                      <div class="palette-tip">拖入画布或点击添加；画布内可拖动卡片调顺序</div>
                      <div class="palette-tip">服务函数 {{ registryFunctions.length }} 个（来自后端注册表，新增 Service 注册一次即自动出现）</div>
                    </div>
                    <div class="canvas" @dragover.prevent>
                      <datalist id="fn-options">
                        <option v-for="f in registryFunctions" :key="f.name" :value="f.name">{{ f.service }}.{{ f.method }}</option>
                      </datalist>
                      <datalist id="known-vars">
                        <option v-for="v in knownVariables" :key="v" :value="v"></option>
                      </datalist>
                      <div class="canvas-title">画布（从上到下按顺序执行）</div>
                      <div
                        v-for="(c, idx) in canvasCards"
                        :key="c.id"
                        class="canvas-card"
                        :class="[typeClass(c.type), { sel: cardSelected(c.id), invalid: !!cardIssue(c.id) }]"
                        :style="{ marginLeft: (cardDepth[idx] * 26) + 'px' }"
                        :data-card-id="c.id"
                        draggable="true"
                        @dragstart="onCardDragStart($event, idx)"
                        @dragover.prevent
                        @drop="onCardDrop($event, idx)"
                        @mouseenter="hoverCardId = c.id"
                        @mouseleave="hoverCardId = hoverCardId === c.id ? null : hoverCardId"
                      >
                        <div class="card-head">
                          <span class="card-icon">{{ cardIcon(c.type) }}</span>
                          <span class="card-name">{{ cardTitle(c) }}</span>
                          <span class="card-lines">{{ cardRangeText(c.id) }}</span>
                          <button class="card-btn" :disabled="idx === 0" @click="moveCard(idx, -1)" title="上移">↑</button>
                          <button class="card-btn" :disabled="idx === canvasCards.length - 1" @click="moveCard(idx, 1)" title="下移">↓</button>
                          <button class="card-btn" @click="duplicateCard(idx)" title="复制此卡">⧉</button>
                          <button class="card-btn danger" @click="removeCard(idx)" title="删除">✕</button>
                        </div>
                        <div class="card-fields">
                          <template v-if="c.type === 'call'">
                            <label>变量<input v-model="c.varName" class="card-input" placeholder="等级" list="known-vars" /></label>
                            <label>函数<input v-model="c.func" class="card-input" list="fn-options" placeholder="查询用户等级" @change="onFuncChange(c)" /></label>
                            <label>参数<input v-model="c.param" class="card-input" :placeholder="funcParamHint(c.func) || 'user'" list="known-vars" /></label>
                          </template>
                          <template v-else-if="c.type === 'prepcall'">
                            <label>参数名<input v-model="c.prepName" class="card-input" placeholder="查询用户" /></label>
                            <label>参数值<input v-model="c.prepExpr" class="card-input wide" placeholder="user 或 表达式" list="known-vars" /></label>
                            <label>函数<input v-model="c.func" class="card-input" list="fn-options" placeholder="查询用户积分" @change="onFuncChange(c)" /></label>
                            <label>结果存入<input v-model="c.varName" class="card-input" placeholder="积分" /></label>
                            <span class="card-none">此卡片生成两行：参数定义行 + 服务调用行</span>
                          </template>
                          <template v-else-if="c.type === 'calc'">
                            <label>变量<input v-model="c.varName" class="card-input" placeholder="打折积分" /></label>
                            <label>表达式<input v-model="c.expr" class="card-input wide" placeholder="积分 * 2" list="known-vars" /></label>
                          </template>
                          <template v-else-if="c.type === 'log'">
                            <label>标题<input v-model="c.title" class="card-input" placeholder="等级查询完成" /></label>
                            <label>内容<input v-model="c.exprRaw" class="card-input wide" placeholder='例如 "等级=" + 等级' /></label>
                          </template>
                          <template v-else-if="c.type === 'if'">
                            <label>左值<input v-model="c.left" class="card-input" placeholder="积分" list="known-vars" /></label>
                            <label>运算
                              <select v-model="c.op" class="card-input">
                                <option v-for="o in CARD_OPS" :key="o" :value="o">{{ o }}</option>
                              </select>
                            </label>
                            <label>右值<input v-model="c.right" class="card-input" placeholder="100 或 &quot;VIP&quot;" list="known-vars" /></label>
                          </template>
                          <template v-else-if="c.type === 'return'">
                            <label>表达式<input v-model="c.expr" class="card-input wide" placeholder="打折积分" list="known-vars" /></label>
                          </template>
                          <template v-else-if="c.type === 'text'">
                            <label>原文<input v-model="c.raw" class="card-input wide" placeholder="脚本原文一行，如 复杂条件输出" /></label>
                          </template>
                          <template v-else-if="c.type === 'else'">
                            <span class="card-none">此卡片代表上面的 如果 进入非成立分支，其后的卡片属于「否则」分支</span>
                          </template>
                          <template v-else-if="c.type === 'end'">
                            <span class="card-none">此卡片代表当前 如果/否则 块结束（编号需与上方 如果 匹配）</span>
                          </template>
                        </div>
                        <div v-if="cardIssue(c.id)" class="card-issue">⚠ {{ cardIssue(c.id) }}</div>
                      </div>
                      <div
                        class="canvas-dropzone"
                        @dragover.prevent
                        @drop="onCardDrop($event, canvasCards.length)"
                      >拖到此处添加卡片</div>
                      <div v-if="canvasCards.length === 0" class="empty">画布为空：从左侧部件区拖入或点击添加卡片</div>
                    </div>
                  </div>
                  <details class="gen-preview">
                    <summary>生成脚本预览（随卡片自动生成，执行与保存均使用它）</summary>
                    <pre class="gen-script">{{ form.content }}</pre>
                  </details>
                </template>
              </div>

              <!-- 右侧：执行日志（与脚本每一行一一对应） -->
              <div class="log-side">
                <div class="log-header">
                  <h3>执行日志</h3>
                  <div class="log-header-btns">
                    <button v-if="selectedLogRecord" class="btn-secondary sm" @click="downloadExecution(selectedLogRecord)">导出</button>
                    <button class="btn-secondary sm" @click="loadScriptExecutions(form.id)">刷新</button>
                  </div>
                </div>
                <div v-if="!selectedLogRecord" class="log-empty">执行脚本后，右侧将逐行显示日志</div>
                <div v-else class="call-list">
                  <!-- 执行摘要卡片 -->
                  <div class="exec-summary" :class="{ collapsed: !execSummaryExpanded }">
                    <div class="exec-summary-title" @click="execSummaryExpanded = !execSummaryExpanded" style="cursor:pointer;">
                      <span>{{ execSummaryExpanded ? '▾' : '▸' }} 执行摘要</span>
                    </div>
                    <div v-if="execSummaryExpanded" class="exec-metrics">
                      <div class="exec-metric">
                        <div class="exec-metric-value">{{ execSummary.durationMs }} ms</div>
                        <div class="exec-metric-label">总耗时</div>
                      </div>
                      <div class="exec-metric">
                        <div class="exec-metric-value">{{ execSummary.totalCalls }}</div>
                        <div class="exec-metric-label">服务调用</div>
                      </div>
                      <div class="exec-metric">
                        <div class="exec-metric-value">{{ execSummary.totalSteps }}</div>
                        <div class="exec-metric-label">业务步骤</div>
                      </div>
                      <div class="exec-metric">
                        <div class="exec-metric-value">{{ execSummary.totalExprs }}</div>
                        <div class="exec-metric-label">表达式</div>
                      </div>
                      <div class="exec-metric">
                        <div class="exec-metric-value exec-status" :class="{ ok: execSummary.success, fail: !execSummary.success }">
                          {{ execSummary.success ? '执行成功' : '执行失败' }}
                        </div>
                        <div class="exec-metric-label">整体状态</div>
                      </div>
                    </div>
                    <div v-if="execSummary.callNames.length > 0" class="exec-detail">
                      <span class="exec-detail-label">涉及服务：</span>
                      <span class="exec-detail-chip" v-for="name in execSummary.callNames" :key="name">{{ name }}</span>
                    </div>
                    <div v-if="execSummary.stepNames.length > 0" class="exec-detail">
                      <span class="exec-detail-label">业务步骤：</span>
                      <span class="exec-detail-chip" v-for="name in execSummary.stepNames" :key="name">{{ name }}</span>
                    </div>
                    <div v-if="execSummary.errorType" class="exec-error">
                      <span class="exec-error-icon">⚠️</span>
                      <span>{{ execSummary.errorType }}：{{ execSummary.errorMessage }}</span>
                    </div>
                    <div v-if="execSummary.result != null && !execSummary.errorType" class="exec-result">
                      <span class="exec-result-label">返回结果：</span>
                      <span class="exec-result-value">{{ formatExprValue(execSummary.result) }}</span>
                    </div>
                  </div>
                  <!-- 脚本入参 -->
                  <div v-if="inputParams.length" class="biz-item params">
                    <span class="biz-icon">📥</span>
                    <span>脚本入参：{{ inputParams.join("，") }}</span>
                  </div>
                  <div
                    v-for="le in lineLogGroups"
                    :key="le.lineNo"
                    class="log-line-item"
                    :class="{ active: le.lineNo === hoverLineNo || activeLineSet.has(le.lineNo) }"
                    @mouseenter="onLogLineEnter(le.lineNo)"
                    @mouseleave="onLogLineLeave()"
                    @click="onLogLineClick(le.lineNo)"
                  >
                    <div class="log-line-header">
                      <span class="log-line-no">第 {{ le.lineNo }} 行</span>
                      <span class="log-line-code">{{ truncate(le.lineContent, 60) }}</span>
                      <span v-if="cardChip(le.lineNo)" class="card-chip" :title="'所属卡片：' + cardChip(le.lineNo)">{{ cardChip(le.lineNo) }}</span>
                      <span v-if="branchChip(le)" class="branch-chip">{{ branchChip(le) }}</span>
                      <span class="line-status" :class="{ ok: le.hasActivity && !le.hasError, skip: !le.hasActivity }">
                        {{ le.hasActivity ? '已执行' : '未执行' }}
                      </span>
                    </div>
                    <div class="log-line-body">
                      <!-- 条件判断（如果是这一行） -->
                      <div v-if="le.lineNo === branchInfo.ifLine" class="biz-item cond">
                        <span class="biz-icon">❓</span>
                        <span>
                          条件判断「{{ condText }}」 →
                          <b :class="condVerdict ? 'pass' : 'reject'">{{ condVerdict ? '成立' : '不成立' }}</b>
                          ，走【{{ condVerdict ? '则' : '否则' }}】分支
                        </span>
                      </div>
                      <!-- 未走此分支 -->
                      <div v-if="notTakenText(le)" class="biz-item notaken">
                        <span class="biz-icon">⊘</span>
                        <span>未走此分支：{{ notTakenText(le) }}</span>
                      </div>
                      <!-- 变量赋值 -->
                      <div v-for="(assign, ai) in lineAssigns(le)" :key="'as' + ai" class="biz-item assign">
                        <span class="biz-icon">📊</span>
                        <span>{{ assign }}</span>
                      </div>
                      <!-- 服务调用 -->
                      <div
                        v-for="c in le.calls"
                        :key="'c' + c.startTime"
                        class="biz-item callctx"
                        :class="{ active: String(c.startTime) === highlightedCallStartTime }"
                      >
                        <span class="biz-icon">🔧</span>
                        <span>
                          服务调用 <b>{{ c.displayName || c.chineseName || c.serviceName }}</b>
                          （{{ callArgs(c) }}） → {{ callResult(c) }}<template v-if="c.durationMs != null">，约 {{ c.durationMs }}ms</template>
                          <template v-if="!c.success"><b class="failtext">，失败：{{ c.errorMessage || '未知错误' }}</b></template>
                        </span>
                        <ul v-if="c.internalSteps && c.internalSteps.length" class="call-steps">
                          <li v-for="(s, si) in c.internalSteps" :key="'is' + si">
                            {{ s.stepName }}<template v-if="s.detail">（{{ s.detail }}）</template>
                          </li>
                        </ul>
                      </div>
                      <!-- 脚本打印 -->
                      <div v-for="(s, si) in le.steps" :key="'p' + si" class="biz-item print">
                        <span class="biz-icon">📝</span>
                        <span>脚本打印「{{ s.stepName }}」<template v-if="s.detail">：{{ s.detail }}</template></span>
                      </div>
                      <!-- 返回 -->
                      <div v-if="leReturn(le)" class="biz-item ret">
                        <span class="biz-icon">🎯</span>
                        <span>{{ leReturn(le) }}</span>
                      </div>
                      <!-- 原始执行轨迹（默认折叠） -->
                      <details v-if="le.expressions && le.expressions.length" class="raw-trace">
                        <summary>原始执行轨迹（{{ le.expressions.length }} 条）</summary>
                        <div
                          v-for="(expr, ei) in le.expressions"
                          :key="'r' + ei"
                          class="raw-expr"
                          :class="{ skip: !expr.evaluated }"
                        >{{ rawExprDesc(expr) }}</div>
                      </details>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div class="form-actions">
              <button class="btn-primary" @click="saveScript">保存</button>
              <button class="btn-success" @click="runScript">执行脚本</button>
              <button class="btn-secondary" @click="exportCurrentScript" title="导出成 JSON 文件，发给他人可导入">导出脚本</button>
              <button class="btn-delete" @click="deleteScript(form.id)">删除</button>
              <button class="btn-secondary" @click="cancelEdit">取消</button>
            </div>
          </div>
          <div v-if="!editing" class="empty">请在上方选择一个脚本，或点击「+ 新建脚本」。</div>
        </div>
      </section>

      <!-- ========== 执行记录页 ========== -->
      <section v-else class="page executions-page">
        <div class="panel-left">
          <h2>执行记录</h2>
          <div class="toolbar">
            <button class="btn-secondary" @click="loadExecutions">刷新</button>
            <span class="count">{{ executions.length }} 条记录</span>
          </div>
          <ul class="execution-list">
            <li
              v-for="r in executions"
              :key="r.traceId"
              :class="{ selected: selectedTraceId === r.traceId }"
              @click="selectExecution(r)"
            >
              <div class="exec-item-main">
                <div class="exec-item-trace">{{ r.traceId }}</div>
                <div class="exec-item-info">
                  <span class="script-id">{{ r.scriptId }}</span>
                  <span class="script-name">{{ r.scriptName }}</span>
                </div>
                <div class="exec-item-meta">
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
        </div>

        <div class="panel-right">
          <h2 v-if="!selectedRecord">选择一条记录查看详情</h2>
          <div v-if="selectedRecord" class="detail-card">
            <!-- 概览卡 -->
            <div class="overview">
              <div class="card">
                <div class="card-title">
                  概览
                  <button class="btn-secondary sm" @click="downloadExecution(selectedRecord)">导出 JSON</button>
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
              <div class="card-title">服务调用与内部步骤</div>
              <div class="calls-tree">
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
      </section>
    </main>

    <footer class="app-footer">
      后端 Spring Boot 2.7.18 + QLExpress 4.1.3 (JDK 8) · 前端 Vite 4 + Vue 3.4 · traceId 贯穿，实现脚本与日志一一对应
    </footer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick, onMounted } from "vue";
import {
  CARD_OPS,
  newCard,
  genScriptFromCards,
  parseScriptToCards,
  hashContent,
  isMetaStale,
  validateCardStructure
} from "./composables/canvasLogic.js";
import {
  normalizeFunctions,
  suggestVarName,
  collectKnownVariables,
  BUILTIN_FUNCTIONS
} from "./composables/functionRegistry.js";
import { buildExecutionExport } from "./composables/exportRecord.js";
import { buildScriptExport, parseScriptImport } from "./composables/scriptTransfer.js";

const API = "/api";

/* ---------- 视图状态 ---------- */
const view = ref("scripts"); // 'scripts' | 'executions'

/* ---------- 脚本管理状态 ---------- */
const scripts = ref([]);
const selectedScriptId = ref(null);
const editing = ref(false);
const form = reactive({
  id: "",
  name: "",
  content: "",
  defaultParamsJson: "{}"
});

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

/** 执行摘要：供业务人员快速了解整体逻辑 */
const execSummary = computed(() => {
  if (!selectedLogRecord.value) return null;
  const r = selectedLogRecord.value;
  let totalCalls = 0;
  let totalSteps = 0;
  let totalExprs = 0;
  let failedCalls = 0;
  const callNames = new Set();
  const stepNames = new Set();
  for (const le of Object.values(r.lineExecutions || {})) {
    totalCalls += le.calls?.length || 0;
    totalSteps += le.steps?.length || 0;
    totalExprs += le.expressions?.length || 0;
    for (const c of le.calls || []) {
      callNames.add(c.chineseName || c.serviceName);
      if (!c.success) failedCalls++;
    }
    for (const s of le.steps || []) {
      stepNames.add(s.stepName);
    }
  }
  return {
    success: r.success,
    durationMs: r.durationMs,
    totalCalls,
    totalSteps,
    totalExprs,
    failedCalls,
    callNames: Array.from(callNames),
    stepNames: Array.from(stepNames),
    result: r.result,
    errorType: r.errorType,
    errorMessage: r.errorMessage
  };
});

const truncate = (s, maxLen) => {
  if (!s) return "";
  return s.length > maxLen ? s.substring(0, maxLen) + "…" : s;
};

const formatExprValue = (v) => {
  if (v == null) return "";
  if (typeof v === "string") return v;
  return JSON.stringify(v);
};

/* ---------- 业务友好的行级日志辅助函数 ---------- */

/** 标量显示：字符串/数字直接显示，其他 JSON 化 */
const fmtScalar = (v) => {
  if (v == null) return "";
  if (typeof v === "string") return v;
  if (typeof v === "number" || typeof v === "boolean") return String(v);
  try {
    return JSON.stringify(v);
  } catch (e) {
    return String(v);
  }
};

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
const branchInfo = computed(() => {
  const info = { map: {}, ifLine: null };
  if (!selectedLogRecord.value || !selectedLogRecord.value.scriptSnapshot) return info;
  const lines = selectedLogRecord.value.scriptSnapshot.split("\n");
  let stage = "none"; // none → then → else → none
  let depth = 0;
  for (let i = 0; i < lines.length; i++) {
    const text = lines[i];
    const opens = (text.match(/\{/g) || []).length;
    const closes = (text.match(/\}/g) || []).length;
    if (stage === "none" && text.includes("如果")) {
      info.ifLine = i + 1;
      stage = "then";
      depth = opens - closes;
      continue;
    }
    if (stage === "then") {
      if (text.includes("否则")) {
        stage = "else";
      } else {
        info.map[i + 1] = "then";
      }
      depth += opens - closes;
      if (depth <= 0) stage = "none";
      continue;
    }
    if (stage === "else") {
      info.map[i + 1] = "else";
      depth += opens - closes;
      if (depth <= 0) stage = "none";
    }
  }
  return info;
});

const lineBranch = (le) => branchInfo.value.map[le.lineNo] || null;

/** 行头分支标签 */
const branchChip = (le) => {
  const b = lineBranch(le);
  return b === "then" ? "则分支" : b === "else" ? "否则分支" : null;
};

/** 条件文本：从脚本第 ifLine 行截取 如果(...) 里的内容 */
const condText = computed(() => {
  if (branchInfo.value.ifLine == null) return "";
  const snap = (selectedLogRecord.value && selectedLogRecord.value.scriptSnapshot) || "";
  const line = (snap.split("\n")[branchInfo.value.ifLine - 1] || "").trim();
  const m = line.match(/如果\s*[（(](.*)[）)]/);
  if (m) return m[1].trim();
  const start = line.indexOf("如果");
  const end = line.indexOf("则", start);
  const body = line.substring(start + 2, end > start ? end : line.length);
  return body.replace(/^[（(]+|[）)]+$/g, "").trim();
});

/** 条件判断结果：取如果行里求过值的布尔运算符 */
const condVerdict = computed(() => {
  const ifLine = branchInfo.value.ifLine;
  if (ifLine == null || !selectedLogRecord.value) return null;
  const le = (selectedLogRecord.value.lineExecutions || {})[ifLine];
  if (!le || !le.expressions) return null;
  const cmp = le.expressions.find(e => e && e.type === "OPERATOR" && typeof e.value === "boolean" && e.evaluated);
  return cmp ? cmp.value : null;
});

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

const OP_SYMBOLS = { "*": "×", "/": "÷", "=": "=", "+": "+", "-": "-" };

/** 变量赋值描述：从表达式轨迹还原 “积分 = 380（来源：…）” */
const lineAssigns = (le) => {
  const exprs = (le.expressions || []).filter(e => e && e.evaluated);
  const eq = exprs.find(e => e.type === "OPERATOR" && e.token === "=");
  if (!eq) return [];
  const assigned = exprs.find(e => e.type === "VARIABLE" && e.value == null);
  const parts = [];
  for (const e of exprs) {
    if (e === eq) continue;
    if (assigned && e === assigned) continue;
    if (e.type === "OPERATOR") {
      parts.push(OP_SYMBOLS[e.token] || e.token);
    } else if (e.type === "VARIABLE") {
      parts.push(e.value != null ? `${e.token}(${fmtScalar(e.value)})` : e.token);
    } else if (e.type === "FUNCTION" || e.type === "METHOD") {
      parts.push(`调用「${e.token}」`);
    } else if (e.type === "VALUE") {
      parts.push(fmtScalar(e.value));
    }
    // IF/RETURN/BLOCK 等结构节点不参与
  }
  // 去除连续重复
  const dedup = [];
  for (const p of parts) {
    if (!dedup.length || dedup[dedup.length - 1] !== p) dedup.push(p);
  }
  // 运算符开头（轨迹父节点先出现）→ 还原成 中缀形式：a × b
  let src = "";
  if (dedup.length && dedup[0].length <= 1) {
    const op = dedup[0];
    const operands = dedup.slice(1);
    src = operands.length >= 2 ? operands.join(` ${op} `) : operands.join(" ");
  } else {
    src = dedup.join(" · ");
  }
  const name = assigned ? assigned.token : "结果";
  return [`${name} = ${fmtScalar(eq.value)}${src ? `（${src}）` : ""}`];
};

/** 返回行描述： RETURN 表达式 → “返回结果：打折积分 → 760” */
const leReturn = (le) => {
  const ret = (le.expressions || []).find(e => e && e.type === "RETURN" && e.evaluated && e.value != null);
  if (!ret) return null;
  const v = (le.expressions || []).find(e => e && e.type === "VARIABLE" && e.value != null && e.token);
  return `返回结果：${v ? v.token + " → " : ""}${fmtScalar(ret.value)}`;
};

/** 服务调用参数格式化：argsJson '[\"张三\"]' → '张三' */
const callArgs = (call) => {
  try {
    const arr = JSON.parse(call.argsJson || "[]");
    return Array.isArray(arr) ? arr.map(fmtScalar).join(", ") : String(call.argsJson || "");
  } catch (e) {
    return String(call.argsJson || "");
  }
};

/** 服务调用结果格式化：去掉 JSON 引号 */
const callResult = (call) => {
  if (call.resultJson == null) return "";
  try {
    return fmtScalar(JSON.parse(call.resultJson));
  } catch (e) {
    return call.resultJson;
  }
};

/** 原始轨迹的单条描述（折叠区内使用） */
const rawExprDesc = (e) => {
  if (!e) return "";
  const labels = {
    IF: "如果", RETURN: "返回", VARIABLE: "变量", OPERATOR: "运算", VALUE: "值",
    FUNCTION: "函数", METHOD: "函数", BLOCK: "代码块"
  };
  let s = `${labels[e.type] || e.type}${e.token ? `【${e.token}】` : ""}`;
  if (e.evaluated && e.value != null) s += ` = ${fmtScalar(e.value)}`;
  if (!e.evaluated) s += "（未执行）";
  return s;
};

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
  const lineHeight = 22;
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
/** if/else/end 配对校验：问题卡片在画布上标红（在执行报错前给出人看得懂的原因） */
const structureIssues = computed(() => validateCardStructure(canvasCards.value));
const issueMap = computed(() => {
  const m = {};
  for (const it of structureIssues.value) {
    m[it.cardId] = it.message;
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
    const res = await fetch(`${API}/scripts`);
    scripts.value = await res.json();
  } catch (e) {
    alert("加载脚本列表失败：" + e);
  }
};

const selectScript = (s) => {
  selectedScriptId.value = s.id;
  editing.value = true;
  form.id = s.id;
  form.name = s.name || "";
  form.content = s.content || "";
  form.defaultParamsJson = compactJson(s.defaultParams);
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
};

/** 保存快照：检测“有未保存的修改”（执行前提醒，避免执行记录与脚本版本错位） */
const savedSnapshot = ref({ name: "", content: "", paramsJson: "" });
const refreshSavedSnapshot = () => {
  savedSnapshot.value = { name: form.name, content: form.content, paramsJson: form.defaultParamsJson };
};
const hasUnsavedChanges = computed(() =>
  editing.value && !!form.id && (
    form.name !== savedSnapshot.value.name ||
    form.content !== savedSnapshot.value.content ||
    form.defaultParamsJson !== savedSnapshot.value.paramsJson
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
    defaultParams
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

  try {
    let res;
    if (form.id) {
      res = await fetch(`${API}/scripts/${form.id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });
    } else {
      res = await fetch(`${API}/scripts`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });
    }
    if (!res.ok) throw new Error(await res.text());
    refreshSavedSnapshot();
    await loadScripts();
    if (!keepOpen) {
      cancelEdit();
    }
  } catch (e) {
    alert("保存脚本失败：" + e);
  }
};

const deleteScript = async (id) => {
  if (!confirm("确定删除该脚本吗？")) return;
  try {
    const res = await fetch(`${API}/scripts/${id}`, { method: "DELETE" });
    if (!res.ok) throw new Error(await res.text());
    await loadScripts();
    if (selectedScriptId.value === id) {
      cancelEdit();
    }
  } catch (e) {
    alert("删除失败：" + e);
  }
};

const runScript = async () => {
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
  try {
    const res = await fetch(`${API}/executions/run?scriptId=${encodeURIComponent(form.id)}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(params)
    });
    if (!res.ok) throw new Error(await res.text());
    const record = await res.json();
    // 执行完成后直接显示到右侧日志框（不跳转页面）
    selectedLogRecord.value = record;
    selectedLogTraceId.value = record.traceId;
    // 刷新该脚本的执行记录列表
    await loadScriptExecutions(form.id);
    // 如需查看全量记录，可手动切换到“执行记录”页
  } catch (e) {
    alert("执行脚本失败：" + e);
  }
};

/* ---------- 执行记录 API ---------- */
const loadExecutions = async () => {
  try {
    const res = await fetch(`${API}/executions?limit=100`);
    executions.value = await res.json();
  } catch (e) {
    alert("加载执行记录失败：" + e);
  }
};

/** 加载指定脚本的执行日志（供脚本页右侧一一对应展示） */
const loadScriptExecutions = async (scriptId) => {
  if (!scriptId) return;
  try {
    const res = await fetch(`${API}/executions/script/${encodeURIComponent(scriptId)}?limit=50`);
    scriptExecutions.value = await res.json();
    // 如果之前选中的日志不在新列表中，清空选中
    if (selectedLogTraceId.value && !scriptExecutions.value.some(r => r.traceId === selectedLogTraceId.value)) {
      selectedLogTraceId.value = null;
      selectedLogRecord.value = null;
    }
  } catch (e) {
    console.warn("加载脚本执行日志失败：" + e);
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
    const req = parseScriptImport(text);
    const res = await fetch(`${API}/scripts`, {
      method: "POST",
      headers: { "Content-Type": "application/json;charset=utf-8" },
      body: JSON.stringify(req)
    });
    if (!res.ok) throw new Error("HTTP " + res.status);
    const created = await res.json();
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

/* ---------- 初始化 ---------- */
onMounted(() => {
  loadScripts();
  loadExecutions();
  loadFunctionRegistry();
});

/* ---------- 函数注册表（产品化：可用函数由后端注册表下发，不再写死在前端） ---------- */
const registryFunctions = ref([...BUILTIN_FUNCTIONS]);

const loadFunctionRegistry = async () => {
  try {
    const res = await fetch(`${API}/functions`);
    if (!res.ok) throw new Error(res.status);
    const fns = normalizeFunctions(await res.json());
    if (fns.length) {
      registryFunctions.value = fns;
    }
  } catch (e) {
    console.warn("函数注册表拉取失败，使用内置函数兜底：" + e);
  }
};

/** 函数的参数提示（作参数输入框占位符），未找到返回空串 */
const funcParamHint = (name) => {
  const f = registryFunctions.value.find((x) => x.name === name);
  return f ? f.paramHint : "";
};

/** 换函数时自动推荐结果变量名（只在变量名为空时接管，不覆盖用户输入） */
const onFuncChange = (card) => {
  if (!card.varName && card.func) {
    card.varName = suggestVarName(card.func);
  }
};

/** 表达式/值输入的变量补全候选：入参键 + 画布上已定义的变量 */
const knownVariables = computed(() => {
  let paramKeys = [];
  try {
    paramKeys = Object.keys(JSON.parse(form.defaultParamsJson || "{}"));
  } catch (e) {
    paramKeys = [];
  }
  return collectKnownVariables(canvasCards.value, paramKeys);
});

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
</script>

<style>
* { box-sizing: border-box; margin: 0; padding: 0; }
body {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", "PingFang SC", "Microsoft YaHei", sans-serif;
  background: #f5f7fa;
  color: #1f2937;
  line-height: 1.5;
}
a { color: inherit; text-decoration: none; }
button {
  cursor: pointer;
  border: none;
  background: none;
  font-family: inherit;
  font-size: inherit;
}

/* ---------- 布局 ---------- */
.app { max-width: 1400px; margin: 0 auto; padding: 0 16px; min-height: 100vh; display: flex; flex-direction: column; }
.app-header {
  display: flex; align-items: center; gap: 12px; padding: 20px 0 12px;
}
.app-header h1 { font-size: 20px; font-weight: 700; letter-spacing: 0.5px; }
.tag {
  font-size: 12px; padding: 2px 8px; border-radius: 10px; background: #e7e9ee; color: #4b5563;
}
.app-nav {
  display: flex; gap: 8px; padding: 8px 0 16px; border-bottom: 1px solid #e5e7eb;
}
.app-nav button {
  padding: 8px 16px; border-radius: 6px; font-weight: 600; color: #6b7280;
  transition: all 0.2s;
}
.app-nav button.active { background: #2563eb; color: #fff; }
.app-nav button:not(.active):hover { background: #f3f4f6; color: #111827; }

.app-main { display: flex; gap: 16px; padding: 16px 0; flex: 1; }
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
