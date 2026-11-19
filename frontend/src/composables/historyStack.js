/**
 * 撤销/重做栈（纯逻辑，无 Vue 依赖，可单测）。
 * state = { undo: [snapshot...], redo: [snapshot...], limit }
 * snapshot = canvasCards 的深拷贝数组。
 */

export const makeHistory = (limit = 30) => ({ undo: [], redo: [], limit });

/** 记录一次变更前的快照：压入 undo、清空 redo、限制栈深 */
export const pushHistory = (state, snapshot) => ({
  undo: [...state.undo, snapshot].slice(-state.limit),
  redo: [],
  limit: state.limit
});

/** 撤销：从 undo 弹出快照作为返回值，当前状态压入 redo */
export const popUndo = (state, current) => {
  if (!state.undo.length) return { snapshot: null, state };
  const snapshot = state.undo[state.undo.length - 1];
  return {
    snapshot,
    state: {
      undo: state.undo.slice(0, -1),
      redo: [...state.redo, current].slice(-state.limit),
      limit: state.limit
    }
  };
};

/** 重做：从 redo 弹出快照作为返回值，当前状态压入 undo */
export const popRedo = (state, current) => {
  if (!state.redo.length) return { snapshot: null, state };
  const snapshot = state.redo[state.redo.length - 1];
  return {
    snapshot,
    state: {
      undo: [...state.undo, current].slice(-state.limit),
      redo: state.redo.slice(0, -1),
      limit: state.limit
    }
  };
};

export const canUndo = (state) => state.undo.length > 0;
export const canRedo = (state) => state.redo.length > 0;
