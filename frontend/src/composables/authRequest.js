/**
 * 认证请求辅助（纯逻辑，可单测）：Basic 凭据管理 + Authorization 头 + 401 识别。
 * 真实请求仍走 apiClient.requestJson；本模块只负责生成头、保存凭据、判断是否需要登录。
 */

/** 生成 Basic Auth 头值 */
export const basicHeader = (username, password) =>
  "Basic " + (typeof btoa === "function"
    ? btoa(`${username}:${password}`)
    : Buffer.from(`${username}:${password}`).toString("base64"));

/** 识别 401 错误 */
export const isUnauthorized = (err) => !!err && err.status === 401;

/**
 * 校验凭据：用给定的 requestJson + auth 请求受保护接口。
 * 抽成纯函数以便测试"校验必须携带认证头"这一关键行为。
 * @param requestJson 请求函数
 * @param auth 凭据对象（需有 headers()）
 * @param url 受保护接口地址
 * @returns true 凭据有效；false 凭据错误（401）
 */
export const verifyCredentials = async (requestJson, auth, url) => {
  try {
    await requestJson(url, { headers: auth.headers() });
    return true;
  } catch (e) {
    if (isUnauthorized(e)) return false;
    throw e; // 非 401（网络等）继续抛出
  }
};

/**
 * 创建凭据状态闭包（存内存 + sessionStorage 可选持久化）。
 * @param {object} storage 可选的 Storage（测试可注入内存实现；默认 sessionStorage）
 */
export const createAuth = (storage) => {
  const store = storage || (typeof sessionStorage !== "undefined" ? sessionStorage : null);
  const KEY = "rulelog-auth";
  let creds = null;
  // 从 storage 恢复
  try {
    const saved = store && store.getItem(KEY);
    if (saved) creds = JSON.parse(saved);
  } catch (e) { /* ignore */ }

  return {
    set(username, password) {
      creds = { username, password };
      try { store && store.setItem(KEY, JSON.stringify(creds)); } catch (e) { /* ignore */ }
    },
    clear() {
      creds = null;
      try { store && store.removeItem(KEY); } catch (e) { /* ignore */ }
    },
    get() { return creds; },
    hasCredentials() { return !!(creds && creds.username); },
    headers() {
      return creds && creds.username ? { Authorization: basicHeader(creds.username, creds.password) } : {};
    }
  };
};
