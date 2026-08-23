<script setup lang="ts">
import type { MonthGridCell } from "@/utils/home-week";
import { WEEKDAYS } from "@/utils/home-week";

defineOptions({ name: "SigninMonthGrid" });

defineProps<{
  cells: MonthGridCell[];
  compact?: boolean;
}>();

const emit = defineEmits<{
  select: [date: string];
}>();
</script>

<template>
  <div class="month-grid" :class="{ 'month-grid--compact': compact }" data-testid="signin-month-grid">
    <span v-for="label in WEEKDAYS" :key="label" class="month-grid__dow">{{ label }}</span>
    <button
      v-for="(cell, index) in cells"
      :key="cell.date || `pad-${index}`"
      type="button"
      class="month-grid__cell"
      :class="[
        cell.inMonth ? `month-grid__cell--${cell.state.toLowerCase()}` : 'month-grid__cell--pad',
      ]"
      :disabled="!cell.inMonth"
      :data-testid="cell.inMonth ? `month-cell-${cell.date}` : undefined"
      @click="cell.inMonth ? emit('select', cell.date) : undefined"
    >
      {{ cell.inMonth ? cell.dayNum : "" }}
    </button>
  </div>
</template>

<style scoped>
.month-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 2px;
}
.month-grid__dow {
  color: var(--portal-muted);
  font-size: 10px;
  text-align: center;
}
.month-grid__cell {
  aspect-ratio: 1;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--portal-ink);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}
.month-grid__cell--pad {
  visibility: hidden;
}
.month-grid__cell--signed,
.month-grid__cell--catchup {
  background: var(--portal-primary-soft);
  color: var(--portal-primary-deep);
  font-weight: 600;
}
.month-grid__cell--today_available {
  background: var(--portal-accent-soft);
  color: var(--portal-accent);
  font-weight: 600;
}
.month-grid__cell--missed_catchable {
  color: var(--portal-accent);
}
.month-grid--compact .month-grid__dow,
.month-grid--compact .month-grid__cell {
  font-size: 9px;
}
.month-grid--compact .month-grid__cell {
  border-radius: 4px;
}
</style>
