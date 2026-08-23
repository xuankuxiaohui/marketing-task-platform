<script setup lang="ts">
import { computed } from "vue";
import { clipCellText, ELLIPSIS_LIMIT } from "@/utils/ellipsis";

defineOptions({ name: "EllipsisCell" });

const props = withDefaults(
  defineProps<{
    value?: unknown;
    max?: number;
  }>(),
  {
    max: ELLIPSIS_LIMIT,
  },
);

const clipped = computed(() => clipCellText(props.value, props.max));
</script>

<template>
  <a-tooltip v-if="clipped.clipped" :title="clipped.full" color="#595959" placement="topLeft">
    <span class="cell-ellipsis">{{ clipped.display }}</span>
  </a-tooltip>
  <span v-else class="cell-ellipsis">{{ clipped.display }}</span>
</template>

<style scoped>
.cell-ellipsis {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
</style>
