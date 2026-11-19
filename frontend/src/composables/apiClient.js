/**
 * 统一 API 请求封装（纯逻辑，可单测）
 *
 * 解决三件事：
 * 1. 所有请求强制检查 res.ok——错误响应不再被当成数据塞进 state
 * 2. 后端 4xx/5xx 返回的 { message } 提取成人看得懂的错误
 * 3. 统一附带可选 X-Api-Token（与后端 ApiTokenFilter 对应），并透传 AbortSignal 做竞态取消
 */

/** 生成请求头：JSON content-type + 可选 token */
export const makeHeaders = (token, contentType = "application/json") => {
  const headers = { "Content-Type": contentType };
  if (token) {
    headers["X-Api-Token"] = token;
  }
  return headers;
};

/** 从错误响应体解析错误消息：优先后端 {message}，其次是纯文本，最后是 HTTP 状态 */
export const parseErrorMessage = async (res) => {
  let text = "";
  try {
    text = await res.text();
  } catch (e) {
    return `HTTP ${res.status}`;
  }
  if (text) {
    try {
      const obj = JSON.parse(text);
      if (obj && typeof obj.message === "string" && obj.message) {
        return obj.message;
      }
    } catch (e) {
      // 非 JSON，回退到原始文本
    }
    return text;
  }
  return `HTTP ${res.status}`;
};

/**
 * 发起请求并解析 JSON。
 * @param {string} url 请求地址
 * @param {object} fetchOptions fetch 选项（method/body/signal 等）
 * @param {function} fetchImpl fetch 实现（测试注入；运行时用全局 fetch）
 * @param {string} token 可选 API token
 * @returns 解析后的 JSON；204 返回 null
 * @throws 带后端 message 的 Error
 */
export const requestJson = async (url, fetchOptions = {}, fetchImpl = fetch, token = "") => {
  const opts = {
    ...fetchOptions,
    headers: { ...makeHeaders(token), ...(fetchOptions.headers || {}) }
  };
  const res = await fetchImpl(url, opts);
  if (!res.ok) {
    throw new Error(await parseErrorMessage(res));
  }
  if (res.status === 204) {
    return null;
  }
  // 空 body（如某些 DELETE 200）返回 null，避免 JSON.parse 抛错
  const text = await res.text();
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch (e) {
    throw new Error("服务器返回了无法解析的响应");
  }
};
