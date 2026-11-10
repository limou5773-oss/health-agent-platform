<script setup lang="ts">
defineProps<{
  loading?: boolean;
  error?: string;
  empty?: boolean;
  title?: string;
  description?: string;
}>();
defineEmits<{ retry: [] }>();
</script>
<template>
  <div v-if="loading" class="notice loading" role="status" aria-live="polite">
    <span class="loading-dot"></span>正在读取数据…
  </div>
  <div v-else-if="error" class="notice error" role="alert">
    <strong>未能完成操作</strong>
    <p>{{ error }}</p>
    <button type="button" class="secondary" @click="$emit('retry')">
      重新加载
    </button>
  </div>
  <div v-else-if="empty" class="notice empty">
    <strong>{{ title || "这里还没有数据" }}</strong>
    <p>{{ description || "添加记录后，就能在这里查看。" }}</p>
    <slot />
  </div>
</template>
