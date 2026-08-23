<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { debugEvents, type TrackDebugEventResponse } from "@/api/track";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import { PERMS } from "@/constants/identity";
import { TRACK_SOURCES } from "@/constants/track";
import { zhCN } from "@/locales/zh-CN";
import { formatDateTime, toIsoInstant } from "@/utils/datetime";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";
import { ADMIN_PAGE_SIZE, adminPagination, adminRowKey } from "@/utils/table";

defineOptions({ name: "TrackEventPage" });

const records = ref<TrackDebugEventResponse[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ADMIN_PAGE_SIZE;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const filters = reactive({
  eventCode: "",
  userId: "",
  source: "",
  deviceId: "",
  from: "",
  to: "",
});

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await debugEvents({
    eventCode: filters.eventCode,
    userId: filters.userId ? Number(filters.userId) : undefined,
    source: filters.source,
    deviceId: filters.deviceId,
    from: toIsoInstant(filters.from),
    to: toIsoInstant(filters.to),
    page: page.value,
    pageSize,
  });
  const parsed = okOrFeedback(result);
  loading.value = false;
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  records.value = parsed.data?.records ?? [];
  total.value = parsed.data?.total ?? 0;
}

function eventsText(row: TrackDebugEventResponse): string {
  return JSON.stringify(row.events ?? []);
}


function onTableChange(pag: { current?: number }): void {
  page.value = pag.current ?? 1;
  void load();
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="track-event-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.trackEvent.title }}</h2>
    </div>
    <p class="hint" data-testid="debug-no-side-effect">{{ zhCN.trackEvent.hint }}</p>
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-input v-model:value="filters.eventCode" data-testid="filter-code" :placeholder="zhCN.trackEvent.eventCode" />
      <a-input v-model:value="filters.userId" data-testid="filter-user" :placeholder="zhCN.trackEvent.userId" />
      <a-select v-model:value="filters.source" data-testid="filter-source">
        <a-select-option value="">{{ zhCN.trackEvent.source }}</a-select-option>
        <a-select-option v-for="item in TRACK_SOURCES" :key="item" :value="item">{{ item }}</a-select-option>
      </a-select>
      <a-input v-model:value="filters.deviceId" data-testid="filter-device" :placeholder="zhCN.trackEvent.deviceId" />
      <a-date-picker v-model:value="filters.from" data-testid="filter-from" show-time value-format="YYYY-MM-DDTHH:mm" format="YYYY-MM-DD HH:mm" />
      <a-date-picker v-model:value="filters.to" data-testid="filter-to" show-time value-format="YYYY-MM-DDTHH:mm" format="YYYY-MM-DD HH:mm" />
      <a-button type="primary" v-auth="PERMS.TRACK_EVENT_QUERY" data-testid="debug-query" @click="load">
        {{ zhCN.common.query }}
      </a-button>
    </a-form>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="records" class="data-table admin-table" data-testid="debug-table" :pagination="adminPagination(page, pageSize, total)" :row-key="adminRowKey" @change="onTableChange">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty" />
      </template>

      <a-table-column :title="zhCN.trackEvent.source">
        <template #default="{ record: row }">{{ row.source }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.trackEvent.eventCode">
        <template #default="{ record: row }">{{ row.eventCode }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.trackEvent.userId">
        <template #default="{ record: row }">{{ row.userId }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.trackEvent.deviceId">
        <template #default="{ record: row }">{{ row.deviceId }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.trackEvent.registered">
        <template #default="{ record: row }">{{ row.registered ? zhCN.common.enabled : zhCN.common.disabled }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.trackEvent.simulated">
        <template #default="{ record: row }">{{ row.simulated ? zhCN.common.enabled : zhCN.common.disabled }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.trackEvent.serverTime">
        <template #default="{ record: row }">{{ formatDateTime(row.serverTime) }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.trackEvent.events">
        <template #default="{ record: row }"><span data-testid="debug-events">{{ eventsText(row) }}</span></template>
      </a-table-column>
    </a-table>
  </section>
</template>
