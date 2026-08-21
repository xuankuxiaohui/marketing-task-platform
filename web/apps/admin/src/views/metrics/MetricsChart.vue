<script setup lang="ts">
import { init, type ECharts } from "echarts";
import { onBeforeUnmount, onMounted, ref, watch } from "vue";

defineOptions({ name: "MetricsChart" });

const props = defineProps<{
  option: Record<string, unknown>;
}>();

const el = ref<HTMLDivElement | null>(null);
let chart: ECharts | null = null;

function render(): void {
  if (!el.value) {
    return;
  }
  if (!chart) {
    chart = init(el.value);
  }
  chart.setOption(props.option, true);
}

onMounted(() => {
  render();
});

watch(
  () => props.option,
  () => {
    render();
  },
);

onBeforeUnmount(() => {
  chart?.dispose();
  chart = null;
});
</script>

<template>
  <div ref="el" class="metrics-chart" data-testid="metrics-chart" />
</template>

<style scoped>
.metrics-chart {
  width: 100%;
  height: 280px;
}
</style>
