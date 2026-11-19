// 认证请求封装测试：Basic 凭据管理 + Authorization 头 + 401 识别
import { describe, it, expect, beforeEach } from "vitest";
import { createAuth, basicHeader, isUnauthorized } from "../src/composables/authRequest.js";

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
