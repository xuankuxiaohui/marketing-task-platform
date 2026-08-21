<script setup lang="ts">
import { ref, watch } from "vue";

defineOptions({ name: "FallbackImage" });

const props = defineProps<{
  src?: string;
  alt: string;
}>();

const failed = ref(false);

watch(
  () => props.src,
  () => {
    failed.value = false;
  },
);

function onError(): void {
  failed.value = true;
}
</script>

<template>
  <span class="fallback-image">
    <img v-if="src && !failed" :src="src" :alt="alt" data-testid="fallback-image" @error="onError" />
    <span v-else class="fallback-image__placeholder" data-testid="image-placeholder" :aria-label="alt" />
  </span>
</template>

<style scoped>
.fallback-image {
  display: inline-flex;
  width: 48px;
  height: 48px;
  overflow: hidden;
  border-radius: 8px;
  background: #ebedf0;
}
.fallback-image img,
.fallback-image__placeholder {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.fallback-image__placeholder {
  display: block;
  background: repeating-linear-gradient(45deg, #ebedf0, #ebedf0 6px, #dcdee0 6px, #dcdee0 12px);
}
</style>
