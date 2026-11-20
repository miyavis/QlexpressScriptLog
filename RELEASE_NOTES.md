# 发布说明

> 本文件只写**本次发版新增**的内容（增量格式）。历史版本正文见 [GitHub Releases](https://github.com/miyavis/QlexpressScriptLog/releases)。
> 发版流程：更新本文件 → `git tag vX.Y.Z` → `git push origin vX.Y.Z`，CI 读取本文件作为 Release 正文。

## 本次发版（v1.1.5）

### ✨ 新增功能

- **退出登录 / 登录入口**：侧边栏底部新增认证区——已登录显示当前用户名 + 「退出」按钮，未登录显示「🔐 登录」按钮。退出时清空会话凭据与登录态；登录成功自动更新用户名显示。

### 📦 部署

下载 `rule-log-1.1.5.jar`（已内嵌前端），`java -jar rule-log-1.1.5.jar` 即可，需 JRE 17+。默认写鉴权，本地可用 `-Dapp.security.enabled=false` 免登录。

在线演示：https://qlexpress-script-log-production.up.railway.app

---

## 历史版本

### v1.1.4（2026-11-19）

401 不带 WWW-Authenticate 头，杜绝浏览器原生登录框。

### v1.1.3（2026-11-19）

修复登录体验：点新建即探测写权限弹登录框；登录用 verifyCredentials 携带 Basic 头真实校验密码。

### v1.1.2（2026-11-19）

读公开、写需登录：GET 与执行类接口公开，增删改/评审/发布/分支/清空需认证，审计仅管理员。

### v1.1.1（2026-11-19）

修复 v1.1.0 发布 jar 缺前端；OTel 韧性；RBAC viewer 权限；深拷贝；场景用例导出；加载态、键盘可达性、清空执行记录。

### v1.1.0（2026-11-19）

大版本：决策表、撤销重做、场景测试、执行对比、时间轴、拓扑、完整 OTel、H2 审计、分支评审、环境标记、Spring Boot 3.2 + JDK 17、QLExpress 4.1.4。

### v1.0.0（2026-11-16）

首个开源版本：QLExpress 4.1.3 中文规则执行平台，含卡片画布、行级业务日志、traceId 贯穿、单 jar 部署。
