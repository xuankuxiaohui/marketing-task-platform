<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { Empty, NavBar } from "vant";
import { isOk } from "@mkt/shared";
import { fetchActivities, type PortalActivityView } from "@/api/activity";
import FallbackImage from "@/components/FallbackImage.vue";
import { zhCN } from "@/locales/zh-CN";
import { activityCover, activityWindow } from "@/utils/activity-cover";
import { showNetworkFail } from "@/utils/portal-error";

defineOptions({ name: "ActivityHubPage" });

const router = useRouter();
const activities = ref<PortalActivityView[]>([]);
const loaded = ref(false);

async function load(): Promise<void> {
  try {
    const result = await fetchActivities();
    activities.value = isOk(result) && result.data ? result.data : [];
  } catch {
    showNetworkFail();
    activities.value = [];
  } finally {
    loaded.value = true;
  }
}

function openActivity(row: PortalActivityView): void {
  void router.push({ path: "/activity", query: { id: String(row.id) } });
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="activity-hub">
    <NavBar :title="zhCN.mine.activityHub" left-arrow @click-left="router.back()" />
    <Empty v-if="loaded && activities.length === 0" :description="zhCN.activity.empty" data-testid="activity-hub-empty" />
    <div v-else-if="activities.length > 0" data-testid="activity-hub-list">
      <button
        v-for="row in activities"
        :key="row.id"
        type="button"
        class="activity-hub__card"
        data-testid="activity-hub-card"
        @click="openActivity(row)"
      >
        <FallbackImage v-if="activityCover(row)" :src="activityCover(row)" :alt="row.name" />
        <span v-else class="activity-hub__fallback">{{ row.name.slice(0, 1) }}</span>
        <span class="activity-hub__meta">
          <strong>{{ row.name }}</strong>
          <em v-if="activityWindow(row.startTime, row.endTime)">{{
            activityWindow(row.startTime, row.endTime)
          }}</em>
        </span>
      </button>
    </div>
  </section>
</template>

<style scoped>
.activity-hub {
  min-height: 100%;
  padding-bottom: 16px;
  background: var(--portal-bg);
}
.activity-hub__card {
  display: flex;
  gap: 12px;
  align-items: center;
  width: calc(100% - 32px);
  margin: 12px 16px 0;
  padding: 12px;
  border: 0;
  border-radius: var(--portal-radius);
  background: var(--portal-surface);
  box-shadow: var(--portal-shadow-soft);
  text-align: left;
}
.activity-hub__card :deep(.fallback-image),
.activity-hub__fallback {
  width: 64px;
  height: 64px;
  flex: none;
  border-radius: 16px;
  background: var(--portal-primary-soft);
}
.activity-hub__fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--portal-primary);
  font-size: 22px;
  font-weight: 700;
}
.activity-hub__meta {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}
.activity-hub__meta strong {
  font-size: 16px;
}
.activity-hub__meta em {
  color: var(--portal-muted);
  font-size: 12px;
  font-style: normal;
}
</style>
