<script setup lang="ts">
import { onMounted, ref } from "vue";
import { Search, ArrowUpRight } from "@lucide/vue";
import { api, errorMessage, safeLink } from "../api";
import type { Article } from "../types";
import StateNotice from "../components/StateNotice.vue";
const query = ref(""),
  articles = ref<Article[]>([]),
  loading = ref(true),
  error = ref(""),
  selected = ref<number | null>(null);
async function load() {
  loading.value = true;
  error.value = "";
  selected.value = null;
  try {
    articles.value = await api<Article[]>(
      `/knowledge?${new URLSearchParams({ query: query.value })}`,
    );
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>
<template>
  <form class="search-bar" @submit.prevent="load">
    <label class="search-input"
      ><Search :size="18" /><span class="sr-only">检索知识资料</span
      ><input
        v-model="query"
        type="search"
        placeholder="搜索标题、内容或关键词" /></label
    ><button :disabled="loading">搜索资料</button>
  </form>
  <StateNotice
    :loading="loading"
    :error="error"
    :empty="!articles.length"
    title="暂无匹配资料"
    description="可以换个关键词，或稍后再来查看已发布资料。"
    @retry="load"
  />
  <div v-if="!loading && !error" class="knowledge-list">
    <article
      v-for="article in articles"
      :key="article.id"
      class="knowledge-article"
    >
      <div class="article-meta">
        <span class="eyebrow">{{ article.sourceName }}</span
        ><span class="small muted"
          >更新于 {{ article.updatedAt.slice(0, 10) }}</span
        >
      </div>
      <h2>
        <button
          class="article-title"
          :aria-expanded="selected === article.id"
          @click="selected = selected === article.id ? null : article.id"
        >
          {{ article.title
          }}<span aria-hidden="true">{{
            selected === article.id ? "−" : "+"
          }}</span>
        </button>
      </h2>
      <p v-if="selected !== article.id" class="article-excerpt">
        {{ article.content.slice(0, 140)
        }}{{ article.content.length > 140 ? "…" : "" }}
      </p>
      <div v-else class="prose">{{ article.content }}</div>
      <a
        v-if="safeLink(article.sourceUrl)"
        :href="safeLink(article.sourceUrl)"
        target="_blank"
        rel="noopener noreferrer"
        class="text-link"
        >查看原始来源<ArrowUpRight :size="15"
      /></a>
      <p v-else class="small muted">
        来源：{{ article.sourceName
        }}{{ article.sourceUrl ? "（无可打开的网页链接）" : "" }}
      </p>
    </article>
  </div>
</template>
