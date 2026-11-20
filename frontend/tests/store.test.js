// store 冒烟测试：布局壳重构后，store 模块必须能独立加载且导出完整
// 页面组件（ScriptsPage/ExecutionsPage/HelpPage）与 App.vue 壳都从这里取状态与动作
import { describe, it, expect } from "vitest";
import * as store from "../src/store.js";

describe("store.js（页面共享状态单例）", () => {
  it("核心状态 ref 可用且有初始值", () => {
    expect(store.view.value).toBe("scripts");
    expect(Array.isArray(store.scripts.value)).toBe(true);
    expect(Array.isArray(store.executions.value)).toBe(true);
    expect(store.editing.value).toBe(false);
    expect(store.canvasMode.value).toBe(false);
  });

  it("goView 切换页面视图", () => {
    store.goView("executions");
    expect(store.view.value).toBe("executions");
    store.goView("scripts");
    expect(store.view.value).toBe("scripts");
  });

  it("页面壳需要的动作都已导出（防重构漏导出）", () => {
    const actions = [
      "loadScripts", "loadExecutions", "loadFunctionRegistry",
      "selectScript", "newScript", "saveScript", "runScript", "deleteScript",
      "importScriptFromFile", "exportCurrentScript",
      "selectExecution", "enterCanvas", "addCard", "moveCard", "duplicateCard",
      "logout", "openLogin", "submitLogin", "cancelLogin"  // 认证动作（含退出登录）
    ];
    for (const a of actions) {
      expect(typeof store[a], `导出 ${a}`).toBe("function");
    }
  });

  it("页面模板需要的计算属性与数据都已导出", () => {
    const computedAndData = [
      "visibleScripts", "visibleExecutions", "scriptKeyword", "execKeyword", "execStatus",
      "historyVersions", "registryFunctions", "CARD_TYPES", "form", "scriptLines", "canvasCards",
      "isLoggedIn", "currentUsername"  // 侧边栏登录状态
    ];
    for (const k of computedAndData) {
      expect(store[k], `导出 ${k}`).toBeDefined();
    }
  });
});
