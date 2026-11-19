<template>
  <div class="toast-host" aria-live="polite">
    <div
      v-for="t in toasts"
      :key="t.id"
      class="toast"
      :class="'toast-' + t.type"
      role="status"
      @click="dismiss(t.id)"
    >
      <span class="toast-icon">{{ icon(t.type) }}</span>
      <span class="toast-msg">{{ t.message }}</span>
    </div>
  </div>
</template>

<script setup>
import { toasts, dismissToast, notify } from "../store.js";

const dismiss = (id) => dismissToast(id);

const icon = (type) => ({
  success: "✅", error: "⛔", warning: "⚠️", info: "ℹ️"
}[type] || "ℹ️");

// 暴露 notify 供模板外使用（实际调用走 store.notify）
defineExpose({ notify });
</script>

<style scoped>
.toast-host {
  position: fixed; top: 18px; right: 22px; z-index: 100;
  display: flex; flex-direction: column; gap: 8px; pointer-events: none;
}
.toast {
  pointer-events: auto; cursor: pointer;
  display: flex; align-items: center; gap: 9px;
  min-width: 220px; max-width: 420px;
  padding: 10px 14px; border-radius: 10px;
  background: #fff; border: 1px solid var(--ink-200, #e6e9ef);
  box-shadow: 0 6px 22px rgba(16, 24, 40, 0.14);
  font-size: 13px; color: var(--ink-900, #1a2030);
  animation: toast-in 0.28s cubic-bezier(0.32, 0.72, 0, 1);
}
.toast-icon { font-size: 15px; flex: none; }
.toast-msg { flex: 1; line-height: 1.5; }
.toast-success { border-left: 3px solid var(--ok, #0f8a63); }
.toast-error { border-left: 3px solid var(--bad-text, #c0392b); }
.toast-warning { border-left: 3px solid #d97706; }
.toast-info { border-left: 3px solid var(--accent, #4353c9); }
@keyframes toast-in {
  from { opacity: 0; transform: translateX(24px); }
  to { opacity: 1; transform: translateX(0); }
}
@media (prefers-reduced-motion: reduce) {
  .toast { animation: none; }
}
</style>
