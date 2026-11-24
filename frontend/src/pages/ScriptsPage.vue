<template>
  <div class="page scripts-page">
        <!-- 顶部：脚本列表（横向卡片条，占满一行） -->
        <div class="script-strip">
          <div class="strip-head">
            <h2>脚本列表</h2>
            <div class="toolbar">
              <button class="btn-primary" @click="newScript">+ 新建脚本</button>
              <button class="btn-secondary" @click="$refs.scriptImportFile.click()">导入脚本</button>
              <button class="btn-secondary" @click="toggleAudit">{{ showAudit ? '收起审计' : '审计日志' }}</button>
              <input
                ref="scriptImportFile"
                type="file"
                accept=".json,application/json"
                style="display: none"
                @change="importScriptFromFile"
              />
              <input
                v-model="scriptKeyword"
                class="search-input"
                placeholder="🔍 搜索脚本名称 / ID"
              />
              <select v-model="envFilter" class="env-filter" title="按环境筛选">
                <option value="all">全部环境</option>
                <option value="dev">dev</option>
                <option value="staging">staging</option>
                <option value="prod">prod</option>
              </select>
              <select v-model="reviewFilter" class="env-filter" title="按评审状态筛选">
                <option value="">全部状态</option>
                <option value="draft">草稿</option>
                <option value="review">评审中</option>
                <option value="published">已发布</option>
              </select>
              <span v-if="scriptKeyword" class="count">{{ visibleScripts.length }}/{{ scripts.length }}</span>
            </div>
          </div>
          <ul class="script-list horiz">
            <li
              v-for="s in visibleScripts"
              :key="s.id"
              :class="{ selected: selectedScriptId === s.id }"
              role="button"
              tabindex="0"
              :aria-selected="selectedScriptId === s.id"
              @click="selectScript(s)"
              @keydown.enter="selectScript(s)"
              @keydown.space.prevent="selectScript(s)"
            >
              <div class="script-item-info">
                <div class="script-item-name">{{ s.name }}</div>
                <div class="script-item-id">{{ s.id }} <span class="env-badge" :class="s.environment || 'dev'">{{ s.environment || 'dev' }}</span></div>
              </div>
              <button class="btn-delete sm" @click.stop="deleteScript(s.id)">删除</button>
            </li>
          </ul>
          <div v-if="showAudit" class="audit-panel">
            <div class="audit-head">审计日志（最近 50 条，H2 持久化）</div>
            <div v-if="auditLogs.length === 0" class="empty">暂无审计记录</div>
            <table v-else class="audit-table">
              <thead><tr><th>时间</th><th>操作者</th><th>动作</th><th>对象</th><th>详情</th></tr></thead>
              <tbody>
                <tr v-for="(a, i) in auditLogs" :key="i">
                  <td>{{ formatTime(a.TS) }}</td>
                  <td>{{ a.ACTOR }}</td>
                  <td><span class="audit-action">{{ a.ACTION }}</span></td>
                  <td>{{ a.TARGET }}</td>
                  <td>{{ a.DETAIL }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-if="isLoadingScripts" class="empty loading-state">⏳ 正在加载脚本…</p>
          <p v-else-if="scripts.length === 0" class="empty">暂无脚本，点击右上角「+ 新建脚本」创建。</p>
          <p v-else-if="visibleScripts.length === 0" class="empty">没有匹配「{{ scriptKeyword }}」的脚本。</p>
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
                  <template v-if="canvasMode">
                    <span class="toolbar-sep"></span>
                    <button class="mode-btn" :disabled="!canUndoCards" @click="undoCards" title="撤销 (Ctrl+Z)">↶ 撤销</button>
                    <button class="mode-btn" :disabled="!canRedoCards" @click="redoCards" title="重做 (Ctrl+Y)">↷ 重做</button>
                  </template>
                  <button
                    class="mode-btn"
                    :class="{ tabon: showHistory }"
                    :disabled="!form.id"
                    @click="showHistory = !showHistory"
                    title="每次保存自动快照“改之前”的版本，最多保留 10 个"
                  >🕘 历史版本</button>
                  <span v-if="canvasMode" class="hint inline-hint">
                    <template v-if="structureIssues.length">⚠️ 画布结构有 {{ structureIssues.length }} 个问题（红色卡片），请先修正</template>
                    <template v-else>卡片改动自动生成脚本；悬停/点击右侧日志行会高亮对应卡片</template>
                  </span>
                </div>
                <div v-if="showHistory && form.id" class="version-panel">
                  <div class="version-head">
                    <span>历史版本（新→旧，最多 10 个；保存动作会自动为“改之前”留快照）</span>
                    <button class="card-btn" @click="showHistory = false" title="收起">✕</button>
                  </div>
                  <p v-if="historyVersions.length === 0" class="empty">暂无历史版本：修改并保存一次后，这里就会出现“改之前”的版本。</p>
                  <ul v-else class="version-list">
                    <li v-for="(ver, vi) in historyVersions" :key="vi">
                      <div class="version-info">
                        <div class="version-name">
                          <span class="version-no">v{{ ver.versionNo || vi + 1 }}</span>
                          {{ ver.name || "未命名" }}
                          <span v-if="currentScript && currentScript.publishedVersion === (ver.versionNo || vi + 1)" class="version-published">已发布</span>
                          <span class="version-time">{{ formatTime(ver.savedAt) }}</span>
                        </div>
                        <div v-if="ver.message" class="version-message">备注：{{ ver.message }}</div>
                        <div class="version-preview">{{ (ver.content || "").split("\n")[0] }}</div>
                      </div>
                      <div class="version-actions">
                        <button class="btn-secondary sm" @click="toggleDiff(vi)">{{ diffVerIndex === vi ? "收起对比" : "对比" }}</button>
                        <button class="btn-secondary sm" @click="loadVersion(ver)">载入此版本</button>
                        <button class="btn-secondary sm" @click="publishVersion(ver)">发布</button>
                      </div>
                    </li>
                    <li v-if="diffVerIndex >= 0" class="diff-block" @dblclick.stop>
                      <div class="diff-head">
                        <span>当前 ↔ {{ historyVersions[diffVerIndex].name || "该版本" }}：
                          <template v-if="activeDiffSummary.changed">
                            <span class="diff-add-n">＋{{ activeDiffSummary.addCount }}</span>
                            <span class="diff-remove-n">－{{ activeDiffSummary.removeCount }}</span>
                          </template>
                          <template v-else>内容完全一致</template>
                        </span>
                        <span class="diff-legend"><i class="lg lg-add">＋</i>回滚会加入 <i class="lg lg-remove">－</i>回滚会删除</span>
                      </div>
                      <div class="diff-body">
                        <div v-for="(row, ri) in activeDiffRows" :key="ri" :class="['diff-row', row.type]">{{ row.text || " " }}</div>
                      </div>
                    </li>
                  </ul>
                  <div class="hint">回滚 = 载入后点「保存」。回滚前的那一刻也会自动存进历史，反悔随时可再载回。</div>
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
                    <div class="canvas" @dragover.prevent @focusin="onCardFocusIn">
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
                            <label>函数
                              <select v-model="c.func" class="card-input" @change="onFuncChange(c)">
                                <option v-if="!c.func" value="">请选择函数</option>
                                <option v-for="f in funcOptions" :key="f.name" :value="f.name">{{ f.name }}</option>
                              </select>
                            </label>
                            <template v-if="funcParams(c.func).length">
                              <div class="card-struct-params">
                                <div class="card-struct-title">参数（按函数定义填写）</div>
                                <ParamInput
                                  v-for="p in funcParams(c.func)"
                                  :key="c.id + '_' + p.name"
                                  :def="p"
                                  :model-value="(c.paramObj || {})[p.name]"
                                  @update:model-value="onParamObjChange(c, { ...(c.paramObj || {}), [p.name]: $event })"
                                />
                              </div>
                            </template>
                            <label v-else>参数<input v-model="c.param" class="card-input" :placeholder="funcParamHint(c.func) || '参数名'" list="known-vars" /></label>
                          </template>
                          <template v-else-if="c.type === 'prepcall'">
                            <label>参数名<input v-model="c.prepName" class="card-input" placeholder="查询用户" /></label>
                            <label>参数值<input v-model="c.prepExpr" class="card-input wide" placeholder="user 或 表达式" list="known-vars" /></label>
                            <label>函数
                              <select v-model="c.func" class="card-input" @change="onFuncChange(c)">
                                <option v-if="!c.func" value="">请选择函数</option>
                                <option v-for="f in funcOptions" :key="f.name" :value="f.name">{{ f.name }}</option>
                              </select>
                            </label>
                            <label>结果存入<input v-model="c.varName" class="card-input" placeholder="积分" /></label>
                            <span class="card-none">此卡片生成两行：参数定义行 + 服务调用行</span>
                          </template>
                          <template v-else-if="c.type === 'compare'">
                            <CompareCard :card="c" />
                          </template>
                          <template v-else-if="c.type === 'decisiontable'">
                            <div class="decision-config">
                              <label>命中策略
                                <select v-model="c.strategy" class="card-input">
                                  <option value="first">First（首个命中）</option>
                                  <option value="collect">Collect（累加）</option>
                                </select>
                              </label>
                              <label>结果变量<input v-model="c.resultVar" class="card-input" placeholder="结果" /></label>
                              <label>默认值<input v-model="c.defaultValue" class="card-input" placeholder="0" /></label>
                            </div>
                            <table class="decision-table">
                              <thead><tr>
                                <th v-for="(cond, ci) in c.conditions" :key="'h'+ci">
                                  <input v-model="c.conditions[ci]" class="card-input" placeholder="变量名" list="known-vars" />
                                </th>
                                <th>结果</th>
                                <th></th>
                              </tr></thead>
                              <tbody>
                                <tr v-for="(row, ri) in c.rows" :key="'r'+ri">
                                  <td v-for="(cell, ci) in row.cells" :key="'c'+ci">
                                    <input v-model="row.cells[ci]" class="card-input" placeholder="> 100" />
                                  </td>
                                  <td><input v-model="row.result" class="card-input" placeholder="0.8" /></td>
                                  <td><button class="card-btn danger" @click="c.rows.splice(ri, 1)" title="删除行">✕</button></td>
                                </tr>
                              </tbody>
                            </table>
                            <div class="decision-actions">
                              <button class="btn-secondary sm" @click="addDecisionRow(c)">+ 加行</button>
                              <button class="btn-secondary sm" @click="addDecisionColumn(c)">+ 加列</button>
                              <span class="card-none">生成脚本会展开为如果/否则，回画布将显示为普通卡片</span>
                            </div>
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
                    <select
                      v-if="scriptExecutions.length"
                      class="log-record-select"
                      :value="selectedLogTraceId"
                      @change="onSelectLogRecord"
                      title="选择查看哪条执行记录"
                    >
                      <option v-for="r in scriptExecutions" :key="r.traceId" :value="r.traceId">
                        {{ formatTime(r.startTime) }} · {{ r.traceId.slice(0, 8) }} · {{ r.success ? '成功' : '失败' }}
                      </option>
                    </select>
                    <button v-if="selectedLogRecord" class="btn-secondary sm" @click="downloadExecution(selectedLogRecord)">导出</button>
                    <button class="btn-secondary sm" @click="loadScriptExecutions(form.id)">刷新</button>
                  </div>
                </div>
                <div v-if="!selectedLogRecord" class="log-empty">执行脚本后，右侧将逐行显示日志</div>
                <div v-else class="call-list">
                  <!-- 执行时间轴：逐步回放 -->
                  <div v-if="executionTimeline.length" class="timeline-bar">
                    <button class="btn-secondary sm" @click="timelineStep(-1)" title="上一步">◀</button>
                    <span class="timeline-progress">
                      {{ timelineCurrent ? `第 ${timelineIndex + 1}/${executionTimeline.length} 步 · 第 ${timelineCurrent.lineNo} 行 · ${timelineCurrent.label}` : `共 ${executionTimeline.length} 步（点 ▶ 开始回放）` }}
                    </span>
                    <button class="btn-secondary sm" @click="timelineStep(1)" title="下一步">▶</button>
                  </div>
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
                    :class="{ active: le.lineNo === hoverLineNo || activeLineSet.has(le.lineNo) || (timelineCurrent && timelineCurrent.lineNo === le.lineNo) }"
                    role="button"
                    tabindex="0"
                    @mouseenter="onLogLineEnter(le.lineNo)"
                    @mouseleave="onLogLineLeave()"
                    @click="onLogLineClick(le.lineNo)"
                    @keydown.enter="onLogLineClick(le.lineNo)"
                    @keydown.space.prevent="onLogLineClick(le.lineNo)"
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

            <div v-if="currentScript" class="review-bar">
              <span class="review-branch">分支：<b>{{ currentScript.branch || 'main' }}</b></span>
              <label class="review-env">环境
                <select v-model="form.environment" class="card-input">
                  <option value="dev">dev</option>
                  <option value="staging">staging</option>
                  <option value="prod">prod</option>
                </select>
              </label>
              <span class="review-status" :class="currentScript.reviewStatus || 'draft'">
                {{ { draft: '草稿', review: '评审中', published: '已发布' }[currentScript.reviewStatus] || '草稿' }}
              </span>
              <span class="review-actions">
                <button class="btn-secondary sm" @click="switchBranch">切换分支</button>
                <button v-if="!currentScript.reviewStatus || currentScript.reviewStatus === 'draft'" class="btn-secondary sm" @click="reviewAction('submit')">提交评审</button>
                <template v-if="currentScript.reviewStatus === 'review'">
                  <button class="btn-success sm" @click="reviewAction('approve')">通过并发布</button>
                  <button class="btn-secondary sm" @click="reviewAction('reject')">驳回</button>
                </template>
              </span>
            </div>

            <div class="version-note-row">
              <label>本次修改备注</label>
              <input v-model="versionMessage" class="card-input" placeholder="可选：说明这次改了什么，便于日后回滚" />
            </div>

            <!-- 场景测试：批量用例 -->
            <div class="case-panel">
              <div class="case-head">
                <span>🧪 场景测试 <span class="hint">（配多组参数，一键跑，看通过率）</span></span>
                <span class="case-head-actions">
                  <button class="btn-secondary sm" @click="addTestCase">+ 加用例</button>
                  <button class="btn-primary sm" :disabled="isTestingCases || testCases.length === 0" @click="runTestCases">
                    {{ isTestingCases ? '执行中…' : '▶ 跑全部用例' }}
                  </button>
                </span>
              </div>
              <template v-if="testCases.length">
                <div v-for="(c, ci) in testCases" :key="c.id" class="case-row">
                  <input v-model="c.name" class="card-input case-name" placeholder="用例名" />
                  <input v-model="c.paramsJson" class="card-input case-params" placeholder='{"user":"张三"}' />
                  <button class="card-btn danger" @click="removeTestCase(ci)" title="删除用例">✕</button>
                  <span v-if="caseResults[ci]" class="case-result" :class="caseResults[ci].ok ? 'ok' : 'fail'">
                    {{ caseResults[ci].ok ? '✓ ' + fmtScalar(caseResults[ci].result) : '✗ ' + caseResults[ci].error }}
                    <span class="case-ms">{{ caseResults[ci].durationMs }}ms</span>
                  </span>
                </div>
                <div v-if="caseResults.length" class="case-summary">
                  通过 <b :class="caseSummary.failed ? 'fail' : 'ok'">{{ caseSummary.passed }}/{{ caseSummary.total }}</b>
                  （{{ (caseSummary.passRate * 100).toFixed(0) }}%）
                </div>
              </template>
              <p v-else class="case-empty">暂无用例。点「+ 加用例」配置参数，保存脚本后可批量执行。</p>
            </div>

            <div class="form-actions">
              <button class="btn-primary" @click="saveScript">保存</button>
              <button class="btn-success" :disabled="isRunning" @click="runScript">{{ isRunning ? "执行中…" : "执行脚本" }}</button>
              <button class="btn-secondary" :disabled="isRunning" @click="trialScript" title="用当前内容+参数立即执行，不保存、不落执行记录">⚡ 试跑</button>
              <button class="btn-secondary" @click="exportCurrentScript" title="导出成 JSON 文件，发给他人可导入">导出脚本</button>
              <button class="btn-delete" @click="deleteScript(form.id)">删除</button>
              <button class="btn-secondary" @click="cancelEdit">取消</button>
            </div>
            <div v-if="trialResult" class="trial-result" :class="trialResult.success ? 'ok' : 'bad'">
              <b>{{ trialResult.success ? '⚡ 试跑结果' : '⚡ 试跑失败' }}</b>
              <code>{{ trialResult.success ? JSON.stringify(trialResult.result) : trialResult.error }}</code>
            </div>
          </div>
          <div v-if="!editing" class="empty">请在上方选择一个脚本，或点击「+ 新建脚本」。</div>
        </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted } from "vue";
import ParamInput from "../components/ParamInput.vue";
import CompareCard from "../components/CompareCard.vue";
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
  isLoadingScripts,
  selectScript,
  newScript,
  cancelEdit,
  savedSnapshot,
  refreshSavedSnapshot,
  hasUnsavedChanges,
  saveScript,
  deleteScript,
  runScript,
  trialResult,
  trialScript,
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
  diffVerIndex,
  toggleDiff,
  savedContentOfCurrent,
  activeDiffRows,
  activeDiffSummary,
  scriptKeyword,
  envFilter,
  reviewFilter,
  auditLogs,
  showAudit,
  toggleAudit,
  visibleScripts,
  execKeyword,
  execStatus,
  visibleExecutions,
  exportCurrentScript,
  importScriptFromFile,
  selectExecution,
  toggleLogDetail,
  toggleCall,
  registryFunctions,
  loadFunctionRegistry,
  funcOptions,
  funcParamHint,
  funcParams,
  onFuncChange,
  onParamObjChange,
  knownVariables,
  duplicateCard,
  focusCardFirstInput,
  goView,
  canUndoCards,
  canRedoCards,
  undoCards,
  redoCards,
  recordHistory,
  testCases,
  caseResults,
  caseSummary,
  isTestingCases,
  addTestCase,
  removeTestCase,
  runTestCases,
  selectLogRecord
} from "../store.js";

// 字段编辑撤销：字段获得焦点时记录快照；同一张卡片连续聚焦不重复记录
let lastFocusedCardId = null;
const onCardFocusIn = (e) => {
  const cardEl = e.target && e.target.closest && e.target.closest("[data-card-id]");
  if (!cardEl) return;
  const cardId = cardEl.getAttribute("data-card-id");
  if (cardId === lastFocusedCardId) return;
  lastFocusedCardId = cardId;
  recordHistory();
};

// 全局快捷键：Ctrl+S 保存；画布模式额外支持 Ctrl+Z/Y 撤销重做、Delete 删选中卡
const onKeydown = (e) => {
  const mod = e.ctrlKey || e.metaKey;
  const tag = e.target && e.target.tagName;
  const inInput = tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT";
  // Ctrl+S 保存（编辑态，输入框内也生效，阻止浏览器保存页面）
  if (mod && (e.key === "s" || e.key === "S")) {
    e.preventDefault();
    if (editing.value) saveScript();
    return;
  }
  if (!canvasMode.value || inInput) return;
  if (mod) {
    if (e.key === "z" && !e.shiftKey) { e.preventDefault(); undoCards(); }
    else if (e.key === "y" || (e.key === "z" && e.shiftKey)) { e.preventDefault(); redoCards(); }
  } else if (e.key === "Delete" && selectedCardId.value) {
    // 删除当前选中的卡片
    const idx = canvasCards.value.findIndex((c) => c.id === selectedCardId.value);
    if (idx >= 0) { e.preventDefault(); removeCard(idx); selectedCardId.value = null; }
  }
};
onMounted(() => window.addEventListener("keydown", onKeydown));
onUnmounted(() => window.removeEventListener("keydown", onKeydown));

// 日志区下拉框：按选中的 traceId 切换展示的执行记录
const onSelectLogRecord = (e) => {
  selectLogRecord(e.target.value);
};
</script>
