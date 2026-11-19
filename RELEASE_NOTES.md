# 发布说明

> 本文件只写**本次发版新增**的内容（增量格式）。历史版本正文见 [GitHub Releases](https://github.com/miyavis/QlexpressScriptLog/releases)。
> 发版流程：更新本文件 → `git tag vX.Y.Z` → `git push origin vX.Y.Z`，CI 读取本文件作为 Release 正文。

## 本次发版（v1.1.4）

修复 v1.1.3 认证弹窗体验问题。

### 🐛 修复

- **浏览器原生登录框问题**：Spring Security 的 `httpBasic()` 默认在 401 响应里带 `WWW-Authenticate: Basic` 头，导致浏览器弹出原生登录对话框（即截图里"此网站要求您登录"），绕过我们自己的登录弹窗，且连续失败会触发浏览器/IP 临时锁定。现自定义 401 响应（纯 JSON、不带该头），前端统一用自己的登录框；补测试断言 401 响应不含 `WWW-Authenticate`。

### 📦 部署

下载 `rule-log-1.1.4.jar`（已内嵌前端），`java -jar rule-log-1.1.4.jar` 即可，需 JRE 17+。默认写鉴权，本地可用 `-Dapp.security.enabled=false` 免登录。

在线演示：https://qlexpress-script-log-production.up.railway.app

---

## 历史版本

### v1.1.3（2026-11-19）

修复登录体验：点新建即探测写权限弹登录框（`/api/auth/status`）；登录用 `verifyCredentials` 携带 Basic 头真实校验密码，错误密码被拒。

### v1.1.2（2026-11-19）

读公开、写需登录：GET 与执行类接口公开，增删改/评审/发布/分支/清空需认证，审计仅管理员；前端 401 弹登录框并重试。

### v1.1.1（2026-11-19）

修复 v1.1.0 发布 jar 缺前端；OTel 导出失败不吞记录；RBAC viewer 权限修正；脚本深拷贝；导出保留场景用例；首屏加载态、键盘可达性、清空执行记录。

### v1.1.0（2026-11-19）

大版本：决策表、撤销重做、场景测试、执行对比、时间轴、拓扑、完整 OTel、H2 审计、分支评审、环境标记、Spring Boot 3.2 + JDK 17、QLExpress 4.1.4。

### v1.0.0（2026-11-16）

首个开源版本：QLExpress 4.1.3 中文规则执行平台，含卡片画布、行级业务日志、traceId 贯穿、单 jar 部署。
