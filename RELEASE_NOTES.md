# 发布说明

> 本文件只写**本次发版新增**的内容（增量格式）。历史版本正文见 [GitHub Releases](https://github.com/miyavis/QlexpressScriptLog/releases)。
> 发版流程：更新本文件 → `git tag vX.Y.Z` → `git push origin vX.Y.Z`，CI 读取本文件作为 Release 正文。

## 本次发版（vX.Y.Z，待填写）

> 发版前把上面版本号改成实际版本，并填写以下板块（没有的板块可删）。

### ✨ 新增功能

- （例：新增 XX 能力，一句话说明用途）

### 🐛 修复

- （例：修复 XX 场景下的问题）

### ⚙️ 改进 / 重构

- （例：XX 性能优化、XX 代码结构调整）

### 📦 部署 / 依赖

- （例：升级 XX 版本；jar 已内嵌前端，`java -jar rule-log-X.Y.Z.jar` 即可）

---

## 历史版本

### v1.1.0（2026-11-19）

自 v1.0.0 以来 27 次提交的大版本更新，覆盖规则创作、可观测性、平台治理、工程升级四大方向。前端单测 163 个、后端单测全绿。

**规则创作**
- 决策表卡片（First/Collect 命中策略，自动生成可执行中文脚本）+ 内置会员折扣演示脚本
- 画布撤销/重做（Ctrl+Z/Y）、场景测试（批量用例 + 通过率汇总）
- 保存前静态校验、历史参数重跑、执行记录对比
- 新建脚本便捷性：函数下拉、参数预填、依赖缺失标红、UUID 防碰撞

**可观测性**
- 执行时间轴逐步回放、调用拓扑视图（耗时着色）
- 完整 OpenTelemetry 集成（traceId 对齐、child span、event、真实采集时间、OTLP 可配）
- W3C traceparent、行级业务日志五类配色

**平台治理**
- RBAC（Spring Security admin/viewer）、H2 审计日志
- 分支与评审状态机（draft→review→published/驳回）、发布标记与版本备注
- 环境标记（dev/staging/prod）+ 筛选、安全加固（沙箱白名单/异常脱敏/原子写/输入校验）

**工程升级**
- Spring Boot 3.2.12 + JDK 17（原 2.7.18 + JDK 8）、QLExpress 4.1.4
- 执行记录落盘 JSON、toast 替代阻塞 alert、帮助页/README 同步

资产：`rule-log-1.1.0.jar`（33MB，内嵌前端）。演示站：https://qlexpress-script-log-production.up.railway.app

### v1.0.0（2026-11-16）

首个开源版本：QLExpress 4.1.3 中文规则执行平台，含卡片画布、行级业务日志、traceId 贯穿、单 jar 部署。
