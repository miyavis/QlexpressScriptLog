# 发布说明

> 本文件只写**本次发版新增**的内容（增量格式）。历史版本正文见 [GitHub Releases](https://github.com/miyavis/QlexpressScriptLog/releases)。
> 发版流程：更新本文件 → `git tag vX.Y.Z` → `git push origin vX.Y.Z`，CI 读取本文件作为 Release 正文。

## 本次发版（v1.1.1）

紧急修复版，修复 v1.1.0 发布 jar 缺少前端页面的问题，并补齐若干健壮性与体验问题。

### 🐛 修复

- **发布 jar 内嵌前端**：v1.1.0 的 release 流程漏跑 `npm run build`，导致发布的 jar 是纯 API 包（无 index.html，访问 8080 是 404）。本次修复 release.yml：构建前先生成 dist，并在打包后校验 jar 是否含 `BOOT-INF/classes/static/index.html`，缺失即失败，杜绝空壳 jar 再次发布。
- **OTel 导出失败不再吞掉执行记录**：配置 OTLP 后 collector 不可达时，导出异常会导致执行记录不落盘 + ThreadLocal 泄漏。现已加兜底，导出失败仅告警，记录照常保存、上下文照常清理。
- **RBAC 角色语义修正**：开启鉴权后，viewer 原可写（POST/PUT/DELETE/执行/评审/发布）。现改为 GET 只读、所有写操作仅 ADMIN；补 RBAC 集成测试（viewer 读 200/写 403、admin 写 200、未认证 401）。
- **脚本对象并发读**：`ScriptStore.get/list` 原返回活引用，并发 GET 可能读到"改到一半"的脚本。现返回深拷贝，隔离内部存储。
- **导出丢场景用例**：导出脚本时 `meta.cases`（场景测试用例）未包含，导入后用例丢失。现已随导出保留。

### ✨ 新增 / 改进

- **首屏加载态**：脚本/执行记录加载中显示提示，不再闪"暂无数据"。
- **键盘可达性**：脚本条支持 Enter/Space 选中，Ctrl+S 保存，画布 Delete 删选中卡。
- **清空执行记录**：`DELETE /api/executions` + 执行记录页「清空」按钮（审计日志保留）。
- **测试补充**：OTel 失败路径、RBAC 写权限、`/check` 接口边界、导出 cases 往返。

### 📦 部署

下载 `rule-log-1.1.1.jar`（已内嵌前端，约 32MB，含 index.html），`java -jar rule-log-1.1.1.jar` 即可，浏览器访问 8080。需 JRE 17+。

在线演示：https://qlexpress-script-log-production.up.railway.app

---

## 历史版本

### v1.1.0（2026-11-19）

自 v1.0.0 以来 27 次提交的大版本更新，覆盖规则创作、可观测性、平台治理、工程升级四大方向。

**规则创作**：决策表卡片（First/Collect）+ 内置会员折扣演示、画布撤销/重做、场景测试、保存前静态校验、历史参数重跑、执行记录对比、新建脚本便捷性。

**可观测性**：执行时间轴、调用拓扑视图、完整 OpenTelemetry 集成、W3C traceparent、行级业务日志五类配色。

**平台治理**：RBAC、H2 审计日志、分支与评审状态机、发布标记与版本备注、环境标记与筛选、安全加固。

**工程升级**：Spring Boot 3.2.12 + JDK 17、QLExpress 4.1.4、执行记录落盘 JSON、toast 通知、文档同步。

资产：`rule-log-1.1.0.jar`（注：该 jar 因发布流程缺陷缺少前端页面，请改用 v1.1.1）。

### v1.0.0（2026-11-16）

首个开源版本：QLExpress 4.1.3 中文规则执行平台，含卡片画布、行级业务日志、traceId 贯穿、单 jar 部署。
