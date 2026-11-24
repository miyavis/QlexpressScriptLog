# rulelog-core 外部接入 Demo

演示外部 Java 项目如何引入 SDK、从 `QL_RULE` 表加载规则、执行并拿到 `result` + `traceId`。

## 运行

```bash
# 先把 core 发布到本地 Maven 仓库（JitPack 发布需仓库 owner 确认）
./gradlew :rulelog-core:publishToMavenLocal
# 运行 demo
./gradlew -p examples/sdk-demo run
```

预期输出：

```
结果: 200
traceId: XXXXXXXXXXXXXXXX
✅ SDK 外部接入验证通过
```

## 发布到 JitPack 后的依赖写法（需确认发布）

```gradle
repositories { maven { url 'https://jitpack.io' } }
dependencies { implementation 'com.github.miyavis:rulelog-core:v1.1.6' }
```

## 关键代码

```java
JdbcRuleLoader loader = JdbcRuleLoader.builder()
        .dataSource(dataSource)      // 任意 javax.sql.DataSource
        .cacheTtlMillis(30_000)
        .build();
RuleEngine engine = RuleEngine.builder().loader(loader).build();

Result r = engine.runById("points_double", Map.of("积分", 100));
r.getValue();    // 200
r.getTraceId();  // 回平台下钻完整执行日志
```

规则也可来自文件：`FileRuleLoader.fromDirectory("rules/")` 或 `.fromClassPath("rules/")`。
业务函数可用 `RuleEngine.builder().scanFunctions(bean)` 扫描 `@RuleFunction` 注解自动注册元信息。
