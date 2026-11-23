<template>
  <div class="param-input-root">
    <!-- 简单类型：输入框 -->
    <template v-if="!def || !def.type || ['string','number','boolean'].includes(def.type)">
      <label class="param-field" :title="def && def.desc">
        <span class="param-label">{{ def && def.label ? def.label : def.name }}<b v-if="def && def.required" class="req">*</b></span>
        <input
          v-if="!def || def.type !== 'boolean'"
          :type="def && def.type === 'number' ? 'number' : 'text'"
          class="card-input"
          :value="modelValue"
          :placeholder="def && def.desc ? def.desc : '输入' + (def && def.label ? def.label : '值')"
          @input="$emit('update:modelValue', $event.target.value)"
        />
        <input
          v-else
          type="checkbox"
          :checked="!!modelValue"
          @change="$emit('update:modelValue', $event.target.checked)"
        />
      </label>
    </template>

    <!-- object：可折叠字段组，递归渲染子字段 -->
    <div v-else-if="def.type === 'object'" class="param-object">
      <div class="param-object-head" @click="collapsed = !collapsed">
        <span class="param-object-title">📦 {{ def.label || def.name }}（对象）</span>
        <span class="param-toggle">{{ collapsed ? '▸' : '▾' }}</span>
      </div>
      <div v-show="!collapsed" class="param-object-body">
        <ParamInput
          v-for="child in (def.fields || [])"
          :key="child.name"
          :def="child"
          :model-value="getField(modelValue, child.name)"
          @update:model-value="setField(child.name, $event)"
        />
      </div>
    </div>

    <!-- array：可增删行，每行递归渲染元素 -->
    <div v-else-if="def.type === 'array'" class="param-array">
      <div class="param-array-head">
        <span class="param-object-title">📋 {{ def.label || def.name }}（数组）</span>
        <button type="button" class="btn-secondary sm" @click="addRow">+ 添加一行</button>
      </div>
      <div v-for="(row, idx) in rows" :key="idx" class="param-array-row">
        <span class="param-row-idx">#{{ idx + 1 }}</span>
        <div class="param-array-row-body">
          <ParamInput
            :def="def.element || { name:'item', label:'值', type:'string' }"
            :model-value="row"
            @update:model-value="updateRow(idx, $event)"
          />
        </div>
        <button type="button" class="card-btn danger" @click="removeRow(idx)" title="删除此行">✕</button>
      </div>
      <p v-if="rows.length === 0" class="param-array-empty">暂无元素，点「+ 添加一行」</p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from "vue";

const props = defineProps({
  def: { type: Object, default: null },
  modelValue: { default: null }
});
const emit = defineEmits(["update:modelValue"]);

const collapsed = ref(false);
// array 的行数据（从 modelValue 同步）
const rows = ref([]);
watch(() => props.modelValue, (v) => {
  if (Array.isArray(v)) rows.value = v;
}, { immediate: true });

const getField = (obj, name) => (obj && typeof obj === "object") ? obj[name] : undefined;
const setField = (name, value) => {
  const next = (props.modelValue && typeof props.modelValue === "object")
    ? { ...props.modelValue } : {};
  next[name] = value;
  emit("update:modelValue", next);
};

const addRow = () => {
  const el = props.def.element;
  const blank = el && el.type === "object" ? {} : "";
  const next = [...rows.value, blank];
  rows.value = next;
  emit("update:modelValue", next);
};
const updateRow = (idx, value) => {
  const next = [...rows.value];
  next[idx] = value;
  rows.value = next;
  emit("update:modelValue", next);
};
const removeRow = (idx) => {
  const next = rows.value.filter((_, i) => i !== idx);
  rows.value = next;
  emit("update:modelValue", next);
};
</script>

<style scoped>
.param-input-root { width: 100%; }
.param-field { display: flex; flex-direction: column; gap: 3px; margin-bottom: 6px; font-size: 11.5px; color: var(--ink-700); }
.param-label { font-weight: 600; }
.req { color: var(--bad-text); margin-left: 2px; }
.param-field input[type="text"], .param-field input[type="number"] { width: 100%; }
.param-field input[type="checkbox"] { width: auto; }
.param-object { border: 1px solid var(--ink-200); border-radius: 8px; margin-bottom: 6px; overflow: hidden; }
.param-object-head { display: flex; justify-content: space-between; align-items: center; padding: 5px 9px; background: var(--ink-50); cursor: pointer; user-select: none; }
.param-object-title { font-size: 11.5px; font-weight: 600; color: var(--ink-700); }
.param-toggle { color: var(--ink-400); }
.param-object-body { padding: 8px 9px 2px; }
.param-array { border: 1px solid var(--ink-200); border-radius: 8px; margin-bottom: 6px; padding: 6px 9px; }
.param-array-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.param-array-row { display: flex; gap: 6px; align-items: flex-start; margin-bottom: 6px; padding: 6px; background: var(--ink-50); border-radius: 6px; }
.param-row-idx { font-size: 11px; color: var(--ink-400); flex: none; padding-top: 4px; }
.param-array-row-body { flex: 1; min-width: 0; }
.param-array-empty { font-size: 11px; color: var(--ink-400); margin: 4px 0; }
</style>
