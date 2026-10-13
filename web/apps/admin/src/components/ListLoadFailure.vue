<script setup lang="ts">
import { zhCN } from "@/locales/zh-CN";

defineOptions({ name: "ListLoadFailure" });

withDefaults(defineProps<{ loading?: boolean }>(), { loading: false });

defineEmits<{
  reload: [];
}>();
</script>

<template>
  <div class="list-load-failure" data-testid="list-load-failure">
    <p class="list-load-failure__title" data-testid="list-load-failed-title">
      {{ zhCN.common.listLoadFailed }}
    </p>
    <p class="list-load-failure__hint" data-testid="list-load-failed-hint">
      {{ zhCN.common.listLoadFailedHint }}
    </p>
    <a-button
      type="primary"
      size="small"
      data-testid="list-load-reload"
      :loading="loading"
      :disabled="loading"
      @click="$emit('reload')"
    >
      {{ zhCN.common.reload }}
    </a-button>
  </div>
</template>

<style scoped>
.list-load-failure {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 24px 12px;
  text-align: center;
}

.list-load-failure__title {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  color: rgba(0, 0, 0, 0.88);
}

.list-load-failure__hint {
  margin: 0;
  font-size: 13px;
  color: rgba(0, 0, 0, 0.45);
}
</style>
