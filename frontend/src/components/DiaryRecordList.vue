<script setup lang="ts">
import type { DiaryRecord, Page } from "../types";
defineProps<{ records: Page<DiaryRecord>; busy: boolean; editing: boolean }>();
const emit = defineEmits<{
  detail: [id: number];
  edit: [record: DiaryRecord];
  remove: [record: DiaryRecord];
  permission: [record: DiaryRecord];
  page: [value: number];
}>();
</script>
<template>
  <div class="table-wrap">
    <table>
      <caption class="sr-only">
        日记记录，按实际记录时间从新到旧排列
      </caption>
      <thead>
        <tr>
          <th scope="col">实际记录时间 / 来源</th>
          <th scope="col">情绪自述 / 压力</th>
          <th scope="col">Agent 权限</th>
          <th scope="col">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="record in records.items" :key="record.id">
          <td class="note-cell">
            <time :datetime="record.recordedAt">{{
              record.recordedAt.replace("T", " ")
            }}</time>
            <p class="small muted diary-source">
              {{ record.sourceName }} · #{{ record.id }}
            </p>
          </td>
          <td class="note-cell">
            <span class="prose">{{ record.mood || "情绪未填写" }}</span>
            <p class="small muted">
              主观压力：{{
                record.stressLevel === null
                  ? "未填写"
                  : `${record.stressLevel} / 10`
              }}
            </p>
          </td>
          <td>
            <span class="tag" :class="{ warm: !record.agentAllowed }">{{
              record.agentAllowed ? "允许后续任务读取" : "私密"
            }}</span>
          </td>
          <td>
            <div class="actions">
              <button class="text-button" @click="emit('detail', record.id)">
                原始详情
              </button>
              <button
                class="text-button"
                :disabled="busy || editing"
                @click="emit('edit', record)"
              >
                编辑
              </button>
              <button
                class="text-button"
                :disabled="busy || editing"
                @click="emit('permission', record)"
              >
                {{ record.agentAllowed ? "撤销授权" : "允许读取" }}
              </button>
              <button
                class="text-button danger-text"
                :disabled="busy || editing"
                @click="emit('remove', record)"
              >
                删除
              </button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  <div class="pagination">
    <span>共 {{ records.total }} 条 · 第 {{ records.page }} 页</span>
    <button
      class="secondary"
      :disabled="busy || records.page === 1"
      @click="emit('page', records.page - 1)"
    >
      上一页
    </button>
    <button
      class="secondary"
      :disabled="busy || records.page * records.pageSize >= records.total"
      @click="emit('page', records.page + 1)"
    >
      下一页
    </button>
  </div>
</template>
<style scoped>
.diary-source {
  white-space: pre-wrap;
}
</style>
