<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { debugEvents, type TrackDebugEventResponse } from "@/api/track";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import { PERMS } from "@/constants/identity";
import { TRACK_SOURCES } from "@/constants/track";
import { zhCN } from "@/locales/zh-CN";
import { formatDateTime, toIsoInstant } from "@/utils/datetime";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

defineOptions({ name: "TrackEventPage" });

const records = ref<TrackDebugEventResponse[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
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
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-input v-model="filters.eventCode" data-testid="filter-code" :placeholder="zhCN.trackEvent.eventCode" />
      <el-input v-model="filters.userId" data-testid="filter-user" :placeholder="zhCN.trackEvent.userId" />
      <el-select v-model="filters.source" data-testid="filter-source">
        <el-option value="" :label="zhCN.trackEvent.source" />
        <el-option v-for="item in TRACK_SOURCES" :key="item" :value="item" :label="item" />
      </el-select>
      <el-input v-model="filters.deviceId" data-testid="filter-device" :placeholder="zhCN.trackEvent.deviceId" />
      <el-input v-model="filters.from" data-testid="filter-from" type="datetime-local" />
      <el-input v-model="filters.to" data-testid="filter-to" type="datetime-local" />
      <el-button v-auth="PERMS.TRACK_EVENT_QUERY" data-testid="debug-query" @click="load">
        {{ zhCN.common.query }}
      </el-button>
    </el-form>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <div v-else-if="records.length === 0" data-testid="page-empty" class="page-empty">
      <span>{{ zhCN.common.empty }}</span>
    </div>
    <el-table v-else :data="records" class="data-table admin-table" data-testid="debug-table" size="small" stripe>
      <el-table-column :label="zhCN.trackEvent.source">
        <template #default="{ row }">{{ row.source }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.trackEvent.eventCode">
        <template #default="{ row }">{{ row.eventCode }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.trackEvent.userId">
        <template #default="{ row }">{{ row.userId }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.trackEvent.deviceId">
        <template #default="{ row }">{{ row.deviceId }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.trackEvent.registered">
        <template #default="{ row }">{{ row.registered ? zhCN.common.enabled : zhCN.common.disabled }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.trackEvent.simulated">
        <template #default="{ row }">{{ row.simulated ? zhCN.common.enabled : zhCN.common.disabled }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.trackEvent.serverTime">
        <template #default="{ row }">{{ formatDateTime(row.serverTime) }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.trackEvent.events">
        <template #default="{ row }"><span data-testid="debug-events">{{ eventsText(row) }}</span></template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <span>{{ zhCN.common.total }} {{ total }}</span>
      <el-button :disabled="page <= 1" @click="page -= 1; load()">{{ zhCN.common.prevPage }}</el-button>
      <span>{{ page }}</span>
      <el-button :disabled="page * pageSize >= total" @click="page += 1; load()">{{ zhCN.common.nextPage }}</el-button>
    </div>
  </section>
</template>
