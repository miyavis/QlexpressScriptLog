# 发布说明

> 本文件只写**本次发版新增**的内容（增量格式）。历史版本正文见 [GitHub Releases](https://github.com/miyavis/QlexpressScriptLog/releases)。
> 发版流程：更新本文件 → `git tag vX.Y.Z` → `git push origin vX.Y.Z`，CI 读取本文件作为 Release 正文。

## 本次发版（v1.1.6）

### ✨ 新增功能

- **外部系统跳转到规则编辑页**：支持 query 参数深链——`/?scriptId=vip_discount` 打开编辑页并自动选中最新执行记录；加 `&traceId=A1B2...` 则展示指定记录（找不到回退最新）。日志区顶部新增「执行记录下拉框」，可切换查看该脚本最近 50 条记录（时间 + traceId 前缀 + 成功/失败）。
- 纯前端实现（deepLink 解析 + 选取纯逻辑，9 个单测），不改后端接口；README/帮助页补使用说明。

### 📦 部署

下载 `rule-log-1.1.6.jar`（已内嵌前端），`java -jar rule-log-1.1.6.jar` 即可，需 JRE 17+。默认写鉴权，本地可用 `-Dapp.security.enabled=false` 免登录。

在线演示：https://qlexpress-script-log-production.up.railway.app

---

## 历史版本

### v1.1.5（2026-11-20）

侧边栏退出登录按钮 + 登录入口（isLoggedIn/currentUsername/logout/openLogin）。

### v1.1.4（2026-11-19）

401 不带 WWW-Authenticate 头，杜绝浏览器原生登录框。

### v1.1.3（2026-11-19）

修复登录体验：新建弹框 + 密码真实校验（verifyCredentials）。

### v1.1.2（2026-11-19）

读公开、写需登录认证。

### v1.1.1（2026-11-19）

修复 v1.1.0 发布 jar 缺前端；OTel 韧性；RBAC 修正；深拷贝；场景用例导出；加载态、键盘可达性、清空执行记录。

### v1.1.0（2026-11-19）

大版本：决策表、撤销重做、场景测试、执行对比、时间轴、拓扑、完整 OTel、H2 审计、分支评审、环境标记、Spring Boot 3.2 + JDK 17、QLExpress 4.1.4。

### v1.0.0（2026-11-16）

首个开源版本：QLExpress 4.1.3 中文规则执行平台，含卡片画布、行级业务日志、traceId 贯穿、单 jar 部署。
