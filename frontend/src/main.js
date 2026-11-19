import { createApp } from "vue";
import App from "./App.vue";

// 设计系统：令牌 → 布局壳与通用原语 → 页面专属样式（顺序不可换）
import "./styles/tokens.css";
import "./styles/base.css";
import "./styles/scripts.css";
import "./styles/executions.css";

createApp(App).mount("#app");
