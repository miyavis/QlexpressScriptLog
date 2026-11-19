/**
 * 执行时间轴（纯函数，可单测）：把行级日志 lineExecutions 按事件发生时间排序，
 * 形成可逐步回放的事件序列。每个活跃行产生一个事件，时间取该行最早的活动时间。
 */

/**
 * 构建时间轴。
 * @param {object} lineExecutions 行号→LineExecution 映射
 * @returns {Array<{lineNo, time, type, label}>} 按时间升序的事件列表
 */
export const buildTimeline = (lineExecutions) => {
  if (!lineExecutions || typeof lineExecutions !== "object") return [];
  const events = [];
  for (const le of Object.values(lineExecutions)) {
    if (!le || !le.hasActivity) continue;
    let best = null;
    for (const call of le.calls || []) {
      const t = call.startTime;
      if (typeof t === "number" && (best == null || t < best.time)) {
        best = { time: t, type: "call", label: call.chineseName || call.serviceName || "服务调用" };
      }
    }
    for (const step of le.steps || []) {
      const t = step.timestamp;
      if (typeof t === "number" && (best == null || t < best.time)) {
        best = { time: t, type: "step", label: step.stepName || "脚本打印" };
      }
    }
    if (best) {
      events.push({ lineNo: le.lineNo, time: best.time, type: best.type, label: best.label });
    }
  }
  events.sort((a, b) => a.time - b.time);
  return events;
};
