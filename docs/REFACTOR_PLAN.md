# QLExpress 规则执行平台 — 改造计划

> 版本：v1.1.6 基线之上
> 目标：从"带 UI 的规则执行器"升级为"可观测的规则执行平台"
> 状态：P1 / P2 主体已落地（v1.1.6 已验证）；本文档保留为当时的设计档案，当前口径见下节。

---

## 落地状态（2026-11-25 核对）

> 后文各批次描述保留为原始设计稿，不代表仍待实施；以下结论依据 v1.1.6 源码与实测。

- **P1 平台化核心：已完成**。多模块 Gradle（`rulelog-core` / `rulelog-platform`）、`@RuleFunction/@RuleParam` + `FunctionScanner`、`ParamDef` 递归模型与 `ParamBinder/ParamValidator`、`RuleEngine.builder()`、`File/JdbcRuleLoader` + TTL 缓存、`QL_RULE` DDL 与同步、JitPack、对外 `/api/public/*` 三个接口均已落地。
- **P2 比对画布：主体已完成**。`CompareEngine`（取数 DAG + 字段策略）、`/api/compare-rules` + `/api/compare-schedules`、JSON 持久化与历史重跑、前端比对卡与运行页均已落地；近期提交仍在持续打磨联动与明细日志。
- **P3**：试跑按钮已在脚本编辑页实现；其余暂缓项（规则间调用、灰度发布、iframe/JS SDK、cron 表达式、异步回调、外部接口取数）维持"暂缓"结论。
- **后续改进**：见 `docs/IMPROVEMENT_PLAN.md`。

---

## 一、背景与现状

### 1.1 已完成的基线（无需重做）
- 规则创作：中文脚本、卡片画布、决策表、分支评审、版本历史、场景测试
- 可观测：行级业务日志、traceId 贯穿、执行时间轴、调用拓扑、完整 OpenTelemetry
- 平台治理：RBAC（读公开写需登录）、H2 审计、环境标记、外部跳转深链
- 执行能力：历史重跑、保存前静态校验、执行对比、批量用例

### 1.2 现状的局限
| 维度 | 现状 | 目标 |
|---|---|---|
| 规则存储 | 平台私有 JSON 文件 | 标准化 `QL_RULE` 表，外部可读 |
| 规则消费方 | 只有平台页面 | 平台 + Java SDK + HTTP API |
| 函数扩展 | 改 `Express4Engine` 写死注册 | `@RuleFunction` 注解自动发现 |
| 参数定义 | 单个 `paramHint` 字符串 | 递归 `ParamDef`（中文名/类型/对象/数组）全链路 |
| 引擎依赖 | 深度绑定 Spring | 无 Spring 核心层，可独立嵌入 |
| 可观测范围 | 平台内执行 | 任何地方执行都有 traceId |

---

## 二、最终架构

```
规则生产(UI 画布) → QL_RULE 表(集中存储) → core 引擎(无 Spring)
                                               ├─ 平台自身执行（运行中心/定时任务）
                                               ├─ Java SDK 本地执行（外部项目 Maven/Gradle）
                                               └─ HTTP API 执行（跨语言）
```

### 模块结构（多模块 Gradle）
```
QlexpressScriptLog/
├── build.gradle / settings.gradle      (根，子模块配置)
├── rulelog-core/          ★ SDK 主体，无 Spring 纯 Java
│   ├── annotation/   @RuleFunction / @RuleParam
│   ├── function/     FunctionScanner（反射扫描注解）
│   ├── engine/       Express4Core / RuleEngine(builder) / RuleExecutor
│   ├── loader/       RuleLoader 接口 + File / Jdbc / Remote 实现 + 缓存TTL
│   ├── param/        ParamDef 递归模型 / ParamBinder / ParamValidator
│   ├── trace/        StepLogger / RuleTraceContext / TraceParent（内置）
│   ├── model/        RuleExecutionRecord / ServiceCallLog / StepLog / LineExecution
│   ├── util/         JsonUtils / ContextResolver / ContentHash / ErrorSummarizer
│   └── sql/          QL_RULE / QL_RULE_HISTORY 的 DDL（MySQL/PG/H2）
├── rulelog-platform/     现有 Spring Boot 应用，依赖 core
│   ├── controller/ service/ store/ config/ security/ aspect/
│   ├── express/      Express4Engine（改为组合 core 引擎 + 注解扫描）
│   └── frontend/     Vue 前端
└── （HTTP API 契约先并入 platform）
```

---

## 三、第一部分：平台化核心（P1，第一优先级）

> 目标：规则可被外部系统复用，达成"平台形态"

### P1-1 多模块 Gradle 改造
- 建 `rulelog-core` / `rulelog-platform` 子模块，`settings.gradle` include
- 依赖调整：core 只依赖 QLExpress + Jackson（OTel 可选）
- **验收**：`./gradlew build` 通过，现有功能不回归

### P1-2 core 模型层搬迁（无 Spring）
- 搬迁：执行记录模型 / ServiceCallLog / StepLog / LineExecution / JsonUtils / ContentHash / ErrorSummarizer / TraceParent
- 新增 **ParamDef 递归模型**：`name/label/type/desc/required/fields(object 子字段)/element(array 元素)`
- **验收**：模型单测通过

### P1-3 @RuleFunction + @RuleParam 注解 + FunctionScanner
```java
@Target(METHOD) @Retention(RUNTIME)
public @interface RuleFunction {
    String name();
    String desc() default "";
    RuleParam[] params() default {};
}
@Target({}) @Retention(RUNTIME)
public @interface RuleParam {
    String name(); String label();
    String type() default "string";  // string/number/boolean/object/array
    String desc() default ""; boolean required() default true;
    RuleParam[] fields() default {}; // object 子字段（递归）
    RuleParam element() default @RuleParam(name="", label=""); // array 元素
}
```
- `FunctionScanner` 反射扫描注解 → 构建 `FunctionMeta`（含 ParamDef 树），无 Spring
- **验收**：注解解析用例（含对象嵌套 + 数组递归）

### P1-4 core 引擎层抽取（去 Spring）
- 拆出 `Express4Core`（构造函数传函数注册表，去 `@Component`）
- 拆出 `RuleExecutor`（execute + 日志聚合，去 `@Autowired`/`@Value`）
- **traceId 生成 + StepLogger 采集内置到 core**；OTel 做成**可选桥接**（SPI/可选模块，不强依赖）
- **验收**：core 脱离 Spring 可独立执行规则

### P1-5 参数绑定器（Map → Java 实参）
- `ParamBinder`：按 `ParamDef` 把嵌套 Map/JSON 转成方法实参（对象反序列化、数组展开）
- 处理 string/number/boolean/object/array 各类型 + 嵌套
- **验收**：各类型绑定用例

### P1-6 platform 改依赖 core + 注解化改造
- `Express4Engine` 改组合 core 引擎 + 注解扫描注册（删手写注册代码）
- `ServiceCallAspect` 优先读方法上 `@RuleFunction.name()` 拿中文名
- `/api/functions` 下发嵌套 params（无注解的用 paramHint 兜底）
- **验收关卡**：全量回归（前端 183 + 后端测试 + 端到端冒烟），不过不继续

### P1-7 QL_RULE 表 + DDL
```sql
QL_RULE (
  RULE_ID VARCHAR(64) PK, RULE_NAME VARCHAR(128), RULE_CONTENT CLOB,
  PARAM_DEF CLOB, VERSION INT, ENABLED BOOLEAN, DESCRIPTION VARCHAR(256),
  CREATED_AT BIGINT, UPDATED_AT BIGINT
)
QL_RULE_HISTORY (ID PK, RULE_ID, VERSION, RULE_CONTENT, SAVED_AT)
```
- MySQL / PostgreSQL / H2 三套 DDL，表前缀可配
- **验收**：建表 + 存取 round-trip

### P1-8 RuleLoader + 缓存
- `RuleLoader` 接口：`loadContent(ruleId)` + `loadContract(ruleId)`
- `FileRuleLoader`（ClassPath/文件）、`JdbcRuleLoader`（QL_RULE 表）、`RemoteRuleLoader`（平台 API，可选）
- **缓存 + TTL**（默认 30s 过期重查）+ 手动 `refresh(ruleId)`
- **验收**：各 loader 加载用例 + 缓存失效用例

### P1-9 RuleEngine builder + 参数校验
- `RuleEngine.builder().loader(..).scanFunctions(beans).build()`
- `ParamValidator` 递归校验（必填/类型/对象子字段/数组元素，错误定位到字段路径）
- 结果模型 `Result { value, traceId, success, error }`
- **验收**：SDK 端到端单测

### P1-10 平台规则同步到 QL_RULE + 数据迁移
- 平台保存规则时同步写入 QL_RULE 表
- 迁移工具：`scripts.json` → QL_RULE（一次性，启动时或脚本）
- **验收**：同步用例

### P1-11 HTTP API（对外）

| API | 作用 |
|---|---|
| `GET /api/public/rules` | 规则列表 |
| `GET /api/public/rules/{id}/contract` | 参数契约（含中文名/嵌套） |
| `POST /api/public/run` | 执行 → `{success, result, traceId}` |

- 执行类公开访问，入参按 ParamDef 校验
- **验收**：集成测试

### P1-12 前端参数渲染改造
- `ParamInput.vue` **递归组件**：string/number 输入、boolean 勾选、object 折叠字段组、array 增删行
- 取数卡用 ParamDef 渲染，每个参数显示中文 label + desc 占位
- 无 ParamDef 的函数保持现有单框（向后兼容）
- **验收**：配置一个嵌套参数函数

### P1-13 JitPack + 文档 + 外部 demo
- `jitpack.yml`（JDK17），多模块发布 core
- 文档：SDK 用法 / 表结构 / API / 注解
- 外部 demo 工程：Gradle 引入 SDK → JdbcRuleLoader 连表 → 执行 → 拿 result + traceId
- **验收**：demo 跑通

### P1-14 全量回归 + 发版
- 前端 183 + 后端全绿，CI 通过，打 tag 发版

---

## 四、第二部分：比对规则画布（P2，第二优先级）

> 目标：业务人员可视化配置多数据源比对/加工规则

| 批次 | 内容 |
|---|---|
| P2-1 | 比对画布模型：取数节点（函数/脚本规则）+ 比对节点（key+字段+方式）+ 结果节点 |
| P2-2 | CompareEngine + 比对策略（相等/数值容差/包含/大小）+ DAG 执行 + 单测 |
| P2-3 | 比对 API（CRUD + run） |
| P2-4 | CompareRecord 模型 + JSON 持久化 + 轮转 + 历史重跑（context 快照） |
| P2-5 | 前端比对画布（节点拖拽 + 配置面板，复用现有拖拽代码） |
| P2-6 | 运行页（上下文入参 + 日期范围 + 执行 + 定时任务 + 并排结果表格差异高亮） |
| P2-7 | 文档 + 发版 |

**依赖**：P1 的参数模型 / 注册机制 / HTTP API。

---

## 五、第三部分：小改进（P3，可穿插）
| 项 | 内容 | 工作量 |
|---|---|---|
| P3-1 | 试跑按钮（编辑页用当前内容+参数立即执行，不落正式记录） | 小 |
| P3-2 | 比对结果"只看差异"切换（P2 一部分） | 小 |

---

## 六、明确不做 / 暂缓
- ❌ IDE 式断点（QLExpress 不支持，与业务人员定位冲突）
- ⏸ 规则间调用（规则组合复用）
- ⏸ 灰度发布（部分实例用旧版本）
- ⏸ iframe / JS SDK（先做 HTTP API + Maven SDK）
- ⏸ 条件分支节点、cron 表达式、异步回调、外部接口取数

---

## 七、风险与对策
| 风险 | 对策 |
|---|---|
| P1-4/P1-6 重构破坏现有平台 | P1-6 设全量回归关卡，不过不继续 |
| 依赖版本冲突（Jackson/QLExpress/OTel） | core 显式版本 + 有条件依赖，OTel 可选 |
| JitPack 多模块发布配置坑 | P1-13 用外部 demo 实际引入验证 |
| ThreadLocal 日志清理（SDK 场景） | RuleExecutor 保证 drainAndRemove 在 finally |
| 规则缓存与实时性 | TTL + 手动 refresh，文档说明 |

---

## 八、优先级建议
1. **本期先做 P1（平台化核心）**：质变最大、价值最高，达成"平台形态"
2. **P2（比对画布）下一期**：功能增量，依赖 P1 基础
3. **P3 穿插**：试跑可提前到 P1-12 一起做

---

## 九、验收总标准
- 现有平台功能零回归
- 外部 demo 工程：Gradle 引入 SDK → JdbcRuleLoader 连表 → 执行规则 → 拿 result + traceId
- HTTP API：curl 调 `/api/public/run` 拿极简返回
- 注解：给 Service 加 `@RuleFunction` 后，画布下拉自动出现该中文函数及其参数定义
