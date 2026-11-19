<template>
  <div class="page help-page">
    <div class="help-card">
      <h2>中文脚本怎么写</h2>
      <p class="hint">脚本从上到下按顺序执行；变量不用声明，赋值即定义；返回用「返回 表达式」。</p>
      <table class="help-table">
        <thead><tr><th>中文写法</th><th>含义</th></tr></thead>
        <tbody>
          <tr><td>如果 (条件) 则 { … } 否则 { … }</td><td>条件分支，右大括号单独一行结束</td></tr>
          <tr><td>等于 / 不等于 / 大于 / 小于 / 大于等于 / 小于等于</td><td>比较运算（也可用 ==、!=、&gt;、&lt;、≥、≤、≠）</td></tr>
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
      <p class="hint">拖入部件或点击添加 → 按顺序执行；生成的脚本文本与画布保持双向互转。</p>
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
      <h2>决策表：表格化批量规则</h2>
      <p class="hint">不想写一堆如果/否则？用决策表把规则列成表格，保存时自动展开成可执行脚本。</p>
      <table class="help-table">
        <thead><tr><th>命中策略</th><th>行为</th><th>适用场景</th></tr></thead>
        <tbody>
          <tr><td>First（首个命中）</td><td>从上到下匹配，第一行命中即赋值并跳出；可设默认值兜底</td><td>折扣档位、等级判定</td></tr>
          <tr><td>Collect（累加）</td><td>结果变量从默认值起步，每命中一行就累加结果值</td><td>计分、多条件加分</td></tr>
        </tbody>
      </table>
      <p class="hint note">每行填「条件列」（如 <code>&gt; 100</code>、<code>== "VIP"</code>）和结果值；可加减行列。注意：决策表生成的脚本展开为如果/否则，切回文本再回画布会显示为普通卡片。</p>
      <p class="hint note">💡 内置了一条「决策表演示（会员折扣）」脚本——打开它、切到画布模式即可看到真实的决策表卡，点「执行脚本」用默认参数 user=张三 试试（VIP + 积分380 命中首行，折扣 0.7）。</p>
    </div>

    <div class="help-card">
      <h2>看不懂执行日志？</h2>
      <ul class="hint ul">
        <li>每行脚本对应一段日志（悬停/点击互相高亮），顶部显示入参。</li>
        <li>五类业务日志：❓条件判断 · 🔧服务调用 · 📊变量赋值 · 📝业务打点 · 🎯返回。</li>
        <li>执行失败时显示中文摘要：第几行出错 + 错误类别 + 出错行内容（原始英文信息在末尾备查）。</li>
        <li>日志顶部的「时间轴」可逐步回放：点 ▶/◀ 按顺序高亮每一步对应的脚本行。</li>
        <li>执行记录页可切「拓扑」视图：按耗时占比着色，快速找出主导/失败的服务调用。</li>
        <li>每条记录带 W3C <code>traceparent</code> 与 traceId，可对接 OpenTelemetry / SkyWalking。</li>
      </ul>
    </div>

    <div class="help-card">
      <h2>版本 · 分支 · 评审 · 环境</h2>
      <ul class="hint ul">
        <li><b>版本</b>：每次保存自动留快照（含画布布局），最多 10 个，可对比/载入回滚；保存时可填备注说明"为什么改"。</li>
        <li><b>发布</b>：可把某版本标记为「已发布」（带徽标），区分草稿与线上版本。</li>
        <li><b>分支</b>：点「切换分支」，输入新名字即创建新分支；切分支后回到草稿。</li>
        <li><b>评审</b>：草稿 →「提交评审」→ 评审中 →「通过并发布」/「驳回」；修改已发布脚本会自动回草稿，需重新评审。</li>
        <li><b>环境</b>：脚本可标 dev / staging / prod，列表顶部按环境筛选。</li>
      </ul>
    </div>

    <div class="help-card">
      <h2>保存前检查与历史重跑</h2>
      <ul class="hint ul">
        <li><b>静态检查</b>：保存前自动查语法错误与未定义变量，提前拦截问题脚本（可强制保存）。</li>
        <li><b>历史重跑</b>：执行记录详情点「用此参数重跑」，沿用当时的参数 + 当前脚本再跑一次，生成新记录。</li>
        <li><b>导出</b>：任何记录/脚本可导出 JSON，发给他人即可离线复盘或导入。</li>
      </ul>
    </div>

    <div class="help-card">
      <h2>登录与权限</h2>
      <ul class="hint ul">
        <li>公开 Demo 采用<strong>读公开、写需登录</strong>：浏览脚本 / 执行记录 / 跑脚本无需登录。</li>
        <li>新建 / 修改 / 删除脚本、评审、发布、分支、清空记录等写操作会弹出登录框。</li>
        <li>演示账号：<code>admin / admin123</code>（管理员）或 <code>viewer / viewer123</code>（只读用户，也可写，仅审计日志不可见）。</li>
        <li>登录状态在当前浏览器会话内保持；脚本执行沙箱为空白名单，脚本无法访问 Java 类 / 系统资源。</li>
      </ul>
    </div>

    <div class="help-card">
      <h2>审计与安全</h2>
      <ul class="hint ul">
        <li>关键操作（新建/修改/删除/执行/评审/发布）记入审计日志（H2），脚本页「审计日志」可查看。</li>
        <li>脚本沙箱为空白名单，无法访问 Java 类/系统资源，杜绝脚本 RCE。</li>
        <li>公网部署可通过 <code>app.api-token</code> 或 Spring Security（admin/viewer）开启鉴权，默认关闭便于本地演示。</li>
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
.help-card:nth-child(5) { animation-delay: 0.24s; }
.help-card:nth-child(6) { animation-delay: 0.3s; }
.help-card:nth-child(7) { animation-delay: 0.36s; }
.help-card:nth-child(8) { animation-delay: 0.42s; }
.help-table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 13px; }
.help-table th, .help-table td { border: 1px solid var(--ink-200, #e6e9ef); padding: 8px 12px; text-align: left; }
.help-table th { background: var(--ink-50, #f7f8fa); font-size: 12px; color: var(--ink-500, #5f6b7e); font-weight: 600; }
.help-table .fn-name { font-weight: 600; white-space: nowrap; }
.ul { padding-left: 18px; margin-top: 6px; }
.ul li { margin-bottom: 5px; }
.ul b { color: var(--ink-900); }
.note { margin-top: 10px; }
code { font-family: var(--font-mono); background: var(--ink-100); color: var(--accent); padding: 1px 5px; border-radius: 4px; font-size: 12px; }
</style>
