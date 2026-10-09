<script setup lang="ts">
import { computed, onScopeDispose, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Button, Empty, List, NavBar, PullRefresh, Tab, Tabs, showToast } from "vant";
import { isOk } from "@mkt/shared";
import { claimPrize, fetchPrizeList, type PrizeCardView, type PrizeTab } from "@/api/prize";
import PrizeCard from "@/components/PrizeCard.vue";
import { usePagedList } from "@/composables/usePagedList";
import { useLoginOverlayStore } from "@/store/login-overlay";
import { usePrizePreviewStore } from "@/store/prize-preview";
import { useSessionStore } from "@/store/session";
import { zhCN } from "@/locales/zh-CN";
import { TRACK, track } from "@/tracking";
import { showNetworkFail, showPortalFail } from "@/utils/portal-error";
import { prizeButtonState } from "@/utils/prize-button";

defineOptions({ name: "MinePrizesPage" });

const PAGE_SIZE = 20;
const TABS: { name: PrizeTab; title: string }[] = [
  { name: "ALL", title: zhCN.prize.allTab },
  { name: "PENDING", title: zhCN.prize.pendingTab },
];

const route = useRoute();
const router = useRouter();
const session = useSessionStore();
const overlay = useLoginOverlayStore();
const preview = usePrizePreviewStore();
const isTabRoot = computed(() => route.meta.tab === "prizes");
const activeTab = ref<PrizeTab>("ALL");
const claiming = ref<number | null>(null);
let claimSequence = 0;
const {
  records,
  loading,
  finished,
  refreshing,
  error,
  errorMessage,
  empty,
  loadMore,
  reload,
  refresh,
  retry,
} = usePagedList<PrizeCardView>({
  scope: () => [session.token, activeTab.value],
  enabled: () => session.authenticated,
  fetchPage: (page, pageSize) => fetchPrizeList({ tab: activeTab.value, page, pageSize }),
  pageSize: PAGE_SIZE,
});

function reportView(tab: PrizeTab): void {
  track(TRACK.REWARD_LIST_VIEW, { tab });
}

function requestLogin(): void {
  overlay.request({ redirect: route.fullPath });
}

function openDetail(row: PrizeCardView): void {
  if (row.recordId == null) {
    return;
  }
  preview.set(row);
  void router.push(`/mine/prizes/${row.recordId}`);
}

async function onClaim(row: PrizeCardView): Promise<void> {
  const state = prizeButtonState(row);
  if (!session.authenticated || state.disabled || row.recordId == null || claiming.value != null) {
    return;
  }
  track(TRACK.REWARD_CLAIM_CLICK, { recordId: row.recordId });
  const token = session.token;
  const sequence = ++claimSequence;
  const isCurrent = () => token === session.token && sequence === claimSequence;
  claiming.value = row.recordId;
  try {
    const result = await claimPrize(row.recordId);
    if (!isCurrent()) {
      return;
    }
    if (!isOk(result) || !result.data) {
      showPortalFail(result);
      return;
    }
    const next: PrizeCardView = {
      ...row,
      status: result.data.status,
      fulfillmentStatus: result.data.fulfillmentStatus,
    };
    const mapped = prizeButtonState(next);
    showToast(mapped.label);
    await reload();
  } catch {
    if (isCurrent()) {
      showNetworkFail();
    }
  } finally {
    if (isCurrent()) {
      claiming.value = null;
    }
  }
}

watch(activeTab, reportView, { immediate: true });
watch(
  () => session.token,
  () => {
    claimSequence += 1;
    claiming.value = null;
    if (session.authenticated) {
      reportView(activeTab.value);
    }
  },
  { flush: "sync" },
);
onScopeDispose(() => {
  claimSequence += 1;
});
</script>

<template>
  <section class="mine-prizes">
    <NavBar :title="zhCN.mine.prizes" :left-arrow="!isTabRoot" @click-left="isTabRoot ? undefined : router.back()" />
    <Tabs v-model:active="activeTab" data-testid="mine-prize-tabs">
      <Tab
        v-for="tab in TABS"
        :key="tab.name"
        :title="tab.title"
        :name="tab.name"
        :data-testid="'prize-tab-' + tab.name"
      />
    </Tabs>
    <PullRefresh v-model="refreshing" class="mine-list" data-testid="mine-list" @refresh="refresh">
      <Empty v-if="!session.authenticated" :description="zhCN.session.missing" data-testid="mine-prizes-login">
        <Button type="primary" size="small" data-testid="mine-prizes-login-action" @click="requestLogin">
          {{ zhCN.login.submit }}
        </Button>
      </Empty>
      <Empty v-else-if="error && records.length === 0" :description="errorMessage" data-testid="mine-prizes-error">
        <Button type="primary" size="small" data-testid="mine-prizes-retry" @click="retry">
          {{ zhCN.common.retry }}
        </Button>
      </Empty>
      <Empty v-else-if="empty" :description="zhCN.empty.prizes" data-testid="mine-prizes-empty">
        <Button type="primary" size="small" data-testid="empty-go-home" @click="router.push('/home')">
          {{ zhCN.empty.goTasks }}
        </Button>
      </Empty>
      <List
        v-else
        v-model:loading="loading"
        v-model:error="error"
        :error-text="errorMessage"
        :finished="finished"
        :finished-text="zhCN.task.noMore"
        :immediate-check="false"
        data-testid="mine-prizes-list"
        @load="loadMore"
      >
        <PrizeCard
          v-for="row in records"
          :key="row.recordId"
          :prize="row"
          :claiming="claiming === row.recordId"
          @claim="onClaim(row)"
          @detail="openDetail(row)"
        />
      </List>
    </PullRefresh>
  </section>
</template>

<style scoped>
.mine-prizes {
  min-height: 100%;
  background: var(--portal-bg);
}
.mine-prizes :deep(.van-tabs__wrap),
.mine-prizes :deep(.van-tabs__nav) {
  background: var(--portal-bg);
}
.mine-list {
  padding-top: 12px;
}
</style>
