// API 封装单元测试（TDD）：统一 res.ok 检查 + 后端 message 解析 + token 头
import { describe, it, expect, vi } from "vitest";
import { makeHeaders, parseErrorMessage, requestJson } from "../src/composables/apiClient.js";

/** 构造一个最小 Response 替身（node 环境无全局 Response） */
const fakeRes = (status, body) => ({
  status,
  ok: status >= 200 && status < 300,
  text: async () => body
});

describe("makeHeaders（统一请求头，含可选 token）", () => {
  it("无 token 时只带 JSON 头", () => {
    expect(makeHeaders(undefined, "application/json")).toEqual({ "Content-Type": "application/json" });
  });
  it("有 token 时附带 X-Api-Token", () => {
    expect(makeHeaders("secret", "application/json")).toEqual({
      "Content-Type": "application/json",
      "X-Api-Token": "secret"
    });
  });
});

describe("parseErrorMessage（从错误响应体提取人看得懂的消息）", () => {
  it("解析后端 {message} JSON", async () => {
    expect(await parseErrorMessage(fakeRes(400, JSON.stringify({ message: "脚本名称不能为空" }))))
      .toBe("脚本名称不能为空");
  });
  it("非 JSON 响应回退到文本", async () => {
    expect(await parseErrorMessage(fakeRes(500, "plain error"))).toBe("plain error");
  });
  it("空响应回退到 HTTP 状态", async () => {
    expect(await parseErrorMessage(fakeRes(503, ""))).toBe("HTTP 503");
  });
});

describe("requestJson（统一请求）", () => {
  it("成功时返回解析后的 JSON", async () => {
    const fetchMock = vi.fn().mockResolvedValue(fakeRes(200, JSON.stringify({ ok: true })));
    const data = await requestJson("/api/x", {}, fetchMock, "");
    expect(data).toEqual({ ok: true });
  });

  it("失败时抛出带后端 message 的错误（不把错误 JSON 当成功数据）", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      fakeRes(400, JSON.stringify({ message: "脚本内容不能为空" }))
    );
    await expect(requestJson("/api/x", {}, fetchMock, "")).rejects.toThrow("脚本内容不能为空");
  });

  it("错误携带 HTTP status（供 401 登录拦截识别）", async () => {
    const fetchMock = vi.fn().mockResolvedValue(fakeRes(401, "Unauthorized"));
    try {
      await requestJson("/api/x", {}, fetchMock, "");
      throw new Error("should have thrown");
    } catch (e) {
      expect(e.status).toBe(401);
    }
  });

  it("204 无内容返回 null", async () => {
    const fetchMock = vi.fn().mockResolvedValue(fakeRes(204, ""));
    expect(await requestJson("/api/x", {}, fetchMock, "")).toBeNull();
  });

  it("透传 AbortSignal 与 token 头", async () => {
    const fetchMock = vi.fn().mockResolvedValue(fakeRes(200, "{}"));
    const controller = new AbortController();
    await requestJson("/api/x", { signal: controller.signal }, fetchMock, "tok");
    const [, opts] = fetchMock.mock.calls[0];
    expect(opts.signal).toBe(controller.signal);
    expect(opts.headers["X-Api-Token"]).toBe("tok");
  });
});
