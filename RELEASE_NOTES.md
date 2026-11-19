# 发布说明

> 本文件只写**本次发版新增**的内容（增量格式）。历史版本正文见 [GitHub Releases](https://github.com/miyavis/QlexpressScriptLog/releases)。
> 发版流程：更新本文件 → `git tag vX.Y.Z` → `git push origin vX.Y.Z`，CI 读取本文件作为 Release 正文。

## 本次发版（v1.1.2）

为公开 Demo 增加「读公开、写需登录」认证能力，保护写操作不被匿名滥用。

### ✨ 新增功能

- **读公开、写需登录**：
  - 后端：`app.security.enabled` 默认 true——所有 GET 与执行类接口（run / rerun / check）公开；脚本增删改、评审、发布、分支、清空记录等写操作需认证；`/api/audit` 仅管理员。
  - 前端：写操作遇 401 自动弹登录框，输入账号密码后带 Basic Auth 自动重试原请求；凭据在浏览器会话内保持。
  - 演示账号：`admin / admin123`（管理员）、`viewer / viewer123`（只读用户）。
  - 本地开发可设 `APP_SECURITY_ENABLED=false` 完全免登录。

### 🐛 修复 / 改进

- 帮助页与 README 补充认证机制与演示账号说明。
- 安全边界澄清：脚本沙箱为空白名单，脚本无法访问 Java 类 / 系统资源，认证主要防匿名写操作。

### 📦 部署

下载 `rule-log-1.1.2.jar`（已内嵌前端，约 32MB），`java -jar rule-log-1.1.2.jar` 即可，需 JRE 17+。
注意：默认开启写鉴权；若部署环境不需要登录，启动时加 `-Dapp.security.enabled=false`。

在线演示：https://qlexpress-script-log-production.up.railway.app

---

## 历史版本

### v1.1.1（2026-11-19）

紧急修复 v1.1.0 发布 jar 缺少前端页面（release 流程漏跑 `npm run build`，已加构建后校验 index.html）；OTel 导出失败不再吞执行记录；RBAC viewer 权限修正；脚本 get/list 返回深拷贝；导出保留场景用例；首屏加载态、键盘可达性、清空执行记录。

### v1.1.0（2026-11-19）

大版本更新：决策表卡片 + 演示、画布撤销/重做、场景测试、执行对比、执行时间轴、调用拓扑、完整 OpenTelemetry、W3C traceparent、H2 审计、分支评审、环境标记、Spring Boot 3.2 + JDK 17、QLExpress 4.1.4。

### v1.0.0（2026-11-16）

首个开源版本：QLExpress 4.1.3 中文规则执行平台，含卡片画布、行级业务日志、traceId 贯穿、单 jar 部署。
