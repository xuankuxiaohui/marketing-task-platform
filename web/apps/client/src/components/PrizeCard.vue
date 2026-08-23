<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { Button } from "vant";
import type { PrizeCardView } from "@/api/prize";
import FallbackImage from "@/components/FallbackImage.vue";
import { zhCN } from "@/locales/zh-CN";
import { remainLabel } from "@/utils/countdown";
import { formatBeijing } from "@/utils/datetime";
import { prizeButtonState } from "@/utils/prize-button";

defineOptions({ name: "PrizeCard" });

const props = defineProps<{
  prize: PrizeCardView;
  claiming?: boolean;
}>();

const emit = defineEmits<{
  claim: [];
  source: [];
}>();

const nowMs = ref(Date.now());
let tick: ReturnType<typeof setInterval> | undefined;

const button = computed(() =>
  prizeButtonState({
    status: props.prize.status,
    fulfillmentStatus: props.prize.fulfillmentStatus,
    failReason: props.prize.failReason,
    fulfillFailReason: props.prize.fulfillFailReason,
  }),
);

const countdown = computed(() => {
  if (!button.value.countdown) {
    return "";
  }
  return remainLabel(props.prize.expireAt, nowMs.value);
});

const loading = computed(() => Boolean(props.claiming) || button.value.loading);

onMounted(() => {
  if (button.value.countdown && props.prize.expireAt) {
    tick = setInterval(() => {
      nowMs.value = Date.now();
    }, 1000);
  }
});

onUnmounted(() => {
  if (tick != null) {
    clearInterval(tick);
  }
});
</script>

<template>
  <article class="prize-card" data-testid="prize-card">
    <FallbackImage :src="prize.prizeImage" :alt="prize.prizeName ?? zhCN.mine.prizes" />
    <div class="prize-card__meta">
      <strong>{{ prize.prizeName }}</strong>
      <span v-if="prize.obtainedAt" class="prize-card__obtained" data-testid="prize-obtained-at">
        {{ zhCN.prize.obtainedAt }} {{ formatBeijing(prize.obtainedAt) }}
      </span>
      <button
        v-if="prize.sourceTaskId != null"
        class="prize-card__source"
        type="button"
        data-testid="prize-source"
        @click="emit('source')"
      >
        {{ zhCN.prize.source }} {{ prize.sourceTaskName || prize.sourceTaskId }}
      </button>
      <span v-if="countdown" class="prize-card__countdown" data-testid="prize-countdown">{{ countdown }}</span>
      <span v-if="button.reason && button.kind !== 'terminal'" class="prize-card__reason" data-testid="prize-reason">
        {{ button.reason }}
      </span>
      <span v-if="button.contact" class="prize-card__contact" data-testid="prize-contact">{{ zhCN.prize.contact }}</span>
    </div>
    <Button
      size="small"
      :type="button.disabled ? 'default' : 'primary'"
      :disabled="button.disabled"
      :loading="loading"
      :data-testid="`prize-action-${prize.recordId}`"
      @click="emit('claim')"
    >
      {{ button.label }}
    </Button>
  </article>
</template>

<style scoped>
.prize-card {
  display: flex;
  gap: 12px;
  align-items: center;
  margin: 0 16px 12px;
  padding: 12px;
  border-radius: var(--portal-radius);
  background: var(--portal-surface);
  box-shadow: var(--portal-shadow-soft);
}
.prize-card__meta {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 4px;
}
.prize-card__meta strong {
  font-size: 15px;
}
.prize-card__source,
.prize-card__obtained,
.prize-card__countdown,
.prize-card__reason,
.prize-card__contact {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--portal-muted);
  font-size: 12px;
  text-align: left;
}
.prize-card__source {
  color: var(--portal-primary);
}
.prize-card__countdown {
  color: var(--portal-accent);
}
</style>
