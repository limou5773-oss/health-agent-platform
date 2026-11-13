<script setup lang="ts">
import { ref, watch } from "vue";
import { useRoute } from "vue-router";
import { Search, ArrowUpRight } from "@lucide/vue";
import { api, errorMessage, json, safeLink } from "../api";
import { knowledgeCategories } from "../knowledge";
import type { Article } from "../types";
import StateNotice from "../components/StateNotice.vue";
const query = ref(""),
  articles = ref<Article[]>([]),
  loading = ref(true),
  error = ref(""),
  selected = ref<number | null>(null);
const route = useRoute();
const category = ref("");
const sort = ref("RELEVANCE");
const favoritesOnly = ref(false);
const changing = ref<number | null>(null);
const actionError = ref("");
let revision = 0;
async function load() {
  const request = ++revision;
  const id = typeof route.query.id === "string" && /^\d+$/.test(route.query.id) ? route.query.id : null;
  loading.value = true;
  error.value = "";
  actionError.value = "";
  selected.value = null;
  try {
    const params = new URLSearchParams({ query: query.value, sort: sort.value, favoritesOnly: String(favoritesOnly.value) });
    if (category.value) params.set("category", category.value);
    const result = id ? [await api<Article>(`/knowledge/${id}`)] : await api<Article[]>(`/knowledge?${params}`);
    if (request !== revision) return;
    articles.value = result;
    if (id) selected.value = result[0].id;
  } catch (cause) {
    if (request === revision) error.value = errorMessage(cause);
  } finally {
    if (request === revision) loading.value = false;
  }
}
async function toggleFavorite(article: Article) {
  changing.value = article.id;
  actionError.value = "";
  const favorite = !article.favorite;
  try {
    await api(`/knowledge/${article.id}/favorite`, json(favorite ? "PUT" : "DELETE"));
    article.favorite = favorite;
    if (!favorite && favoritesOnly.value && !route.query.id) await load();
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    changing.value = null;
  }
}
watch(() => route.query.id, load, { immediate: true });
</script>
<template>
  <div v-if="route.query.id" class="actions">
    <RouterLink class="button secondary" to="/knowledge">返回知识库</RouterLink>
  </div>
  <form v-else class="filter-bar" @submit.prevent="load">
    <label class="search-input"
      ><Search :size="18" /><span class="sr-only">检索知识资料</span
      ><input
        v-model="query"
        maxlength="200"
        type="search"
        placeholder="搜索标题、内容或关键词" /></label
    >
    <label>分类<select v-model="category">
      <option value="">全部分类</option>
      <option v-for="(label, key) in knowledgeCategories" :key="key" :value="key">{{ label }}</option>
    </select></label>
    <label>排序<select v-model="sort">
      <option value="RELEVANCE">相关度</option><option value="RECENT">更新时间</option>
    </select></label>
    <label class="checkbox-label"><input v-model="favoritesOnly" type="checkbox" />只看收藏</label>
    <button :disabled="loading || changing !== null">搜索资料</button>
  </form>
  <p v-if="actionError" class="feedback error" role="alert">{{ actionError }}</p>
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
        <span class="eyebrow">{{ knowledgeCategories[article.category] }} · {{ article.sourceName }}</span
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
      <button class="secondary" :disabled="changing !== null" :aria-pressed="article.favorite"
        @click="toggleFavorite(article)">{{ article.favorite ? "取消收藏" : "收藏资料" }}</button>
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
