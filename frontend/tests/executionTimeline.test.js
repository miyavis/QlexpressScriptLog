// 执行时间轴纯逻辑测试：把行级日志按发生时间排序为可逐步回放的事件序列
import { describe, it, expect } from "vitest";
import { buildTimeline } from "../src/composables/executionTimeline.js";

describe("buildTimeline（执行时间轴）", () => {
  it("按时间排序，每个活跃行一个事件", () => {
    const lineExecutions = {
      1: { lineNo: 1, lineContent: "等级 = 查询用户等级(user);", hasActivity: true,
           calls: [{ chineseName: "查询用户等级", startTime: 200 }], steps: [], expressions: [] },
      2: { lineNo: 2, lineContent: '记录日志("x", 等级);', hasActivity: true,
           calls: [], steps: [{ stepName: "x", timestamp: 100 }], expressions: [] },
      3: { lineNo: 3, lineContent: "// 注释", hasActivity: false, calls: [], steps: [], expressions: [] }
    };
    const timeline = buildTimeline(lineExecutions);
    expect(timeline.length).toBe(2);
    expect(timeline.map((e) => e.lineNo)).toEqual([2, 1]); // step@100 在 call@200 之前
    expect(timeline[0].time).toBe(100);
    expect(timeline[0].type).toBe("step");
    expect(timeline[1].type).toBe("call");
  });

  it("无活跃行返回空时间轴", () => {
    expect(buildTimeline({})).toEqual([]);
    expect(buildTimeline(null)).toEqual([]);
  });

  it("同一行多个事件只保留最早时间", () => {
    const lineExecutions = {
      1: { lineNo: 1, hasActivity: true,
           calls: [{ startTime: 500 }], steps: [{ timestamp: 300 }], expressions: [] }
    };
    const timeline = buildTimeline(lineExecutions);
    expect(timeline).toHaveLength(1);
    expect(timeline[0].time).toBe(300);
  });
});
