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
  detail: [];
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
    <button type="button" class="prize-card__main" data-testid="prize-open" @click="emit('detail')">
      <FallbackImage :src="prize.prizeImage" :alt="prize.prizeName ?? zhCN.mine.prizes" />
      <span class="prize-card__meta">
        <strong>{{ prize.prizeName }}</strong>
        <span v-if="prize.obtainedAt" class="prize-card__obtained" data-testid="prize-obtained-at">
          {{ zhCN.prize.obtainedAt }} {{ formatBeijing(prize.obtainedAt) }}
        </span>
        <span class="prize-card__detail-link">{{ zhCN.prize.detail }}</span>
        <span v-if="countdown" class="prize-card__countdown" data-testid="prize-countdown">{{ countdown }}</span>
        <span v-if="button.reason && button.kind !== 'terminal'" class="prize-card__reason" data-testid="prize-reason">
          {{ button.reason }}
        </span>
        <span v-if="button.contact" class="prize-card__contact" data-testid="prize-contact">{{ zhCN.prize.contact }}</span>
      </span>
    </button>
    <Button
      size="small"
      :type="button.disabled ? 'default' : 'primary'"
      :disabled="button.disabled"
      :loading="loading"
      :data-testid="`prize-action-${prize.recordId}`"
      @click.stop="emit('claim')"
    >
      {{ button.label }}
    </Button>
  </article>
</template>

<style scoped>
.prize-card {
  display: flex;
  gap: 8px;
  align-items: center;
  margin: 0 16px 12px;
  padding: 12px;
  border-radius: var(--portal-radius);
  background: var(--portal-surface);
  box-shadow: var(--portal-shadow-soft);
}
.prize-card:first-child {
  margin-top: 4px;
}
.prize-card__main {
  display: flex;
  min-width: 0;
  flex: 1;
  gap: 12px;
  align-items: center;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
}
.prize-card :deep(.fallback-image) {
  width: 56px;
  height: 56px;
  flex: none;
  border-radius: 14px;
  background: var(--portal-primary-soft);
}
.prize-card__meta {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 4px;
}
.prize-card__meta strong {
  overflow: hidden;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.prize-card__obtained,
.prize-card__countdown,
.prize-card__reason,
.prize-card__contact,
.prize-card__detail-link {
  color: var(--portal-muted);
  font-size: 12px;
}
.prize-card__detail-link {
  color: var(--portal-primary);
}
.prize-card__countdown {
  color: var(--portal-accent);
}
</style>
