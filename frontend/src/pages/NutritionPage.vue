<script setup lang="ts">
import { GlassWater, Utensils } from "@lucide/vue";
import NutritionRecordsPanel from "../components/NutritionRecordsPanel.vue";
</script>
<template>
  <nav class="page-actions nutrition-jump" aria-label="饮食与饮水分区">
    <a class="text-link" href="#nutrition-meals"
      ><Utensils :size="18" />三餐与加餐</a
    >
    <a class="text-link" href="#nutrition-water"
      ><GlassWater :size="18" />饮水记录</a
    >
  </nav>
  <p class="footnote">
    按个人档案时区填写实际食用、饮用时间。不同来源发生冲突时，由你选择统计来源；无记录与热量未知会分别保留。
  </p>
  <section
    id="nutrition-meals"
    class="nutrition-section"
    aria-labelledby="nutrition-meals-title"
  >
    <h2 id="nutrition-meals-title" class="nutrition-title">
      <Utensils :size="21" />三餐与加餐
    </h2>
    <NutritionRecordsPanel kind="meals" />
  </section>
  <section
    id="nutrition-water"
    class="nutrition-section"
    aria-labelledby="nutrition-water-title"
  >
    <h2 id="nutrition-water-title" class="nutrition-title">
      <GlassWater :size="21" />饮水记录
    </h2>
    <NutritionRecordsPanel kind="water" />
  </section>
</template>
<style scoped>
.nutrition-jump {
  justify-content: flex-start;
  padding: 0.75rem 0;
  border-bottom: 1px solid var(--line);
  gap: 1.5rem;
  flex-wrap: wrap;
}
.nutrition-section {
  scroll-margin-top: 5rem;
}
.nutrition-section + .nutrition-section {
  margin-top: 2.5rem;
}
.nutrition-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 1.25rem;
  font-size: 1.3rem;
}
</style>
