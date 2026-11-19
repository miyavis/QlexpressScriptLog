import { describe, it, expect } from "vitest";
import { pushHistory, popUndo, popRedo, canUndo, canRedo } from "../src/composables/historyStack.js";

describe("historyStack（撤销/重做栈）", () => {
  it("push 后能 undo 回到上一状态", () => {
    let h = { undo: [], redo: [], limit: 30 };
    h = pushHistory(h, ["a"]); // 变更前的快照
    expect(canUndo(h)).toBe(true);
    const prev = popUndo(h, ["a", "b"]); // 当前状态入 redo
    expect(prev.snapshot).toEqual(["a"]);
    expect(canRedo(prev.state)).toBe(true);
  });

  it("redo 恢复", () => {
    let h = { undo: [], redo: [], limit: 30 };
    h = pushHistory(h, ["a"]);
    const u = popUndo(h, ["a", "b"]);
    const r = popRedo(u.state);
    expect(r.snapshot).toEqual(["a", "b"]);
  });

  it("新操作清空 redo 栈", () => {
    let h = { undo: [], redo: [], limit: 30 };
    h = pushHistory(h, ["a"]);
    h = popUndo(h, ["a", "b"]).state; // redo 有内容
    h = pushHistory(h, ["a", "b"]); // 新变更 → 清 redo
    expect(canRedo(h)).toBe(false);
  });

  it("栈深上限", () => {
    let h = { undo: [], redo: [], limit: 3 };
    for (let i = 0; i < 5; i++) h = pushHistory(h, ["s" + i]);
    expect(h.undo.length).toBe(3);
    expect(h.undo[0]).toEqual(["s2"]);
  });
});
