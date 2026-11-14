<script setup lang="ts">
import { ref } from "vue";
import { api, errorMessage } from "../api";

type Candidate = { foodName: string; portionGrams: number | null; confidence: "LOW" | "MEDIUM" | "HIGH" };
defineProps<{ disabled: boolean }>();
const emit = defineEmits<{
  candidate: [foodName: string, portionGrams: number | null, confidence: string];
  busy: [value: boolean];
}>();
const confidenceText = { LOW: "低", MEDIUM: "中", HIGH: "高" } as const;
const photo = ref<File>();
const result = ref<Candidate[]>();
const error = ref("");
const busy = ref(false);

function choose(event: Event) {
  photo.value = (event.target as HTMLInputElement).files?.[0];
  result.value = undefined;
  error.value = "";
}

async function preview(file: File) {
  busy.value = true;
  emit("busy", true);
  error.value = "";
  result.value = undefined;
  try {
    const body = new FormData();
    body.append("photo", file);
    result.value = (await api<{ candidates: Candidate[] }>("/nutrition/meals/photo-preview",
      { method: "POST", body }, 105_000)).candidates;
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
    emit("busy", false);
  }
}
</script>

<template>
  <details class="photo-preview">
    <summary>从餐食照片填写</summary>
    <p class="small muted">选择不超过 5 MB 的 JPEG 或 PNG。点击识别会将处理后的照片发送给已配置的外部模型。</p>
    <form class="filter-bar" @submit.prevent="photo && preview(photo)">
      <label>餐食照片<input type="file" accept="image/jpeg,image/png" :disabled="disabled || busy" required @change="choose" /></label>
      <button class="secondary" :disabled="disabled || busy || !photo">{{ busy ? "正在识别…" : "识别候选" }}</button>
    </form>
    <p v-if="error" class="feedback error" role="alert">{{ error }} 可以继续在下方手动录入。</p>
    <div v-if="result" role="status">
      <p v-if="!result.length" class="muted">照片中未识别到明确的食物，可以在下方手动录入。</p>
      <template v-else>
        <p class="small muted">选择一项填入下方表单并核对。照片份量为粗估，可修改或清空。</p>
        <ul>
          <li v-for="(item, index) in result" :key="index">
            {{ item.foodName }} · {{ item.portionGrams === null ? "份量未知" : `约 ${item.portionGrams} 克` }}
            · 识别把握{{ confidenceText[item.confidence] }}
            <button type="button" class="secondary" :disabled="disabled"
              @click="emit('candidate', item.foodName, item.portionGrams, confidenceText[item.confidence])">填入</button>
          </li>
        </ul>
      </template>
    </div>
  </details>
</template>

<style scoped>
.photo-preview { margin-block: 0.8rem; }
.photo-preview summary { cursor: pointer; }
</style>
