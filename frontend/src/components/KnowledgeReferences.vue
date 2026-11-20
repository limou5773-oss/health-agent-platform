<script setup lang="ts">
import { safeLink } from "../api";
import type { KnowledgeReference } from "../types";

defineProps<{
  references: KnowledgeReference[];
  publishedIds?: number[] | null;
}>();
</script>

<template>
  <ul>
    <li v-for="reference in references" :key="reference.id">
      <a v-if="safeLink(reference.sourceUrl ?? '')" :href="safeLink(reference.sourceUrl ?? '')"
        target="_blank" rel="noopener noreferrer">{{ reference.title }}</a>
      <strong v-else>{{ reference.title }}</strong>
      <span class="muted small"> · 资料 #{{ reference.id }} · {{ reference.sourceName }}</span>
      <span v-if="reference.updatedAt" class="muted small">
        · 引用版本更新于 {{ reference.updatedAt.replace("T", " ") }}
      </span>
      <template v-if="publishedIds?.includes(reference.id)">
        <span class="muted small"> · 当前已发布</span>
        <RouterLink class="small report-screen-only" :to="{ path: '/knowledge', query: { id: reference.id } }">
          查看当前库内资料
        </RouterLink>
      </template>
      <span v-else class="muted small"> · 当前未发布</span>
    </li>
  </ul>
</template>
