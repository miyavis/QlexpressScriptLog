<template>
  <div class="page help-page">
    <div class="help-card">
      <h2>中文脚本怎么写</h2>
      <p class="hint">脚本从上到下按顺序执行；变量不用声明，赋值即定义；返回用「返回 表达式」。</p>
      <table class="help-table">
        <thead><tr><th>中文写法</th><th>含义</th></tr></thead>
        <tbody>
          <tr><td>如果 (条件) 则 { … } 否则 { … }</td><td>条件分支，右大括号单独一行结束</td></tr>
          <tr><td>等于 / 不等于 / 大于 / 小于</td><td>比较运算（也可用 ==、!=、&gt;、&lt;）</td></tr>
          <tr><td>并且 / 或者</td><td>逻辑组合（&amp;&amp;、||）</td></tr>
          <tr><td>返回 表达式</td><td>结束执行并返回结果</td></tr>
          <tr><td>记录日志("步骤名", "详情")</td><td>业务打点，出现在执行日志里</td></tr>
        </tbody>
      </table>
    </div>

    <div class="help-card">
      <h2>平台可调用的服务函数</h2>
      <p class="hint">来自后端注册表（新增业务 Service 注册一次即自动出现）；卡片画布的函数下拉与这里一致。</p>
      <table class="help-table">
        <thead><tr><th>函数</th><th>对应 Java 方法</th><th>参数</th></tr></thead>
        <tbody>
          <tr v-for="f in registryFunctions" :key="f.name">
            <td class="fn-name">{{ f.name }}</td>
            <td>{{ f.service }}.{{ f.method }}</td>
            <td>{{ f.paramHint || "—" }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="help-card">
      <h2>卡片画布怎么用</h2>
      <p class="hint">拖入部件或点击添加 → 按顺序执行；生成的脚本文本与画布始终保持双向互转。</p>
      <table class="help-table">
        <thead><tr><th>卡片</th><th>作用</th></tr></thead>
        <tbody>
          <tr v-for="t in CARD_TYPES" :key="t.type">
            <td>{{ t.icon }} {{ t.label }}</td>
            <td>{{ t.hint }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="help-card">
      <h2>看不懂执行日志？</h2>
      <ul class="hint ul">
        <li>每行脚本对应一段日志（悬停/点击互相高亮），顶部显示入参。</li>
        <li>五类业务日志：❓条件判断 · 🔧服务调用 · 📊变量赋值 · 📝业务打点 · 🎯返回。</li>
        <li>执行失败时显示中文摘要：第几行出错 + 错误类别 + 出错行内容（原始英文信息在末尾备查）。</li>
        <li>改坏脚本不慌：编辑区「🕘 历史版本」随时回滚，每次保存都自动留快照。</li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { registryFunctions, CARD_TYPES } from "../store.js";
</script>

<style scoped>
.help-page { flex-direction: column; gap: 16px; }
.help-card {
  border: 1px solid rgba(16, 24, 40, 0.07); border-radius: var(--r-xl, 16px); background: #fff;
  padding: 20px 24px; box-shadow: var(--shadow-card, none), var(--halo, none);
  animation: rise-in 0.55s cubic-bezier(0.32, 0.72, 0, 1) backwards;
}
.help-card:nth-child(2) { animation-delay: 0.06s; }
.help-card:nth-child(3) { animation-delay: 0.12s; }
.help-card:nth-child(4) { animation-delay: 0.18s; }
.help-table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 13px; }
.help-table th, .help-table td { border: 1px solid var(--ink-200, #e6e9ef); padding: 8px 12px; text-align: left; }
.help-table th { background: var(--ink-50, #f7f8fa); font-size: 12px; color: var(--ink-500, #5f6b7e); font-weight: 600; }
.help-table .fn-name { font-weight: 600; white-space: nowrap; }
.ul { padding-left: 18px; margin-top: 6px; }
.ul li { margin-bottom: 4px; }
</style>
