/**
 * Toast 通知队列（纯逻辑，无 Vue 依赖，可单测）。
 * 每条消息有 id/type/message，支持 push / dismiss / 自动过期。
 */

let seq = 0;

/** 新建一条通知 */
export const makeToast = (message, type = "info") => ({
  id: ++seq,
  message: String(message == null ? "" : message),
  type: ["success", "error", "warning", "info"].includes(type) ? type : "info"
});

/** 加入队列（返回新数组，保持不可变便于测试） */
export const pushToast = (list, toast) => [...list, toast];

/** 按 id 移除 */
export const dismissToast = (list, id) => list.filter((t) => t.id !== id);

/** 限制最大条数（超出丢弃最早的） */
export const trimToasts = (list, max = 4) =>
  list.length > max ? list.slice(list.length - max) : list;
