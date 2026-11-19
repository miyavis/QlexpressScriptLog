// 认证请求封装测试：Basic 凭据管理 + Authorization 头 + 401 识别
import { describe, it, expect, beforeEach } from "vitest";
import { createAuth, basicHeader, isUnauthorized, verifyCredentials } from "../src/composables/authRequest.js";

describe("basicHeader（Basic Auth 头）", () => {
  it("生成标准 Basic base64 头", () => {
    // admin:admin123 → YWRtaW46YWRtaW4xMjM=
    expect(basicHeader("admin", "admin123")).toBe("Basic " + btoa("admin:admin123"));
  });
});

describe("createAuth（凭据状态）", () => {
  let auth;
  beforeEach(() => { auth = createAuth(); });

  it("初始无凭据", () => {
    expect(auth.hasCredentials()).toBe(false);
    expect(auth.headers()).toEqual({});
  });

  it("设置凭据后生成头，清除后还原", () => {
    auth.set("admin", "admin123");
    expect(auth.hasCredentials()).toBe(true);
    expect(auth.headers()["Authorization"]).toBe(basicHeader("admin", "admin123"));
    auth.clear();
    expect(auth.hasCredentials()).toBe(false);
    expect(auth.headers()).toEqual({});
  });
});

describe("isUnauthorized（识别 401）", () => {
  it("status 401 返回 true", () => {
    expect(isUnauthorized({ status: 401 })).toBe(true);
    expect(isUnauthorized({ status: 403 })).toBe(false);
    expect(isUnauthorized(null)).toBe(false);
  });
});

describe("verifyCredentials（校验必带认证头）", () => {
  it("正确凭据 → true，且请求携带 Authorization", async () => {
    const auth = createAuth();
    auth.set("admin", "admin123");
    let seenHeaders;
    const requestJson = async (url, opts) => { seenHeaders = opts.headers; };
    expect(await verifyCredentials(requestJson, auth, "/api/auth/status")).toBe(true);
    expect(seenHeaders.Authorization).toBe(basicHeader("admin", "admin123"));
  });

  it("错误凭据（401）→ false", async () => {
    const auth = createAuth();
    auth.set("admin", "wrong");
    const requestJson = async () => { const e = new Error("unauthorized"); e.status = 401; throw e; };
    expect(await verifyCredentials(requestJson, auth, "/api/auth/status")).toBe(false);
  });

  it("非 401 异常继续抛出", async () => {
    const auth = createAuth();
    const requestJson = async () => { throw new Error("network"); };
    await expect(verifyCredentials(requestJson, auth, "/api/auth/status")).rejects.toThrow("network");
  });
});
