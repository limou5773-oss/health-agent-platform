<script setup lang="ts">
import { computed, ref } from "vue";
import { dateAfter } from "../api";
import type { Plan } from "../types";

const props = defineProps<{ plan: Plan; today: string; selected: string | null }>();
const emit = defineEmits<{ select: [date: string | null] }>();
const weekdays = ["一", "二", "三", "四", "五", "六", "日"];
const minMonth = computed(() => props.plan.startDate.slice(0, 7));
const maxMonth = computed(() => props.plan.endDate.slice(0, 7));
const today = computed(() => props.today);
const initialDate = today.value < props.plan.startDate ? props.plan.startDate
  : today.value > props.plan.endDate ? props.plan.endDate : today.value;
const activeMonth = ref(initialDate.slice(0, 7));
const month = computed(() => activeMonth.value < minMonth.value ? minMonth.value
  : activeMonth.value > maxMonth.value ? maxMonth.value : activeMonth.value);
const monthStart = computed(() => `${month.value}-01`);
const nextMonthStart = computed(() => `${dateAfter(monthStart.value, 32).slice(0, 7)}-01`);
const leading = computed(() => (new Date(`${monthStart.value}T12:00:00`).getDay() + 6) % 7);
const days = computed(() => {
  const counts = new Map<string, { total: number; completed: number }>();
  for (const item of props.plan.items ?? []) {
    const count = counts.get(item.date) ?? { total: 0, completed: 0 };
    count.total++;
    if (item.completed) count.completed++;
    counts.set(item.date, count);
  }
  const result = [];
  for (let date = monthStart.value; date < nextMonthStart.value; date = dateAfter(date, 1)) {
    const count = counts.get(date);
    const status = !count ? "无任务" : count.completed === count.total ? "已完成"
      : date > today.value ? "未到期" : date === today.value ? "待完成" : "未完成";
    result.push({ date, status, count });
  }
  return result;
});

function move(direction: -1 | 1) {
  activeMonth.value = (direction === -1 ? dateAfter(monthStart.value, -1) : nextMonthStart.value).slice(0, 7);
  emit("select", null);
}
</script>

<template>
  <section class="checkin-calendar" aria-labelledby="checkin-calendar-title">
    <h3 id="checkin-calendar-title">打卡日历</h3>
    <div class="actions">
      <button class="secondary" :disabled="month === minMonth" @click="move(-1)">上个月</button>
      <strong aria-live="polite">{{ month }}</strong>
      <button class="secondary" :disabled="month === maxMonth" @click="move(1)">下个月</button>
      <button v-if="selected" class="secondary" @click="emit('select', null)">查看全部任务</button>
    </div>
    <div class="calendar-grid">
      <span v-for="weekday in weekdays" :key="weekday" class="small muted">周{{ weekday }}</span>
      <span v-for="space in leading" :key="space" aria-hidden="true" />
      <template v-for="day in days" :key="day.date">
        <button v-if="day.count" type="button" class="secondary calendar-day"
          :class="{ 'calendar-complete': day.status === '已完成' }"
          :aria-label="`${day.date}，${day.status}，${day.count.completed} / ${day.count.total} 项`"
          :aria-current="day.date === today ? 'date' : undefined"
          :aria-pressed="selected === day.date" @click="emit('select', day.date)">
          {{ Number(day.date.slice(-2)) }}<small>{{ day.count.completed }}/{{ day.count.total }}</small>
        </button>
        <span v-else class="calendar-day muted" :aria-label="`${day.date}，无任务`">
          {{ Number(day.date.slice(-2)) }}
        </span>
      </template>
    </div>
    <p class="small muted">日期下方为已打卡数 / 任务数。点击有任务的日期查看当天安排。</p>
  </section>
</template>

<style scoped>
.checkin-calendar { margin-block: 1.5rem; }
.calendar-grid { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 0.25rem; text-align: center; margin-top: 0.75rem; }
.calendar-day { display: grid; justify-items: center; align-content: center; gap: 0.1rem; min-height: 2.7rem; padding: 0.2rem; font-variant-numeric: tabular-nums; }
.calendar-complete { background: #edf5ec; }
.calendar-day[aria-pressed="true"] { outline: 2px solid var(--green); outline-offset: -2px; }
</style>
