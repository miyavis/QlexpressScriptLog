import { describe, it, expect } from "vitest";
import { makeToast, pushToast, dismissToast, trimToasts } from "../src/composables/toast.js";

describe("toast 队列纯逻辑", () => {
  it("makeToast 生成唯一 id 与归一化 type", () => {
    const a = makeToast("成功", "success");
    const b = makeToast("错误", "error");
    expect(a.id).not.toBe(b.id);
    expect(a.type).toBe("success");
    expect(makeToast("x", "bogus").type).toBe("info");
    expect(makeToast(null).message).toBe("");
  });

  it("push / dismiss 不可变", () => {
    const list = [];
    const l1 = pushToast(list, makeToast("a"));
    expect(list).toHaveLength(0);
    expect(l1).toHaveLength(1);
    const l2 = dismissToast(l1, l1[0].id);
    expect(l2).toHaveLength(0);
  });

  it("trim 超出保留最新", () => {
    const list = [1, 2, 3, 4, 5].map((n) => ({ id: n }));
    expect(trimToasts(list, 3).map((t) => t.id)).toEqual([3, 4, 5]);
    expect(trimToasts([1, 2], 3)).toEqual([1, 2]);
  });
});
