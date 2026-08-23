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
  <a-alert
    v-if="feedback"
    type="error"
    show-icon
    data-testid="page-error"
    role="alert"
    :message="feedback.message"
  >
    <template v-if="feedback.traceId" #action>
      <a-button size="small" data-testid="copy-trace" @click="copyTrace">
        {{ copied ? zhCN.common.copied : zhCN.common.copyTrace }}
      </a-button>
    </template>
  </a-alert>
</template>
