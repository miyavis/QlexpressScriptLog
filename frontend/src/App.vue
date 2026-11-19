<template>
  <div class="app">
    <!-- 布局壳：左侧菜单 + 右侧内容区（页面组件见 pages/，样式见 styles/） -->
    <aside class="side-nav">
      <div class="brand">
        <div class="brand-icon">🎴</div>
        <div>
          <div class="brand-name">规则执行平台</div>
          <div class="brand-sub">QLExpress4 · 中文脚本</div>
        </div>
      </div>
      <nav class="nav-menu">
        <button :class="{ active: view === 'scripts' }" @click="goView('scripts')"><span class="nav-ico">📜</span>脚本管理<span class="nav-badge">{{ scripts.length }}</span></button>
        <button :class="{ active: view === 'executions' }" @click="goView('executions')"><span class="nav-ico">📊</span>执行记录<span class="nav-badge">{{ executions.length }}</span></button>
        <button :class="{ active: view === 'help' }" @click="goView('help')"><span class="nav-ico">❓</span>使用帮助</button>
      </nav>
      <div class="nav-foot">JDK 8 · Spring Boot 2.7.18<br />Vite 4 · Vue 3.4</div>
    </aside>
    <div class="app-body">
      <main class="app-main">
        <ScriptsPage v-if="view === 'scripts'" />
        <ExecutionsPage v-else-if="view === 'executions'" />
        <HelpPage v-else />
      </main>
      <footer class="app-footer">
        后端 Spring Boot 3.2 + QLExpress 4.1.4 (JDK 17) · 前端 Vite 4 + Vue 3.4 · traceId 贯穿，实现脚本与日志一一对应
      </footer>
    </div>
    <ToastHost />
  </div>
</template>

<script setup>
import { onMounted } from "vue";
import ToastHost from "./components/ToastHost.vue";
import { view, goView, scripts, executions, loadScripts, loadExecutions, loadFunctionRegistry } from "./store.js";
import ScriptsPage from "./pages/ScriptsPage.vue";
import ExecutionsPage from "./pages/ExecutionsPage.vue";
import HelpPage from "./pages/HelpPage.vue";

// 页面初始化：壳挂载后统一拉数据（store 里不再有生命周期）
onMounted(() => {
  loadScripts();
  loadExecutions();
  loadFunctionRegistry();
});
</script>
