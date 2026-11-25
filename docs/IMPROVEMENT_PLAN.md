# 改进计划（2026-11-25 基线核对后）

> 排序原则：先恢复文档与测试口径的可信度，再补工程健壮性，最后做架构与功能增强。
> 每项标注了落点（文件/模块）和验收方式；已完成项直接打勾，避免重复执行。

---

## 基线状态

| 项 | 实测结果 | 说明 |
|---|---|---|
| 前端单测 | ✅ 183/183 通过（20 个测试文件） | `cd frontend && npm run test` |
| 前端构建 | ✅ 通过（修复后无告警） | `cd frontend && npm run build`；修复前 `OP_SYMBOLS` 从未在 `store.js` 导出却被两个页面导入，已移除该死代码 |
| 后端单测 | ✅ 131/131 通过（0 失败） | `./gradlew test --no-daemon`；本机 JDK 17 通过 scoop `java` bucket 安装 |
| npm 依赖 | ✅ 0 个漏洞 | 已升级 Vite 4→6.4.6、Vitest 0.34→4.1.12、plugin-vue 4→5.2.4；`npm audit` 归零 |

---

## P0 — 文档与口径一致 ✅ 已完成

- [x] `docs/REFACTOR_PLAN.md` 回填落地状态：P1/P2 主体已完成、后文改标"设计档案"，新增"落地状态"章节；验收关卡中的测试数改为 183。
- [x] `README.md` 启动路径去掉写死的 `D:\project\LogTest`，改为"在本仓库根目录执行"。
- [x] `CONTRIBUTING.md` 测试数量（22/83 → 128 `@Test` / 183）、Node 版本（16+ → 18+）修正。
- [x] `README.md` 示例脚本数量统一为 5 条（VIP 折扣 / 积分翻倍 / 带参取数 / 决策表演示 / 取数+比对演示）。
- [x] `README.md` 前端用例数 140 → 183（两处）。

## P1 — 工程健壮性

1. **后端测试基线 ✅ 已完成**：`./gradlew test --no-daemon` 全绿（131/131，含 P2-6 新增 3 个用例），`CONTRIBUTING.md` 的数量口径已回填。
2. **前端构建基线 ✅ 已完成**：`npm run build` 通过；顺带修掉 `ScriptsPage.vue` / `ExecutionsPage.vue` 中未导出符号 `OP_SYMBOLS` 的死引用（构建时报 `"OP_SYMBOLS" is not exported by src/store.js`）。esbuild 的 postinstall 告警未影响构建。
3. **依赖漏洞处置 ✅ 已完成**：Vite 升至 `6.4.6`（esbuild 0.25，仍兼容 Node 18）、Vitest 升至 `4.1.12`（彻底移除 tinypool）、plugin-vue 升至 `5.2.4`；`npm audit` 从 4 个漏洞降到 0，183 个用例与构建均通过。Vitest 4 要求 Node 20+，已同步把 CI、release 工作流、Dockerfile 与文档从 Node 18 升到 20（最低 Node 版本因此改变，属已知破坏性变更）。
4. **依赖版本集中治理 ✅ 已完成**：新增 `gradle/libs.versions.toml`，统一 Jackson / QLExpress / JUnit / H2 版本；core 与 platform 均改用 `libs.*`。实测漂移已消除：两模块 H2 均为 `2.2.224`（core 原为 `2.1.214`），QLExpress 保持 `4.1.4`。`./gradlew test` 回归全绿。
5. **配置边界收口 ✅ 已完成**：`RuleLogApplication` 启动后检测内置弱口令（`admin123`/`viewer123`）与空 `app.api-token`，打 WARN 日志提示公网部署需修改；不改默认行为，Demo 零配置仍可用。

## P2 — 可观测性与架构

6. **后端消费生成期 sourceMap ✅ 已完成**：`RuleExecutionService.trustedRanges` 仅在 `meta.contentHash` 与脚本文本一致时采用 `meta.cardSourceMap` 行区间；`LineMatcher` 与步骤名查找优先在区间内匹配，避开字符串/注释里的同名干扰；无映射或 hash 过期时自动退回全文匹配。新增 3 个用例覆盖区间约束与过期回退。
7. **OTel 从 trace 扩到 metrics**：`OtelTraceService` 已有 root span / child span / event；补执行次数、失败率、服务调用耗时直方图，便于平台级监控。
8. **审计持久化可插拔**：`AuditStore` 当前为 H2 内存、重启清空；提供可配置的 JDBC 实现，JSON 种子仍保持 Demo 零配置。

## P3 — 功能增强

9. **比对画布打磨**（近期活跃点）：为"只看差异"筛选、并排差异表格补回归测试；定时任务/历史已有 Store，可补边界用例。
10. **规则组合复用**：`REFACTOR_PLAN` 中明确暂缓，价值高但改动大，建议单独立项，不混入日常迭代。
11. **Webhook / 异步回调 / cron 表达式 / 外部接口取数**：维持"暂缓"结论，不动。

---

## 执行建议

- 先做 P1-1 / P1-2 的基线（环境就绪后），把"全绿"从 README 的说法变成实测事实。
- P0 已完成，可单独提交一个纯文档 commit，与后续代码改动分开。
- P2-6 涉及执行链路，改动前先确保 P1 基线测试全绿。
