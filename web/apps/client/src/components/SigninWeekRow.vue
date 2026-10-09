<script setup lang="ts">
import type { HomeWeekCell } from "@/utils/home-week";

defineOptions({ name: "SigninWeekRow" });

defineProps<{
  cells: HomeWeekCell[];
}>();

const emit = defineEmits<{
  select: [date: string];
}>();
</script>

<template>
  <div class="week-row" data-testid="home-signin-week">
    <button
      v-for="cell in cells"
      :key="cell.date"
      type="button"
      class="week-row__cell"
      :class="`week-row__cell--${cell.state.toLowerCase()}`"
      :data-testid="`week-cell-${cell.date}`"
      @click.stop="emit('select', cell.date)"
    >
      <span class="week-row__dow">{{ cell.weekday }}</span>
      <span class="week-row__num">{{ cell.dayNum }}</span>
    </button>
  </div>
</template>

<style scoped>
.week-row {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 6px;
  width: 100%;
}
.week-row__cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
  align-items: center;
  padding: 6px 0;
  border: 0;
  border-radius: 10px;
  background: var(--portal-bg);
  color: var(--portal-ink);
}
.week-row__dow {
  color: var(--portal-muted);
  font-size: 10px;
}
.week-row__num {
  font-size: 14px;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}
.week-row__cell--signed,
.week-row__cell--catchup {
  background: var(--portal-primary-soft);
  color: var(--portal-primary-deep);
}
.week-row__cell--today_available {
  background: var(--portal-accent-soft);
  color: var(--portal-accent);
}
.week-row__cell--missed_catchable {
  color: var(--portal-accent);
}
</style>
