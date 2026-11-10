<script setup lang="ts">
import { ref } from "vue";
import { Footprints, Dumbbell } from "@lucide/vue";
import StepGoal from "../components/StepGoal.vue";
import StepsPanel from "../components/StepsPanel.vue";
import StepImportPanel from "../components/StepImportPanel.vue";
import WorkoutsPanel from "../components/WorkoutsPanel.vue";
const stepRevision = ref(0);
</script>
<template>
  <nav class="page-actions activity-jump" aria-label="运动管理分区">
    <a class="text-link" href="#activity-steps"
      ><Footprints :size="18" />步数与每日目标</a
    >
    <a class="text-link" href="#activity-workouts"
      ><Dumbbell :size="18" />运动与计划任务</a
    >
  </nav>
  <section
    id="activity-steps"
    class="activity-section"
    aria-labelledby="activity-steps-title"
  >
    <h2 id="activity-steps-title" class="activity-title">
      <Footprints :size="21" />步数与每日目标
    </h2>
    <StepGoal @changed="stepRevision++" />
    <StepsPanel :refresh-key="stepRevision" />
    <StepImportPanel @imported="stepRevision++" />
  </section>
  <section
    id="activity-workouts"
    class="activity-section"
    aria-labelledby="activity-workouts-title"
  >
    <h2 id="activity-workouts-title" class="activity-title">
      <Dumbbell :size="21" />运动与计划任务
    </h2>
    <WorkoutsPanel />
  </section>
</template>
<style scoped>
.activity-jump {
  justify-content: flex-start;
  padding: 0.8rem 0;
  border-bottom: 1px solid var(--line);
  gap: 1.5rem;
  flex-wrap: wrap;
}
.activity-section {
  scroll-margin-top: 5rem;
}
.activity-section + .activity-section {
  margin-top: 2.5rem;
}
.activity-title {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin-bottom: 1.2rem;
  font-size: 1.3rem;
}
</style>
