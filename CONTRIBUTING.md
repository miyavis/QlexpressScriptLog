# 贡献指南

感谢你关注本项目！欢迎提 Issue 和 Pull Request。

## 开发环境

- JDK 17（Spring Boot 3.2，构建用 Gradle Wrapper 7.6.4，无需本机安装 Gradle）
- Node.js 16+ + npm

## 本地开发

```bash
# 后端（Spring Boot，监听 8080）
./gradlew bootRun

# 前端（Vite 开发服务器，监听 5173，/api 代理到 8080）
cd frontend
npm install
npm run dev
```

## 提交前必做

```bash
./gradlew test                    # 后端单测（22 个用例）
cd frontend && npm run test       # 前端单测（83 个用例）
cd frontend && npm run build      # 前端构建通过
```

三项全绿再提交，CI 会自动跑同样的检查。

## 约定

- **新功能走 TDD**：先写失败的测试，再写实现
- **前端纯逻辑**放 `frontend/src/composables/`（可单测），页面组件放 `frontend/src/pages/`，共享状态放 `frontend/src/store.js`
- **提交信息用中文**，一句话说清改了什么、为什么
- **不要提交** `data/scripts.json`（本地业务数据，已被 gitignore）
- 涉及日志样式的改动注意保持业务语言五类日志格式不变

## 报告问题

请用 Issue 模板（Bug 报告 / 功能建议）。安全问题请勿公开提 Issue，见 [SECURITY.md](SECURITY.md)。

## 许可

提交即表示你的贡献遵循 [MIT](LICENSE) 发布。
