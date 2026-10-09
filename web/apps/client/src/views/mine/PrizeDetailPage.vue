<script setup lang="ts">
import { computed, onScopeDispose, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Button, Empty, NavBar, showToast } from "vant";
import { isOk } from "@mkt/shared";
import { claimPrize, fetchPrizeList, type PrizeCardView } from "@/api/prize";
import FallbackImage from "@/components/FallbackImage.vue";
import { zhCN } from "@/locales/zh-CN";
import { usePrizePreviewStore } from "@/store/prize-preview";
import { useLoginOverlayStore } from "@/store/login-overlay";
import { useSessionStore } from "@/store/session";
import { formatBeijing } from "@/utils/datetime";
import { activityAttributionLabel } from "@/utils/activity-ownership";
import { resultMessage, showNetworkFail, showPortalFail } from "@/utils/portal-error";
import { prizeButtonState } from "@/utils/prize-button";

defineOptions({ name: "PrizeDetailPage" });

const route = useRoute();
const router = useRouter();
const session = useSessionStore();
const preview = usePrizePreviewStore();
const overlay = useLoginOverlayStore();
const prize = ref<PrizeCardView | null>(null);
const claiming = ref(false);
const loading = ref(false);
const errorMessage = ref("");
let generation = 0;
let claimSequence = 0;
let disposed = false;

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

const activityLabel = computed(() => (prize.value ? activityAttributionLabel(prize.value) : ""));

async function load(): Promise<void> {
  if (disposed) {
    return;
  }
  const requestGeneration = ++generation;
  const token = session.token;
  const id = recordId.value;
  claimSequence += 1;
  prize.value = null;
  claiming.value = false;
  loading.value = false;
  errorMessage.value = "";
  if (!session.authenticated || !Number.isSafeInteger(id) || id <= 0) {
    return;
  }
  const cached = preview.prize;
  if (cached && cached.recordId === id) {
    prize.value = cached;
    return;
  }
  loading.value = true;
  const current = () => !disposed && generation === requestGeneration
    && token === session.token && id === recordId.value;
  try {
    let page = 1;
    let readCount = 0;
    let scanLimit: number | undefined;
    while (current()) {
      const result = await fetchPrizeList({ tab: "ALL", page, pageSize: 50 });
      if (!current()) {
        return;
      }
      if (!isOk(result) || !result.data) {
        errorMessage.value = resultMessage(result);
        return;
      }
      const rows = result.data.records ?? [];
      const found = rows.find((row) => row.recordId === id);
      if (found) {
        prize.value = found;
        return;
      }
      readCount += rows.length;
      const reportedTotal = Number(result.data.total ?? readCount);
      // Bound the lookup by the initial total even if more awards arrive while reading.
      scanLimit = scanLimit == null ? reportedTotal : Math.min(scanLimit, reportedTotal);
      if (rows.length === 0 || !Number.isFinite(scanLimit) || readCount >= scanLimit) {
        return;
      }
      page += 1;
    }
  } catch {
    if (current()) {
      errorMessage.value = zhCN.common.networkError;
    }
  } finally {
    if (current()) {
      loading.value = false;
    }
  }
}

async function onClaim(): Promise<void> {
  if (disposed || !session.authenticated || claiming.value || !prize.value || !button.value
    || button.value.disabled || prize.value.recordId == null) {
    return;
  }
  const claimId = prize.value.recordId;
  const row = prize.value;
  const token = session.token;
  const id = recordId.value;
  const requestGeneration = generation;
  const sequence = ++claimSequence;
  const current = () => !disposed && generation === requestGeneration && sequence === claimSequence
    && token === session.token && id === recordId.value;
  claiming.value = true;
  try {
    const result = await claimPrize(claimId);
    if (!current()) {
      return;
    }
    if (!isOk(result) || !result.data) {
      showPortalFail(result);
      return;
    }
    const next = {
      ...row,
      status: result.data.status,
      fulfillmentStatus: result.data.fulfillmentStatus,
    };
    prize.value = next;
    preview.set(next);
    showToast(prizeButtonState(next).label);
  } catch {
    if (current()) {
      showNetworkFail();
    }
  } finally {
    if (current()) {
      claiming.value = false;
    }
  }
}

function requestLogin(): void {
  overlay.request({ redirect: route.fullPath });
}

function openSource(): void {
  if (prize.value?.sourceTaskId == null) {
    return;
  }
  void router.push(`/task/${prize.value.sourceTaskId}`);
}

watch(() => [session.token, recordId.value], () => void load(), { immediate: true, flush: "sync" });
onScopeDispose(() => {
  disposed = true;
  generation += 1;
  claimSequence += 1;
});
</script>

<template>
  <section class="prize-detail">
    <NavBar :title="zhCN.prize.detailTitle" left-arrow @click-left="router.back()" />
    <Empty v-if="!session.authenticated" :description="zhCN.session.missing" data-testid="prize-detail-login">
      <Button type="primary" size="small" data-testid="prize-detail-login-action" @click="requestLogin">
        {{ zhCN.login.submit }}
      </Button>
    </Empty>
    <div v-else-if="loading" role="status" class="prize-detail__status" data-testid="prize-detail-loading">
      {{ zhCN.common.loading }}
    </div>
    <div v-else-if="errorMessage" role="alert" class="prize-detail__status" data-testid="prize-detail-error">
      <p>{{ errorMessage }}</p>
      <Button type="primary" size="small" data-testid="prize-detail-retry" @click="load">
        {{ zhCN.common.retry }}
      </Button>
    </div>
    <Empty v-else-if="!prize" :description="zhCN.empty.prizes" data-testid="prize-detail-empty" />
    <div v-else class="prize-detail__body" data-testid="prize-detail">
      <header class="prize-detail__hero">
        <FallbackImage :src="prize.prizeImage" :alt="prize.prizeName ?? zhCN.mine.prizes" />
        <div>
          <h2 data-testid="prize-detail-name">{{ prize.prizeName }}</h2>
        </div>
      </header>
      <dl class="prize-detail__facts">
        <div data-testid="prize-type">
          <dt>{{ zhCN.prize.prizeType }}</dt>
          <dd>{{ prize.categoryCode || "—" }}</dd>
        </div>
        <div data-testid="prize-expire-at">
          <dt>{{ zhCN.prize.expireAt }}</dt>
          <dd>{{ prize.expireAt ? formatBeijing(prize.expireAt) : "—" }}</dd>
        </div>
        <div data-testid="prize-obtained-at">
          <dt>{{ zhCN.prize.obtainedAt }}</dt>
          <dd>{{ prize.obtainedAt ? formatBeijing(prize.obtainedAt) : "—" }}</dd>
        </div>
        <div data-testid="prize-claimed-at">
          <dt>{{ zhCN.prize.claimedAt }}</dt>
          <dd>{{ prize.claimedAt ? formatBeijing(prize.claimedAt) : "—" }}</dd>
        </div>
        <div v-if="activityLabel" data-testid="prize-activity">
          <dt>{{ zhCN.activity.owner }}</dt>
          <dd>{{ activityLabel }}</dd>
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
          :disabled="button?.disabled || claiming"
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
.prize-detail__status {
  padding: 24px 16px;
  text-align: center;
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
