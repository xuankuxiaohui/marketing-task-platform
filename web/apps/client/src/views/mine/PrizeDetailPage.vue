<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Button, Empty, NavBar, showToast } from "vant";
import { isOk } from "@mkt/shared";
import { claimPrize, fetchPrizeList, type PrizeCardView } from "@/api/prize";
import FallbackImage from "@/components/FallbackImage.vue";
import { useSessionReload } from "@/composables/useSessionReload";
import { zhCN } from "@/locales/zh-CN";
import { usePrizePreviewStore } from "@/store/prize-preview";
import { useSessionStore } from "@/store/session";
import { remainLabel } from "@/utils/countdown";
import { formatBeijing } from "@/utils/datetime";
import { showNetworkFail, showPortalFail } from "@/utils/portal-error";
import { prizeButtonState } from "@/utils/prize-button";

defineOptions({ name: "PrizeDetailPage" });

const route = useRoute();
const router = useRouter();
const session = useSessionStore();
const preview = usePrizePreviewStore();
const prize = ref<PrizeCardView | null>(null);
const claiming = ref(false);

const recordId = computed(() => Number(route.params.recordId));
const button = computed(() =>
  prize.value
    ? prizeButtonState({
        status: prize.value.status,
        fulfillmentStatus: prize.value.fulfillmentStatus,
        failReason: prize.value.failReason,
        fulfillFailReason: prize.value.fulfillFailReason,
      })
    : null,
);

async function load(): Promise<void> {
  if (!session.authenticated || !Number.isFinite(recordId.value)) {
    prize.value = null;
    return;
  }
  const cached = preview.prize;
  if (cached && cached.recordId === recordId.value) {
    prize.value = cached;
    return;
  }
  const result = await fetchPrizeList({ tab: "ALL", page: 1, pageSize: 50 });
  if (!isOk(result) || !result.data) {
    showPortalFail(result);
    prize.value = null;
    return;
  }
  prize.value = (result.data.records ?? []).find((row) => row.recordId === recordId.value) ?? null;
}

async function onClaim(): Promise<void> {
  if (!prize.value || !button.value || button.value.disabled || prize.value.recordId == null) {
    return;
  }
  claiming.value = true;
  try {
    const result = await claimPrize(prize.value.recordId);
    if (!isOk(result) || !result.data) {
      showPortalFail(result);
      return;
    }
    const next = {
      ...prize.value,
      status: result.data.status,
      fulfillmentStatus: result.data.fulfillmentStatus,
    };
    prize.value = next;
    preview.set(next);
    showToast(prizeButtonState(next).label);
  } catch {
    showNetworkFail();
  } finally {
    claiming.value = false;
  }
}

function openSource(): void {
  if (prize.value?.sourceTaskId == null) {
    return;
  }
  void router.push(`/task/${prize.value.sourceTaskId}`);
}

useSessionReload(() => {
  void load();
});

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="prize-detail">
    <NavBar :title="zhCN.prize.detailTitle" left-arrow @click-left="router.back()" />
    <Empty v-if="!prize" :description="zhCN.empty.prizes" data-testid="prize-detail-empty" />
    <div v-else class="prize-detail__body" data-testid="prize-detail">
      <header class="prize-detail__hero">
        <FallbackImage :src="prize.prizeImage" :alt="prize.prizeName ?? zhCN.mine.prizes" />
        <div>
          <h2 data-testid="prize-detail-name">{{ prize.prizeName }}</h2>
          <p v-if="prize.obtainedAt" data-testid="prize-obtained-at">
            {{ zhCN.prize.obtainedAt }} {{ formatBeijing(prize.obtainedAt) }}
          </p>
        </div>
      </header>
      <dl class="prize-detail__facts">
        <div v-if="prize.categoryCode">
          <dt>{{ zhCN.prize.detail }}</dt>
          <dd>{{ prize.categoryCode }}</dd>
        </div>
        <div v-if="prize.expireAt">
          <dt>{{ zhCN.prize.remain }}</dt>
          <dd>{{ remainLabel(prize.expireAt, Date.now()) }}</dd>
        </div>
        <div v-if="prize.sourceTaskId != null">
          <dt>{{ zhCN.prize.source }}</dt>
          <dd>
            <button type="button" data-testid="prize-source" @click="openSource">
              {{ prize.sourceTaskName || prize.sourceTaskId }}
            </button>
          </dd>
        </div>
      </dl>
      <div class="prize-detail__actions">
        <Button
          type="primary"
          block
          :disabled="button?.disabled"
          :loading="claiming"
          :data-testid="`prize-action-${prize.recordId}`"
          @click="onClaim"
        >
          {{ button?.label }}
        </Button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.prize-detail {
  min-height: 100%;
  background: var(--portal-bg);
}
.prize-detail__hero {
  display: flex;
  gap: 12px;
  margin: 12px 16px;
  padding: 14px;
  border-radius: var(--portal-radius);
  background: var(--portal-surface);
  box-shadow: var(--portal-shadow-soft);
}
.prize-detail__hero :deep(.fallback-image) {
  width: 72px;
  height: 72px;
  flex: none;
  border-radius: 16px;
}
.prize-detail__hero h2 {
  margin: 0 0 6px;
  font-size: 18px;
}
.prize-detail__hero p {
  margin: 0;
  color: var(--portal-muted);
  font-size: 13px;
}
.prize-detail__facts {
  margin: 0 16px 12px;
  padding: 12px 16px;
  border-radius: var(--portal-radius);
  background: var(--portal-surface);
}
.prize-detail__facts div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  font-size: 14px;
}
.prize-detail__facts dt {
  color: var(--portal-muted);
}
.prize-detail__facts button {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--portal-primary);
}
.prize-detail__actions {
  padding: 8px 16px 24px;
}
</style>
