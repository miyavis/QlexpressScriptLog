# 安全策略

## 报告漏洞

如果发现安全问题，**请勿在公开 Issue 中描述细节**，请通过以下方式私下报告：

- GitHub 私密报告：仓库页 → **Security** 标签 → **Report a vulnerability**（Private vulnerability reporting）

请在报告中包含：

- 影响的版本 / 提交
- 复现步骤
- 影响评估（能造成什么后果）

我会在 **72 小时内**确认收到，并在确认后尽快跟进修复与发布补丁版本。

## 安全设计现状（供评估参考）

- 零第三方依赖面：后端仅 `spring-boot-starter-web/aop/test` + `qlexpress4`，前端无运行时依赖
- **本工具定位为开发/验证工具，默认无鉴权**——脚本可以调用 Spring 容器内任意 Service 方法，请勿将未鉴权的实例直接暴露到不可信网络
- 脚本与执行记录落盘/内存的行为见 README「脚本文件路径」章节
