# 发布说明

> 本文件只写**本次发版新增**的内容（增量格式）。历史版本正文见 [GitHub Releases](https://github.com/miyavis/QlexpressScriptLog/releases)。
> 发版流程：更新本文件 → `git tag vX.Y.Z` → `git push origin vX.Y.Z`，CI 读取本文件作为 Release 正文。

## 本次发版（v1.1.3）

修复 v1.1.2 认证功能的两个登录体验问题。

### 🐛 修复

- **点新建不弹登录框**：`newScript()` 原本是纯前端操作不发请求，导致不会触发 401。现新增 `GET /api/auth/status`（需认证）探测接口，点新建时先探测写权限，未登录立即弹登录框。
- **登录校验缺陷**：登录时未真正校验密码——原逻辑只重试前端动作或写请求，任意密码都能"登录成功"；修复为用 `verifyCredentials()` 携带 Basic 头向后端真实校验，错误密码返回"账号或密码错误"，正确密码方可进入。
- 抽 `verifyCredentials` 纯函数并补测试（校验必带认证头、正确凭据通过、错误凭据拒绝、非 401 异常透传）；前端测试 172 个。

### 📦 部署

下载 `rule-log-1.1.3.jar`（已内嵌前端），`java -jar rule-log-1.1.3.jar` 即可，需 JRE 17+。默认开启写鉴权，本地可用 `-Dapp.security.enabled=false` 免登录。

在线演示：https://qlexpress-script-log-production.up.railway.app

---

## 历史版本

### v1.1.2（2026-11-19）

读公开、写需登录：GET 与执行类接口公开，脚本增删改/评审/发布/分支/清空记录需认证，审计仅管理员；前端 401 自动弹登录框并重试。

### v1.1.1（2026-11-19）

修复 v1.1.0 发布 jar 缺前端；OTel 导出失败不吞记录；RBAC viewer 权限修正；脚本 get/list 深拷贝；导出保留场景用例；首屏加载态、键盘可达性、清空执行记录。

### v1.1.0（2026-11-19）

大版本：决策表、撤销重做、场景测试、执行对比、时间轴、拓扑、完整 OTel、H2 审计、分支评审、环境标记、Spring Boot 3.2 + JDK 17、QLExpress 4.1.4。

### v1.0.0（2026-11-16）

首个开源版本：QLExpress 4.1.3 中文规则执行平台，含卡片画布、行级业务日志、traceId 贯穿、单 jar 部署。
