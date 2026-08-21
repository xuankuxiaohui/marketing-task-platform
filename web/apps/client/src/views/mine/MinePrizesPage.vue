<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { Button, Empty, List, NavBar, PullRefresh, Tab, Tabs, showToast } from "vant";
import { isOk } from "@mkt/shared";
import { claimPrize, fetchPrizeList, type PrizeCardView, type PrizeTab } from "@/api/prize";
import PrizeCard from "@/components/PrizeCard.vue";
import { zhCN } from "@/locales/zh-CN";
import { TRACK, track } from "@/tracking";
import { showNetworkFail, showPortalFail } from "@/utils/portal-error";
import { isPendingPrizeTab, prizeButtonState } from "@/utils/prize-button";

defineOptions({ name: "MinePrizesPage" });

const PAGE_SIZE = 20;
const TABS: { name: PrizeTab; title: string }[] = [
  { name: "PENDING", title: zhCN.prize.pendingTab },
  { name: "ALL", title: zhCN.prize.allTab },
];

const router = useRouter();
const activeTab = ref<PrizeTab>("PENDING");
const records = ref<PrizeCardView[]>([]);
const page = ref(1);
const total = ref(0);
const loading = ref(false);
const finished = ref(false);
const refreshing = ref(false);
const loaded = ref(false);
const claiming = ref<number | null>(null);

const empty = computed(() => loaded.value && records.value.length === 0);

function reportView(tab: PrizeTab): void {
  track(TRACK.REWARD_LIST_VIEW, { tab });
}

async function loadPage(reset: boolean): Promise<void> {
  if (reset) {
    page.value = 1;
    finished.value = false;
  }
  loading.value = true;
  try {
    const result = await fetchPrizeList({
      tab: activeTab.value,
      page: page.value,
      pageSize: PAGE_SIZE,
    });
    if (!isOk(result) || !result.data) {
      showPortalFail(result);
      finished.value = true;
      return;
    }
    const next = result.data.records ?? [];
    total.value = Number(result.data.total ?? 0);
    records.value = reset ? next : [...records.value, ...next];
    page.value += 1;
    finished.value = records.value.length >= total.value || next.length === 0;
  } catch {
    showNetworkFail();
    finished.value = true;
  } finally {
    loading.value = false;
    refreshing.value = false;
    loaded.value = true;
  }
}

function onRefresh(): void {
  void loadPage(true);
}

function onLoadMore(): void {
  if (refreshing.value || loading.value) {
    return;
  }
  void loadPage(false);
}

function openSource(row: PrizeCardView): void {
  if (row.sourceTaskId == null) {
    return;
  }
  void router.push(`/task/${row.sourceTaskId}`);
}

async function onClaim(row: PrizeCardView): Promise<void> {
  const state = prizeButtonState(row);
  if (state.disabled || row.recordId == null || claiming.value != null) {
    return;
  }
  track(TRACK.REWARD_CLAIM_CLICK, { recordId: row.recordId });
  claiming.value = row.recordId;
  try {
    const result = await claimPrize(row.recordId);
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
    if (activeTab.value === "PENDING" && !isPendingPrizeTab(next)) {
      records.value = records.value.filter((item) => item.recordId !== row.recordId);
      total.value = Math.max(0, total.value - 1);
      return;
    }
    records.value = records.value.map((item) => (item.recordId === row.recordId ? next : item));
  } catch {
    showNetworkFail();
  } finally {
    claiming.value = null;
  }
}

watch(activeTab, (tab) => {
  reportView(tab);
  void loadPage(true);
});

onMounted(() => {
  reportView(activeTab.value);
  void loadPage(true);
});
</script>

<template>
  <section class="mine-prizes">
    <NavBar :title="zhCN.mine.prizes" left-arrow @click-left="router.back()" />
    <Tabs v-model:active="activeTab" sticky>
      <Tab v-for="tab in TABS" :key="tab.name" :title="tab.title" :name="tab.name" />
    </Tabs>
    <PullRefresh v-model="refreshing" @refresh="onRefresh">
      <Empty v-if="empty" :description="zhCN.empty.prizes" data-testid="mine-prizes-empty">
        <Button type="primary" size="small" data-testid="empty-go-home" @click="router.push('/home')">
          {{ zhCN.empty.goTasks }}
        </Button>
      </Empty>
      <List
        v-else
        v-model:loading="loading"
        :finished="finished"
        :finished-text="zhCN.task.noMore"
        :immediate-check="false"
        data-testid="mine-prizes-list"
        @load="onLoadMore"
      >
        <PrizeCard
          v-for="row in records"
          :key="row.recordId"
          :prize="row"
          :claiming="claiming === row.recordId"
          @claim="onClaim(row)"
          @source="openSource(row)"
        />
      </List>
    </PullRefresh>
  </section>
</template>
