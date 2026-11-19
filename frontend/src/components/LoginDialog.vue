<template>
  <div v-if="showLoginDialog" class="login-overlay" @click.self="cancelLogin">
    <div class="login-box" role="dialog" aria-modal="true" aria-label="登录">
      <h3>🔐 需要登录</h3>
      <p class="hint">{{ loginReason || '该操作需要登录后才能执行。' }}</p>
      <form @submit.prevent="submitLogin">
        <label>账号
          <input v-model="loginUser" class="card-input" autocomplete="username" autofocus placeholder="admin" />
        </label>
        <label>密码
          <input v-model="loginPass" type="password" class="card-input" autocomplete="current-password" placeholder="密码" />
        </label>
        <p v-if="loginError" class="login-error">{{ loginError }}</p>
        <div class="login-actions">
          <button type="button" class="btn-secondary" @click="cancelLogin">取消</button>
          <button type="submit" class="btn-primary" :disabled="loggingIn">{{ loggingIn ? '登录中…' : '登录' }}</button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { showLoginDialog, loginReason, loginUser, loginPass, loginError, loggingIn, submitLogin, cancelLogin } from "../store.js";
</script>

<style scoped>
.login-overlay { position: fixed; inset: 0; z-index: 200; background: rgba(16, 24, 40, 0.45); display: flex; align-items: center; justify-content: center; }
.login-box { width: 340px; max-width: 90vw; background: #fff; border-radius: 14px; padding: 24px; box-shadow: 0 12px 40px rgba(16,24,40,.25); }
.login-box h3 { margin: 0 0 6px; font-size: 17px; }
.login-box .hint { margin: 0 0 16px; font-size: 12.5px; color: var(--ink-500); }
.login-box label { display: block; font-size: 12.5px; color: var(--ink-700); margin-bottom: 10px; font-weight: 600; }
.login-box label input { display: block; margin-top: 4px; font-weight: 400; }
.login-error { color: var(--bad-text); font-size: 12px; margin: 0 0 10px; }
.login-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 16px; }
</style>
