<script setup lang="ts">
defineProps<{ validDays: number; periodDays: number }>();
</script>

<template>
  <svg class="report-coverage-bar" viewBox="0 0 100 3" preserveAspectRatio="none" aria-hidden="true">
    <rect width="100" height="3" fill="#e3e9e0" />
    <rect :width="validDays * 100 / periodDays" height="3" fill="#3f7155" />
  </svg>
</template>

<style scoped>
.report-coverage-bar {
  display: block;
  width: min(100%, 16rem);
  height: 0.4rem;
  margin-block: 0.25rem;
}
</style>
