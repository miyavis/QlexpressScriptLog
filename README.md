# QLExpress4 规则执行平台（附全链路可观测性）

[![CI](https://github.com/miyavis/QlexpressScriptLog/actions/workflows/ci.yml/badge.svg)](https://github.com/miyavis/QlexpressScriptLog/actions/workflows/ci.yml)
[![Release](https://github.com/miyavis/QlexpressScriptLog/actions/workflows/release.yml/badge.svg)](https://github.com/miyavis/QlexpressScriptLog/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

> **在线演示地址：** [https://splendid-mercy-production-9282fdcf.up.railway.app](https://splendid-mercy-production-9282fdcf.up.railway.app)

本项目演示如何在 QLExpress 4.1.3 规则执行过程中加入**可观测性**，面向业务人员：
- 生产环境**事后查看**，不暂停程序、不需要断点/单步
- 一条规则调用了哪些 Spring Service？Service 内部做了什么？日志全部可查
- 脚本与内部日志**一一对应**（traceId 贯穿），不会串号
- 支持**中文脚本**（如果/则/否则/返回/等于……），降低业务人员阅读门槛
- 天然兼容**卡片化创作**：脚本可由外部标记器/卡片画布转换生成，观测能力锚定脚本文本，无需感知脚本来源（见「卡片化脚本适配方案」章节）
- 脚本持久化为 **JSON 文件**，执行记录保存在内存（Demo 简化，重启清空）

---

## 部署到 Render

仓库根目录提供 [`render.yaml`](render.yaml)，可在 Render 控制台选择 **New → Blueprint** 并连接本仓库。

- 分支：`deploy/render-demo`
- 构建：构建 Vue 前端并打包成单个 Spring Boot Jar
- 启动：读取 Render 提供的 `$PORT`
- 健康检查：`/api/functions`
- 免费实例可能休眠；首次唤醒需要等待
- 重启或重新部署后，内存执行记录会清空，脚本文件可能随实例文件系统重置

---
