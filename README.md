# QLExpress4 规则执行平台（附全链路可观测性）

[![CI](https://github.com/miyavis/QlexpressScriptLog/actions/workflows/ci.yml/badge.svg)](https://github.com/miyavis/QlexpressScriptLog/actions/workflows/ci.yml)
[![Release](https://github.com/miyavis/QlexpressScriptLog/actions/workflows/release.yml/badge.svg)](https://github.com/miyavis/QlexpressScriptLog/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

> **在线演示（Railway，一次性 Demo，免费实例可能休眠、重新部署会重置数据）：** [https://splendid-mercy-production-9282fdcf.up.railway.app](https://splendid-mercy-production-9282fdcf.up.railway.app)

本项目演示如何在 QLExpress 4.1.4 规则执行过程中加入**可观测性**，面向业务人员：
- 生产环境**事后查看**，不暂停程序、不需要断点/单步
- 一条规则调用了哪些 Spring Service？Service 内部做了什么？日志全部可查
- 脚本与内部日志**一一对应**（traceId 贯穿），不会串号
- 支持**中文脚本**（如果/则/否则/返回/等于……），降低业务人员阅读门槛
- 天然兼容**卡片化创作**：脚本可由外部标记器/卡片画布转换生成，观测能力锚定脚本文本，无需感知脚本来源（见「卡片化脚本适配方案」章节）
- 脚本与执行记录均持久化为 JSON 文件（重启不丢，Demo 简化；生产可换 DB）

---

## 技术栈

| 组件 | 版本 |
|------|------|
| Spring Boot | 3.2.12 |
| JDK | 17 |
| QLExpress | 4.1.4 (`com.alibaba:qlexpress4`) |
| Vue | 3.4 |
| Vite | 4 |

---

## 设计思路与设计原则

### 总体设计思路

系统要解决的问题是：**业务人员用中文写规则脚本、在生产环境事后排查执行结果**。没有断点、不暂停流程，一切依据都要来自“一次执行留下的完整(replay-able)现场”。因此整条链路围绕三个支柱搭建：

```
① 全量记录现场 ──  一次执行 = traceId + 脚本快照 + 参数快照 + 结果 + 完整调用链 + 行级日志
② 行级归属       ──  日志条目与脚本行号一一对应，与左侧代码互相对照
③ 业务语言叙事   ──  把技术日志翻译成 业务人员读得懂的五类信息（条件/服务/赋值/打印/返回）
```

```
中文脚本 ────► QLExpress4 执行 ────► AOP/埋点采集 ────► 行级聚合 ────► 前端业务叙事视图
(创作层)       (执行层)                (采集层)          (聚合层)          (观测层)
   ▲                                                                        │
   └────────────── 卡片画布（创作层的另一种视图，生成期建立映射）◄──── 执行记录查询
```

### 设计原则

1. **单一事实源（Single Source of Truth）**：运行时与日志归属只锚定**脚本文本**。卡片、日志、快照都是脚本文本的不同视图或附件；手工改文本、卡片改内容，最终都会落到同一份脚本内容上。

2. **traceId 全程贯穿**：一次执行从入口生成 traceId，写入 ThreadLocal（`RuleTraceContext`），AOP 记录的服务调用、Service 内部埋点、脚本层 `记录日志` 全部带到同一个 traceId；同一服务被多脚本先后调用时记录不串号。这是“事后可查”的地基。

3. **映射在生成期建立，不做执行期猜测**：行号 ↔ 卡片的映射由画布在**生成脚本时同步计数**得出（`cardSourceMap`）；QLExpress 表达式轨迹的 `getLine()` 不可靠（可能返回 0），运行时反推必然翻车，所以精确归属只依赖：调用行匹配中文函数名、打印步骤名匹配所在行。

4. **观测语言 = 业务语言**：日志不直接暴露 QLExpress 表达式，而是归类为五类业务信息（❓ 条件判断 / 🔧 服务调用 / 📊 变量赋值 / 📝 脚本打印 / 🎯 返回，顶部另有一次性的 📥 脚本入参展示），Service 只显示脚本中的中文函数名；技术细节不丢——每行保留可折叠的“原始执行轨迹”。

5. **现场完整性（可复现）**：执行记录保存 `scriptSnapshot`（执行时脚本全文）+ 参数快照。脚本之后被修改、卡片重排导致行号变化，**历史日志的解读不受影响**；细节均为只读快照，不引用可变状态。

6. **零侵入采集，边界明确**：执行引擎处加轨迹开关（`traceExpression`），服务调用用 Spring AOP 自动记录入口/出口；同一个 Bean 内部自调用 AOP 拦不到，因此 Service 内部关键步骤由开发显式调用 `StepLogger.log()`——嵌入点少而固定，业务代码无需理解观测框架。

7. **创作层可插拔**：平台不关心脚本是人写的还是卡片画布生成的。卡片化 = 外层转换器（卡片 → 中文脚本 + sourceMap），平台只增加一个 `meta` 透传字段；Canvas 联动按“行号反查卡片”实现，无映射时自动降级为纯行号，无功能断点。

8. **安全边界显式化**：脚本执行有超时（默认 5s）；安全策略当前为 `QLSecurityStrategy.open()`（演示方便），生产按白名单 `whiteList(...)` 收紧，原则是“默认不放行，有明确清单”。

9. **简单可实现优先**：脚本与执行记录都持久化到 JSON 文件（相对路径锚定应用所在目录，随项目走；原子写、坏文件自动备份兜底启动），重启不丢。无数据库、无 RBAC、无环境隔离——Demo 够用，生产可替换为 DB 并收紧安全策略。整个后端零第三方业务依赖（除 Spring/Jackson/QLExpress 本身），为的是任何人拿到代码就能跑起来。

---

## 模块结构（后端）

```
src/main/java/com/example/rulelog/
├── RuleLogApplication.java          启动类
├── express/
│   ├── Express4Engine.java          QLExpress4 引擎封装（中文别名 + 业务函数注册 + 表达式轨迹采集）
│   └── FunctionAliasRegistry.java   中文函数名 ↔ Java 方法映射（供行号归属 / AOP 中文显示）
├── biz/
│   ├── UserService.java             模拟业务 Service 1（手动 StepLogger.log 埋点）
│   └── DiscountService.java         模拟业务 Service 2（嵌套调用 UserService，演示父子调用链）
├── trace/
│   ├── RuleTraceContext.java        ThreadLocal 保存当前 traceId
│   └── StepLogger.java              业务步骤打点器（AOP + 手动埋点 + 脚本层面记录日志函数）
├── aspect/
│   └── ServiceCallAspect.java       AOP 自动记录 Service 入口/出口
├── service/
│   ├── RuleExecutionService.java    执行入口：生成 traceId → 调 QLExpress → 聚合日志 → 按行号分组 → 存记录
│   └── ScriptVersionService.java    脚本版本历史（修改前自动快照，最新在前，最多 10 个，可单测）
├── store/
│   ├── ScriptStore.java             脚本 JSON 文件持久化（data/scripts.json）
│   └── ExecutionRecordStore.java    执行记录库（内存 LinkedHashMap + 落盘 JSON，重启不丢）
├── controller/
│   ├── ScriptController.java        脚本 CRUD (/api/scripts)
│   ├── ExecutionController.java     执行记录查询 (/api/executions)
│   └── FunctionRegistryController.java  函数注册表下发 (/api/functions，画布函数下拉数据源)
├── model/
│   ├── Script.java                  脚本模型
│   ├── ScriptRequest.java           脚本创建/修改请求
│   ├── RuleExecutionRecord.java     一次执行的完整记录（traceId、脚本快照、参数、结果、调用链、行级日志）
│   ├── ServiceCallLog.java          一次 Service 方法调用（含子调用与内部步骤、中文函数名、行号）
│   ├── StepLog.java                 Service 内部单步业务动作
│   └── LineExecution.java           单行脚本的执行记录（calls/steps/expressions/hasActivity）
├── exception/
│   ├── GlobalExceptionHandler.java  统一异常处理
│   ├── NotFoundException.java
│   └── BadRequestException.java
└── util/
    ├── JsonUtils.java               Jackson 工具
    └── ErrorSummarizer.java         执行错误中文摘要器（行号提取 + 中文分类 + 出错行内容 + 保留原始信息）
```
前端（`frontend/`）：Vite 4 + Vue 3.4。**App.vue 是 43 行布局壳**（左侧菜单 + 内容区 + 页脚），全局样式已拆到 `src/styles/`（tokens 设计令牌 / base 通用原语 / scripts 与 executions 页面专属）；页面在 `src/pages/`（ScriptsPage / ExecutionsPage / HelpPage）；共享状态与动作在 `src/store.js`（模块级单例）；纯逻辑抽在 `src/composables/`（`canvasLogic.js` 画布生成/反解析/校验、`functionRegistry.js` 函数注册表辅助、`apiClient.js` 统一请求封装、`logPresentation.js` 行级日志业务描述、`executionTimeline.js` 执行时间轴、`callTopology.js` 调用拓扑、`scriptTransfer.js` 脚本导出/导入、`versionHistory.js` 版本历史、`diffPreview.js` 行级对比、`listFilters.js` 列表筛选、`exportRecord.js` 执行记录导出），全部可单测（`tests/` 下 133 个用例，含 store 导出完整性、行级日志展示与 round-trip 性质测试）。

---

## 核心机制：traceId 贯穿，实现“一一对应”

```
脚本执行入口
  └─ 生成 traceId（UUID，16 位） → 写入 RuleTraceContext（ThreadLocal）
       ├─ StepLogger.startTrace(traceId) 开始采集
       ├─ AOP ServiceCallAspect 拦截 Service 方法 → beginCall / endCall / endCallError
       ├─ Service 内部手动 StepLogger.log("步骤名", "详情")
       └─ 脚本层面 调用 记录日志("步骤名", "详情") 函数
  └─ finally: StepLogger.drainAndRemove() 收集全部 ServiceCallLog + 根步骤
  └─ finally: RuleTraceContext.clear()
  └─ 存入 RuleExecutionRecord（traceId、scriptId、脚本快照、参数、结果、全部调用链）
```

- `traceId` 标识**这一次执行**
- `scriptId` 标识**哪个脚本**
- 同一个 Service 被 A 脚本、B 脚本先后调用时，各自产生独立 `traceId`，内部步骤精确归属，不会串号

AOP 负责 Service 方法入口/出口，`StepLogger.log()` 负责方法内部业务步骤。手动埋点是因为 **Spring AOP 无法拦截同一个 Bean 内部的自调用**。

---

## 许可协议

本项目基于 [MIT](LICENSE) 协议开源——可自由使用、修改、商用（含集成到公司内部系统），只需保留版权声明。

---

## 环境要求

- **后端**：JDK 17（Spring Boot 3.2，构建用 Gradle Wrapper 7.6.4，无需本机安装 Gradle）
- **前端**：Node.js 18+（Vite 4 要求，Node 16 已 EOL，CI/Docker 均用 18）+ npm

---

## 快速启动

### 1. 启动后端（Spring Boot）

```bash
cd D:\project\LogTest
./gradlew bootRun          # Windows 用 gradlew.bat bootRun
```

> Windows PowerShell 中文日志乱码：先执行 `chcp 65001` 把控制台切到 UTF-8 再启动（bootRun 已配置 JVM 以 UTF-8 输出）。Jar 方式运行同理：`java -Dfile.encoding=UTF-8 -jar xxx.jar`。

后端启动成功后，监听 `http://localhost:8080`。首次启动会在 `data/scripts.json` 写入三条示例脚本。

### 2. 启动前端（Vite + Vue）

```bash
cd D:\project\LogTest\frontend
npm install
npm run dev
```

前端启动后，监听 `http://localhost:5173`，通过 Vite 代理把 `/api` 请求转发到后端 `http://localhost:8080`。

打开浏览器访问 `http://localhost:5173` 即可使用。

### 3. 单 jar 部署（给他人分发，推荐）

```bash
./gradlew buildFrontend bootJar    # 一条命令：构建前端 dist → 内嵌进 jar
java -Dfile.encoding=UTF-8 -jar build/libs/rule-log-1.0.0.jar
```

（也可先 `cd frontend && npm run build`，再 `./gradlew bootJar`——dist 已存在时 bootJar 直接内嵌，不存在时打纯 API jar，构建不报错。）

之后**一个 jar 同时提供 API 和前端页面**：浏览器打开 `http://localhost:8080` 即可使用（页面与接口同端口，无需 Nginx）。`data/` 目录会自动出现在 jar 所在目录。开发时用 `npm run dev`（5173）不受影响。

### 4. GitHub 自动构建与发布（CI/CD）

仓库内置两个工作流（`.github/workflows/`）：

| 工作流 | 触发 | 做什么 |
|--------|------|--------|
| **ci.yml** | 每次 push / PR | 后端单测（JDK 17 + Gradle）、前端单测与构建（Node 18），并行两个 job |
| **release.yml** | 推送 `v*` 标签（如 `v1.0.0`），或在 Actions 页手动触发 | 前后端全量单测 → 构建前端 → 打单 jar → **发布 GitHub Release 并附上 jar** |

**发版三步**：

```bash
git tag v1.0.0
git push origin v1.0.0
# → Actions 自动构建，几分钟后 GitHub Releases 页出现 rule-log-1.0.0.jar
```

**服务器部署**：从 GitHub Releases 下载 jar → 拷到服务器 → `java -Dfile.encoding=UTF-8 -jar rule-log-1.0.0.jar`（需 JRE 8+）。`data/` 目录会自动生成在 jar 旁边。也可以注册成 systemd 服务或用 `nohup` 后台运行。

---

## 使用教程

### 页面布局

- **布局壳（App.vue）**：页面是**左侧深色菜单 + 右侧内容区**的产品化布局。左侧菜单：品牌区（🎴 规则执行平台 · QLExpress4 中文脚本）、三个菜单项（📜 脚本管理 / 📊 执行记录，带数量徽标；❓ 使用帮助）、底部技术栈提示。
- **页面组件（`pages/`）**：`ScriptsPage.vue`（脚本列表条 + 编辑器/画布 + 执行日志）、`ExecutionsPage.vue`（执行记录列表 + 详情）、`HelpPage.vue`（使用帮助：中文关键字表、服务函数表、卡片类型表、日志阅读指南）。三个页面共享的全部状态与动作在 `src/store.js`（模块级单例，无 pinia 依赖）——**切页不丢状态**（编辑中的脚本、画布内容、筛选条件都保留）。
- **脚本管理页**：脚本列表在**顶部**（横向卡片条，支持新建/删除/**导入**，工具栏带**搜索框**按名称/ID 过滤），**编辑区在下方占满整行剩余高度**。编辑区左侧支持**两种创作模式**（顶部 `🎴 卡片画布 | 文本编辑` 切换，默认展示画布）；右侧是执行日志，**左右按脚本行号联动**（悬停/点击互相高亮）。编辑卡片顶部一行：脚本 ID + 脚本名称（各一行）+ 默认参数（JSON，两行输入框）。
- **执行记录页**：左侧执行记录列表（顶部**状态筛选**：全部/只看成功/只看失败 + **关键字**按 traceId/脚本名过滤），右侧记录详情（概览卡 / 参数快照 / 返回结果 / 服务调用链 / 脚本快照）。

### 脚本管理页操作

1. **查看脚本**：点击顶部脚本卡片，下方自动加载内容、默认参数，右侧加载该脚本的执行日志；编辑区默认进入**卡片画布**模式（保存过画布的脚本用保存的卡片布局；纯手写脚本自动反解析成卡片展示，识别不了的行转「原样卡」）。
2. **新建脚本**：点击“+ 新建脚本”，默认进入空画布，从部件区拖入/点击添加卡片（取数/计算/打印/条件/否则/结束/返回/原样），画布改动自动生成脚本文本；也可切到“文本编辑”直接写中文脚本。点击“保存”后卡片定义与行号映射随脚本一起写入 `data/scripts.json`。
3. **修改脚本**：画布上直接改卡片（或切到文本模式手改内容），点击“保存”。文本 ↔ 画布互不覆盖：切换时若文本被手工修改过会自动重新反解析，详见「卡片化脚本适配方案」章节。
4. **删除脚本**：在顶部脚本卡片上点击“删除”，或在编辑区按钮组中点击“删除”。
5. **执行脚本**：点击“执行脚本”后**不跳转页面**，右侧直接显示本次执行的**行级业务日志**（画布上可悬停/点日志行高亮对应卡片）：
   - 生成一个 traceId
   - 用 QLExpress 4.1.4 执行脚本
   - 脚本中调用的 Spring Service 会被 AOP 自动记录入口/出口
   - Service 内部的手动埋点步骤会被采集
   - 执行记录同时存入执行记录页的历史列表

### 右侧行级日志（业务叙事版）

每行脚本下方的日志按**业务语义分 5 类**展示（❓ 条件判断 / 🔧 服务调用 / 📊 变量赋值 / 📝 脚本打印 / 🎯 返回；📥 脚本入参在日志顶部显示一次），不再直接暴露 QLExpress 表达式：

| 图标 | 类别 | 说明 | 示例 |
|------|------|------|------|
| 📥 | 脚本入参 | 日志顶部显示一次 | `user = 张三` |
| ❓ | 条件判断 | “如果”行：条件文本 + 成立/不成立 + 走哪个分支 | `条件判断「积分 大于 100」 → 成立，走【则】分支` |
| 🔧 | 服务调用 | 入参/结果/耗时，内部步骤缩进小字 | `服务调用 查询用户积分（张三） → 380，约 26ms` |
| 📊 | 变量赋值 | 变量名 = 最终值（来源） | `打折积分 = 760（积分(380) × 2）` |
| 📝 | 脚本打印 | `记录日志()` 产生的步骤，归属到正确行 | `脚本打印「积分查询完成」：积分=380` |
| 🎯 | 返回 | “返回”行的最终结果 | `返回结果：打折积分 → 760` |

其余细节：

- 行头带有 **则分支 / 否则分支** 标签（按脚本的 如果/否则 结构解析）。
- **未走到的分支**行显示灰色 `⊘ 未走此分支：第 N 行条件成立`，状态标签为“未执行”。判定规则：该行没有服务调用/打印/任何被求值（evaluated=true）的表达式。
- 技术细节不丢：每行底部有折叠的 **▶ 原始执行轨迹（N 条）**，展开可见逐条表达式记录（含未求值节点，灰色标注“未执行”）。
- **执行摘要**卡片默认折叠，点击标题展开：总耗时、服务调用数、业务步骤数、表达式数、整体状态、涉及服务、业务步骤、返回结果、错误信息。

### 执行记录页

1. 左侧列表展示所有执行记录（按时间倒序），显示 traceId（前 16 位）、脚本 ID、名称、状态（成功/失败）、耗时、时间。
2. 点击某条记录，右侧显示详情：
   - **概览卡**：traceId、执行状态、总耗时、脚本 ID（右上角有 **导出 JSON** 按钮）
   - **执行参数快照**：本次执行实际使用的参数（默认参数 + 请求覆盖）
   - **脚本返回结果**：脚本执行返回值，或错误类型与错误信息
   - **服务调用与内部步骤**：每个 Service 调用可展开，查看入参、出参、耗时、内部业务步骤、嵌套子调用
   - **脚本根层级步骤**：脚本中通过 `记录日志()` 函数打点的步骤
   - **脚本快照**：执行时的脚本完整内容（防止脚本后来被修改导致无法复原）
3. **导出执行记录**：概览卡或脚本页日志头点击「导出」→ 下载 `执行记录_<traceId>_<时间>.json`（含完整现场：traceId、脚本快照、参数、结果、调用链）。把文件发给他人即可**离线复盘一次执行**，不必登录系统。

### 默认示例脚本

首次启动自动内置三条脚本（第三条演示"一卡两行"的带参取数卡）：

**1. VIP 折扣判定（`vip_discount`）**

```text
等级 = 查询用户等级(user);
记录日志("等级查询完成", "等级=" + 等级);

如果 (等级 等于 "VIP") 则 {
    记录日志("进入VIP分支", "准备调用折扣服务");
    返回 计算折扣(user);
} 否则 {
    记录日志("进入普通分支", "非VIP用户不打折");
    返回 0;
}
```

默认参数：`{"user": "张三"}` → 返回 `0.88`（VIP 88 折）

**2. 积分翻倍（`points_double`）**

```text
积分 = 查询用户积分(user);
记录日志("积分查询完成", "积分=" + 积分);

如果 (积分 大于 100) 则 {
    打折积分 = 积分 * 2;
} 否则 {
    打折积分 = 积分;
}

返回 打折积分;
```

默认参数：`{"user": "张三"}` → 返回 `760`（380 积分 × 2）

**3. 带参取数演示（`prepcall_demo`，一卡两行）**

```text
查询用户 = user;
积分 = 查询用户积分(查询用户);
记录日志("积分查询完成", "积分=" + 积分);
如果 (积分 大于 100) 则 {
折扣依据 = user;
折扣 = 计算折扣(折扣依据);
返回 积分 * 折扣;
} 否则 {
记录日志("未达门槛", "积分不足100");
返回 积分;
}
```

默认参数：`{"user": "张三"}` → 返回 `334.4`（380 × 0.88）。打开画布可见两张 🧰 **带参取数卡**，各占两行（参数定义行 + 服务调用行），卡片右上角显示行区间。

---

## 中文脚本支持说明

QLExpress 4.1.4 本身支持中文标识符。本项目通过 `Express4Engine.addAlias()` 注册中文别名：

| 中文 | 映射到 |
|------|--------|
| 如果 | if |
| 则 | then |
| 否则 | else |
| 返回 | return |
| 等于 | == |
| 不等于 | != |
| 大于 | > |
| 小于 | < |
| 大于等于 | >= |
| 小于等于 | <= |
| 并且 | && |
| 或者 | \|\| |

业务函数（中文名）：`查询用户等级`、`查询用户积分`、`计算折扣`、`记录日志`。

---

## 卡片化脚本适配方案（脚本由外部标记器/卡片转换生成）

### 卡片化的设计思路

系统分四层，卡片化只发生在**创作层**，平台负责**执行层 + 观测层**，两层之间通过"脚本文本 + sourceMap"解耦：

```
┌ 创作层 ──────── 卡片画布（业务人员拖卡片、配参数、连线）
├ 转换层 ──────── 标记器/转换器：卡片 → QLExpress 中文脚本文本 + cardSourceMap
├ 执行层 ──────── 本平台：QLExpress 4.1.4 执行 + AOP/埋点采集（不关心脚本来源）
└ 观测层 ──────── 本平台：行级日志 + 执行摘要（锚定脚本文本，卡片只是另一视图）
```

三条设计原则：

1. **单一事实源**：运行时只锚定脚本文本；卡片是脚本的"另一种视图"，不是另一种存储。画布丢了脚本还在，脚本改了他照样能看日志。
2. **映射在生成期建立**，不做执行期猜测。QLExpress `ExpressionTrace` 的行号不可靠（`getLine()` 可能返回 0，已验证），运行时反推卡片必然翻车；转换器在生成文本的同时**边写边计数**，映射是确定的。
3. **观测语言 = 业务语言**：日志条目最终按**卡片名**归组展示（如 "VIP 判断 ✓ 成立"），脚本行只是中间坐标，业务人员可以完全不看脚本。

### 卡片与脚本行的映射机制（核心）

**三层坐标模型：卡片 ↔ 行区间 ↔ 日志行号**

```
卡片(cardId)       ←→  脚本行区间 [startLine, endLine]   ←→  日志(lineNo)
   "VIP 判断"              4 ~ 8 行                        第 4/6 行的条件、第 5 行的调用
```

转换器随脚本一起保存 sourceMap（建议挂在 `Script.meta` 透传字段里，后端只存储不解析）：

```json
"meta": {
  "cardSourceMap": [
    { "cardId": "cond_1", "title": "VIP 判断", "kind": "condition",
      "startLine": 4, "endLine": 8,
      "slots": { "then": ["calc_1"], "else": ["ret_2"] } },
    { "cardId": "calc_1", "title": "计算折扣", "kind": "call",
      "startLine": 5, "endLine": 5 },
    { "cardId": "ret_2", "title": "普通分支返回", "kind": "return",
      "startLine": 7, "endLine": 7 }
  ]
}
```

**生成期行号计数（转换器实现要点，约 10 行代码）：**

```text
lineNo = 0
for each card in cards:
    card.startLine = lineNo + 1
    for each textLine in expandCard(card):   # 固定展开规则
        emit(textLine)
        lineNo += 1
    card.endLine = lineNo
```

**行 → 卡片查询规则：**

- 区间包含即命中；空行/注释行命中时归到**最近的前驱卡片**尾部。
- 子卡片（条件卡的分支内嵌套卡片）区间落在父卡片 `endLine` 之内，查询时取**最内层**命中的卡片。
- 多卡片命中同一行（不应发生，靠契约第 1 条保证）时取 first，不报错。

**卡片 kind 与固定展开形状**（形状固定是映射可测的前提；一张卡片允许生成多行，映射记的是行**区间**）：

| kind | 展开行数 | 生成内容 |
|------|---------|---------|
| 取数卡 call | 1 行 | `变量 = 中文函数(参数);` |
| 取数卡·带参 prepcall | **2 行** | `参数名 = 参数值;` + `变量 = 中文函数(参数名);`（先算参数再调服务，一次业务动作一张卡；示例见内置脚本「带参取数演示（一卡两行）」） |
| 条件卡 condition | 头 1 行 + 分支体 + 尾 1 行 | `如果 (…) 则 {` … `} 否则 {` … `}`，分支体是子卡片区间 |
| 计算卡 calc | 1 行 | `变量 = 值/表达式;` |
| 打印卡 log | 1 行 | `记录日志("标题", "内容");` |
| 返回卡 return | 1 行 | `返回 表达式;` |
| 原样卡 text | 1 行 | 脚本原文（反解析识别不了时兜底） |

**多行卡的反解析（文本 → 卡片）合并启发式**：手写脚本里"参数定义行 + 紧随其后的服务调用行（参数正好是该变量）"会合并还原为一张带参卡；合并不上则保持两张普通卡（脚本无损，只是卡片粒度不同）。已保存 `meta.cards` 且 `contentHash` 校验通过的脚本打开时直接用保存的卡片，不触发合并。

### 转换器输出契约（必须遵守）

1. **一行一个语句，一个卡片至少占一行**。禁止把多个卡片压缩进一行，否则行级日志归属立即失效。
2. **卡片 → 行号映射（sourceMap）由转换器在生成时同步记录**，不要事后解析脚本。
3. **只使用平台已注册的中文函数名**（见上表）。若卡片模板生成 `userService.queryPoints()` 这类英文调用，AOP 中文显示与行号匹配都会退化为 `Service.method()` 兜底。
4. **生成"语法级可读"的中文脚本**（如果/则/否则/返回、变量赋值），与手写脚本同一风格——生成的脚本仍会展示在编辑器里供业务人员阅读。
5. **注释锚点的注意事项**：卡片可用 `// 卡片:cond_1` 注释行做锚点（注释行不执行、照常占行号），但**注释文字避免包含"如果/否则"等结构关键字**——前端分支解析按整行文本判断，注释里出现会误判分支。

### 日志侧联动设计（平台侧需要的小改）

- **后端不变**：仍然产出按行号分组的 lineExecutions（调用/打印/表达式三类归属）。
- **日志行头加卡片标签**：前端拿 `lineNo` 查 sourceMap → 显示卡片名 chip（如 `VIP 判断`）；没有映射的脚本自动降级为纯行号，**无功能断点**。
- **正向联动**：点击日志行 → sourceMap 反查卡片 → 通知画布高亮该卡片。
- **反向联动**：画布选中卡片 → `startLine..endLine` → 高亮这些行的日志条目。
- **容错校验（已实现 contentHash 版）**：保存时在 `meta.contentHash` 记录脚本内容 hash（FNV-1a，纯前端计算）；打开脚本时 hash 不一致（外部手改过 content）即视为过期，**不再信任旧卡片**，以脚本文本为真相重新反解析，避免旧卡片静默覆盖手改内容；无 hash 的旧版数据同样走反解析（安全方向）。
- **保存即回写 meta**：无论画布模式还是文本模式，保存时都把"卡片 + 行号映射 + contentHash"一起写入——meta 永远是 content 的派生视图，文本模式保存不再丢失画布元数据。
- **画布结构校验**：如果/否则/结束 配对错误（缺如果、缺结束、重复否则）在画布上**直接标红**并给出中文原因，执行前就能发现，不必等 QLExpress 英文报错。
- **执行前未保存提醒**：脚本有未保存修改时，执行会先提示"保存后执行"，避免执行记录与脚本版本错位。
- **日志行头卡片名标签**：画布模式下日志行头显示所属卡片 chip（悬停可见完整标题）；点击日志行会**滚动定位**到对应卡片。

### 执行错误中文化（ErrorSummarizer）

业务人员看不懂 QLExpress 的英文堆栈，执行失败时 `errorMessage` 存的是**中文摘要**：

```
原始英文异常 ──ErrorSummarizer.summarize(异常, 脚本行列表)──► 摘要
   ① 行号提取：message 中的 "line N"（超界或缺失则不显示）
   ② 中文分类：NullPointerException→空值错误 / ClassCastException→类型不匹配
              / ArithmeticException→数值计算错误 / not found→变量或函数未定义 / 其余→规则运行错误
   ③ 出错行内容：按行号带出脚本该行原文
   ④ 原始英文信息保留在"（原始信息：…）"尾部，开发排查不受影响
```

示例：`第 3 行出错：空值错误（原始信息：... Cannot invoke ...）（出错行内容：打折积分 = 积分 * 2）`。
`errorType` 字段仍是三分类：语法错误 / 超时 / 规则运行错误。

### 脚本导出/导入（scriptTransfer.js）

- **导出**：编辑区操作栏「导出脚本」→ 下载 `规则脚本_<名称>_<时间>.json`，正文含 `name / content / defaultParams / meta（画布卡片+行号映射+contentHash）`，meta 派生方式与保存一致。**不带内部 id/时间戳**（导入时反正会重新生成）。
- **导入**：脚本列表工具栏「导入脚本」→ 选 JSON 文件 → 校验后 `POST /api/scripts`（**id 由后端新生成**，不会覆盖原脚本）→ 自动选中新脚本开始编辑。meta 一起导入，导入后画布布局原样可用。
- **兼容性**：既认平台导出的带包装格式，也认裸的 `{name, content, ...}` 对象；非法 JSON / 缺 content 抛中文错误提示；`defaultParams` 不是对象归一为空对象，`meta` 非法则丢弃（打开时以脚本文本为真相重新反解析）。
- **重名自动改名**：导入脚本与已有脚本同名时自动加后缀——`打折规则` → `打折规则（导入）` → `打折规则（导入 2）`…不覆盖、不混淆。
- **版本对比**：历史面板每个版本带「对比」按钮，展开**行级 diff**（LCS 算法，`diffPreview.js` 纯逻辑可单测）——绿＋=回滚会加回的行，红－=回滚会删掉的行，顶部汇总＋/－行数；内容一致直接提示，载入前不再盲选。对比基准是**最近一次保存的内容**，不含编辑器里未保存的改动。

### 脚本版本历史（保存即快照，回滚可撤销）

业务人员改坏脚本要能反悔。**不加新接口**：`Script` 增加 `versions` 字段，每次 PUT 修改前自动快照"改之前的版本"（`ScriptVersionService.record`，新快照在最前，最多保留 **10** 个，随 scripts.json 持久化）。

```
修改脚本（PUT /api/scripts/{id}）
  └─ ScriptVersionService.record(已有版本, 改之前的脚本)   ← 先快照，后覆盖
      快照内容：versionNo（递增）/ name / content / defaultParams / meta / message（保存备注）/ author / savedAt
  └─ 再应用新内容并落盘
```

保存时可在编辑区填写「本次修改备注」，记录到版本快照说明"为什么改"。每个版本可点「发布」标记为已发布版本（带"已发布"徽标），区分草稿与线上版本（轻量发布标记，未做分支评审状态机）。

前端编辑区「🕘 历史版本」面板：

- 列表新→旧，展示名称/时间/内容首行预览，最多 10 条（`versionHistory.js` 纯逻辑，可单测）。
- **回滚 = 「载入此版本」+「保存」两步**：载入时若版本自带 meta 且 contentHash 匹配则原样恢复画布布局，否则按内容重新反解析；载入后点保存才生效，且保存动作会为"回滚前那一刻"再留快照——**回滚永远可撤销**。
- 编辑器有未保存修改时，载入前会先弹确认，避免静默覆盖。

### 现有能力在卡片化场景下的表现

| 能力 | 依赖 | 卡片化下 |
|------|------|---------|
| 行级日志归属 | 调用行包含中文函数名 | ✅ 天然命中 |
| 中文函数名 ↔ Service 映射 | 使用 addAlias 注册的中文名 | ✅ 不变 |
| 条件判断/分支标签/未走此分支 | 解析脚本文本的 如果/否则/大括号 | ✅ 不变（注意契约第 5 条） |
| 变量赋值/返回/入参展示 | QLExpress 运行时数据 | ✅ 与脚本来源无关 |
| 历史日志可复现 | 执行时保存 `scriptSnapshot` | ✅ 卡片改动导致行号变化不影响历史解读（旧快照配旧 sourceMap 仍可还原卡片视图） |

### 落地顺序建议

1. ~~转换器侧：实现上表的固定展开规则 + 生成期行号计数，输出 `cardSourceMap`。~~（**已实现 MVP**：前端「卡片画布」模式，卡片 → 中文脚本生成期行号计数 + `meta.cards` / `cardSourceMap` 随脚本保存）
2. ~~平台侧（小改）：`Script` 增加 `meta` 透传字段。~~（**已实现**）前端日志行头卡片名标签 + `contentHash` 过期校验 + 点击日志行滚动定位卡片（**均已实现**）。
3. **已实现**：文本 ↔ 画布**双向互转**。「文本 → 画布」把脚本文本反解析成卡片（识别取数/计算/打印/如果/否则/结束/返回；识别不了的行转“原样卡”保留原文，如 并且/或者 复合条件）；「画布 → 文本」即生成脚本预览，执行/保存均使用生成结果。切换时若文本被手工修改过会自动重新反解析。
4. v2 画布联动：日志行 ↔ 卡片的双向高亮升级为"卡片 ↔ 日志"。（**已实现 MVP 联动**：悬停/点击日志行高亮卡片；悬停/点击卡片高亮对应日志行）
5. **不建议**：依赖 QLExpress 运行时栈帧做行号/卡片映射——生成期映射比运行期映射可靠。

---

## 注意事项与扩展建议

1. **执行记录落盘 JSON**：`ExecutionRecordStore` 用 `LinkedHashMap`（插入顺序 + `synchronized`）并原子写到 `data/executions.json`，重启不丢；坏文件安全降级为空库。未引入 DB / RBAC / 环境隔离（超出 Demo 定位），生产可自行替换数据库。每次执行另带 W3C `traceparent` 字段（由 traceId 映射，轻量对接 OTel 生态，不引入 SDK）。
2. **前后端单测**：前端（`cd frontend && npm run test`，133 个用例）覆盖 composables 纯逻辑（画布生成/反解析/合并启发式/hash/结构配对、函数注册表、统一 API 封装、行级日志展示、执行时间轴、调用拓扑、导出、脚本导入导出、版本历史、行级 diff、列表筛选）+ store 导出完整性 + round-trip 性质测试（画布生成的脚本反解析再生成必须逐字一致）；后端（`./gradlew test`）覆盖函数注册表、错误摘要器、版本历史、引擎安全策略（沙箱白名单）、脚本持久化（原子写）、请求校验、行号匹配、执行链路集成、保存前静态校验、历史参数重跑。
3. **脚本文件路径（换机器必看）**：`data` 为相对路径时锚定**应用所在目录**（开发运行=项目根目录；打 jar 运行=jar 所在目录），项目拷到任何机器/位置都能找到，不受“从哪个目录启动”影响。给别人的机器上请**一起拷 `data/scripts.json`**，否则对方首次启动只会看到内置示例脚本；若该文件损坏/手工编辑写坏，程序会在日志里备份为 `scripts.json.bak-时间戳` 并用内置示例启动，修正后重命名回 `scripts.json` 重启即可恢复。启动日志会打印“脚本数据目录（解析后绝对路径）”，可用来确认实际读取位置。
4. **安全策略（已加固）**：`Express4Engine` 使用**空白名单** `QLSecurityStrategy.whiteList(Collections.emptySet())`——脚本只能调用通过 `addFunction` 注册的业务函数与 QLExpress 内建表达式，**无法访问 Java 类/反射/文件系统/系统命令**（单元测试已验证恶意脚本被拦、业务脚本不误伤）。
5. **可选 API 鉴权**：`application.yml` 的 `app.api-token` 默认空=关闭（Demo 零配置可用）；公网生产部署建议设置环境变量 `APP_API_TOKEN=xxx` 启用——启用后所有 `/api/*` 需带 `X-Api-Token` 头，否则 401。前端在浏览器控制台执行 `localStorage.setItem("rulelog-api-token", "xxx")` 即可让页面带上该头（静态资源不受保护，页面仍可打开）。
6. **脚本超时**：默认 5000 ms，可在 `application.yml` 的 `app.script-timeout-millis` 调整。
7. **前端构建**：`npm run build` 会输出静态资源到 `frontend/dist`；配合 `./gradlew bootJar`（或 `./gradlew buildFrontend bootJar` 一条龙）可打进 jar 实现**单 jar 部署**（见快速启动第 3 节）。单独部署到 Nginx 也支持。
8. **AOP 边界**：Spring AOP 拦截不了同一个 Bean 内部的自调用。Service 内部的关键业务步骤应由开发者显式调用 `StepLogger.log(步骤名, 详情)`。

---

## 接口一览

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/scripts` | 列出所有脚本 |
| GET | `/api/scripts/{id}` | 获取脚本详情 |
| POST | `/api/scripts` | 新增脚本 |
| PUT | `/api/scripts/{id}` | 修改脚本 |
| DELETE | `/api/scripts/{id}` | 删除脚本 |
| POST | `/api/executions/run?scriptId=xxx` | 执行脚本（Body 为参数 JSON） |
| GET | `/api/executions?limit=50` | 执行记录列表 |
| GET | `/api/executions/{traceId}` | 单条执行记录详情 |
| GET | `/api/executions/script/{scriptId}?limit=50` | 查询某脚本的所有执行记录（供脚本页右侧日志一一对应展示） |
| GET | `/api/functions` | 已注册的中文函数列表（含所属 Service、方法名、参数提示），画布据此生成函数下拉与参数占位符 |

---

## 自定义卡片：函数注册表驱动（产品化）

画布的"取数卡/带参卡"**不依赖写死在前端的函数清单**，而是注册表驱动：

```
业务 Service 方法 ──Express4Engine 注册──► FunctionAliasRegistry（中文名/Service/方法/参数提示）
                                              │
                                              ▼
                                    GET /api/functions（FunctionRegistryController）
                                              │
                                              ▼
                    前端画布：函数下拉(datalist 可选可输) + 参数占位符 + 变量名推荐
```

**如何新增一个"自定义卡片函数"（不改前端）**：

1. 在业务 Service 写一个方法（如 `RiskService.queryRiskScore(String user)`）。
2. 在 `Express4Engine.registerBusinessFunctions` 里注册两行：
   - `runner.addFunction("查询风控分", ...)` 调用该方法；
   - `aliasRegistry.register("查询风控分", "RiskService", "queryRiskScore", "user")`——第四个参数是**参数提示**，会成为画布参数框的占位符。
3. 重启后端即可：画布函数下拉自动多出"查询风控分"（后端 `/api/functions` 下发，注册失败时前端用内置三函数兜底）。

**拖入即配置（简化配置）**：

- 拖入/点击添加取数类卡片 → 自动按函数名推荐结果变量名（`查询用户等级`→`用户等级`），并**聚焦第一个输入框**。
- 函数框是 datalist：**既能下拉选已注册函数，也能手输任意函数名**（高级用户不被清单限制）。
- 参数框占位符自动带出该函数的 `paramHint`。
- 表达式/左值/右值/返回值输入有**变量补全**（datalist：入参键 + 画布上已定义的变量，按定义顺序）。
- 卡片头新增 **⧉ 复制** 按钮，一键复制当前卡片（含内容）插到其后。

---

## 日志显示说明

执行日志中，**Service 名和方法名优先显示脚本中定义的中文函数名**，与业务人员在脚本里看到的完全一致：

| 脚本中调用 | 日志中显示 |
|-----------|-----------|
| `查询用户等级(user)` | `查询用户等级` |
| `计算折扣(user)` | `计算折扣` |
| `查询用户积分(user)` | `查询用户积分` |
| `记录日志("...")` | `记录日志` |

如果某个 Service 方法未在中文别名表中注册，则回退显示 `ServiceName.methodName()`。

内部业务步骤（`StepLogger.log`）和脚本根层级步骤（`记录日志`函数）均直接显示脚本中定义的中文步骤名，无英文。

---

## 行级日志归属机制（实现说明）

- **服务调用**：按“该行是否包含调用的中文函数名”匹配行号（`FunctionAliasRegistry.getAllChineseNames()`），匹配不到的兜底到脚本最后一行。
- **脚本打印（记录日志产生的步骤）**：按步骤名匹配所在行（`记录日志("积分查询完成",…)` 行包含 “积分查询完成”）。
- **表达式轨迹**：QLExpress `ExpressionTrace` 的 `getLine()` 不可靠（可能返回 0），因此轨迹行号无效时统一归到脚本最后一行，只做“有内容”展示，精确归属以调用/打印为准。
- **已执行/未执行判定**：某行存在服务调用、脚本打印、或任何被求值（evaluated=true）的表达式才算“已执行”；未走分支里的预演节点（evaluated=false）不计入。
- **历史日志可复现**：每次执行保存 `scriptSnapshot`（执行时的脚本全文），后续修改脚本不影响历史记录的解读；前端对“则/否则分支”的解析也基于快照而非当前编辑器内容。

---

## 日志与前端脚本 / 卡片的绑定机制（归属的关键约定）

日志条目和前端两个视图（文本编辑器、卡片画布）能互相点亮，靠的是**统一绑定键 = 脚本行号 lineNo**。后端只管把一切归到行号；前端两条视图都是“行号 → 视图元素”的查询，因此天然互通。

### 数据流（后端 → 前端）

```text
执行 /api/executions/run
  └─ 返回 RuleExecutionRecord
       ├─ scriptSnapshot        执行时脚本全文（文本编辑器与分支解析均基于它）
       ├─ lineExecutions{lineNo → {calls[], steps[], expressions[], hasActivity}}
       ├─ calls[]  / rootSteps[]            顶层列表（执行摘要用）
       └─ 每条 ServiceCallLog 自带 lineNo / chineseName
```

行号归属在 finally 里完成（顺序重要）：先 `setCalls(getRootCalls())` / `setRootSteps(...)`，再调用 `groupLogsByLine(...)`。

### 绑定一：日志 ↔ 文本编辑器（文本模式）

文本模式下，编辑器与日志共用两个状态变量完成**双向联动**：

| 方向 | 交互 | 状态变化 | 视觉结果 |
|------|------|---------|---------|
| 编辑器 → 日志 | 鼠标在 textarea 内移动 | `hoverLineNo = 当前行号`（按 22px 行高换算） | 对应 `log-line-item` 高亮蓝色 |
| 日志 → 编辑器（行号） | 悬停日志行某服务调用 | `highlightCallByLine` 反查该行调用 → `highlightLineNo` | 左侧行号 gutter 高亮 |
| 日志 → 编辑器（调用） | 悬停日志中一行服务调用 | `highlightedCallStartTime = 调用startTime` | 日志中对应调用行 `callHighlight` 高亮 |
| 编辑器行号 → 日志 | 悬停行号 gutter | 反查该行调用 | 对应日志调用高亮 |

要点：行高 22px 是编辑器 `line-height: 22px` 的约定值，改动文本样式时必须同步 `onEditorMouseMove` 里的换算。

### 绑定二：日志 ↔ 卡片画布（画布模式）

画布模式新增三个状态完成联动，绑定键仍是行号：

```text
卡片(cardId) ──genCanvas.sourceMap──► 行区间 [startLine..endLine] ◄──lineExecutions── 日志(lineNo)
```

| 方向 | 交互 | 状态变化 | 视觉结果 |
|------|------|---------|---------|
| 卡片 → 日志 | 悬停/点击画布卡片 | `hoverCardId / selectedCardId` → `activeLineSet`（该卡片段落内所有行号） | 对应日志行沿用 `.active` 样式高亮 |
| 日志 → 卡片 | 悬停/点击日志行 | `cardOfLineId(lineNo)` 在 sourceMap 里反查 → `logHoverCardId / selectedCardId` | 对应卡片 `.sel` 蓝框高亮 |

要点：

1. **映射只存前端内存 + 随脚本保存**：`genCanvas` 在生成脚本时同步计数建立 `sourceMap`（每张卡片记录自己的行区间），保存脚本时随 `meta.cards` / `meta.cardSourceMap` 写入 `scripts.json`；后端只透传不解析。重新打开脚本时前端重建或复用映射。
2. **绑定一致性由生成器保证**：卡片能映射，是因为其生成文本 = 执行所用的脚本内容（同一次生成）。若用户在文本模式手改了脚本再切画布，前端会按“文本是否与上次生成快照(`lastCanvasScript`)一致”决定重新反解析，避免旧卡片映射错位的行。
3. **无映射自动降级**：`sourceMap` 缺失（如外部导入脚本、映射过期校验失败）时，卡片行头映射功能静默关闭，日志仍按行号正常显示，无功能断点。
4. **未执行行的绑定**：未走分支行的日志仍显示（`⊘ 未走此分支`），同样参与映射——点它也能高亮到对应卡片，业务人员能直接看清“哪张卡片没执行”。

### 绑定安全网（为什么不会映射错乱）

- 后端执行与前端展示使用**同一脚本文本**（run 接口读取当前保存的脚本内容；执行记录内含快照）。
- 卡片行区间是**生成期计数**，不是运行期猜测；同一卡片固定展开行数。
- 修改卡片 → watch 自动重新生成脚本 + 重建映射；保存与执行永远使用同一份生成结果。

---

## 云端演示部署（Docker / Railway）

仓库根目录提供一套容器化部署文件，把前端构建产物内嵌进 Spring Boot Jar，单进程对外提供服务：

- [`Dockerfile`](Dockerfile)：多阶段构建（Node 18 构建前端 → Gradle 打 Jar → JRE 17 运行），监听 Railway 注入的 `$PORT`
- [`.dockerignore`](.dockerignore)：排除构建产物、本地数据与 IDE 文件
- [`DOCKER_DEPLOY.md`](DOCKER_DEPLOY.md)：Railway 部署步骤

免费实例会休眠、重启后执行记录从磁盘恢复（落盘 JSON），仅适合演示；生产请自行接数据库并收紧安全策略。
