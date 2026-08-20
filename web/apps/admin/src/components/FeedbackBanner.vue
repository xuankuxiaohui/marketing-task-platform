<script setup lang="ts">
import { ref } from "vue";
import { zhCN } from "@/locales/zh-CN";
import type { PageFeedback } from "@/utils/feedback";

defineOptions({ name: "FeedbackBanner" });

const props = defineProps<{
  feedback: PageFeedback | null;
}>();

const copied = ref(false);

async function copyTrace(): Promise<void> {
  if (!props.feedback?.traceId) {
    return;
  }
  try {
    await globalThis.navigator.clipboard.writeText(props.feedback.traceId);
    copied.value = true;
  } catch {
    copied.value = false;
  }
}
</script>

<template>
  <p v-if="feedback" class="feedback-banner" data-testid="page-error" role="alert">
    <span>{{ feedback.message }}</span>
    <button
      v-if="feedback.traceId"
      type="button"
      data-testid="copy-trace"
      @click="copyTrace"
    >
      {{ copied ? zhCN.common.copied : zhCN.common.copyTrace }}
    </button>
  </p>
</template>

<style scoped>
.feedback-banner {
  display: flex;
  gap: 12px;
  align-items: center;
  color: #b91c1c;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 6px;
  padding: 8px 12px;
}
</style>
