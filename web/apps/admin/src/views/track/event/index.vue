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
    <h2>{{ zhCN.trackEvent.title }}</h2>
    <p class="hint" data-testid="debug-no-side-effect">{{ zhCN.trackEvent.hint }}</p>
    <div class="admin-toolbar">
      <input v-model="filters.eventCode" data-testid="filter-code" :placeholder="zhCN.trackEvent.eventCode" />
      <input v-model="filters.userId" data-testid="filter-user" :placeholder="zhCN.trackEvent.userId" />
      <select v-model="filters.source" data-testid="filter-source">
        <option value="">{{ zhCN.trackEvent.source }}</option>
        <option v-for="item in TRACK_SOURCES" :key="item" :value="item">{{ item }}</option>
      </select>
      <input v-model="filters.deviceId" data-testid="filter-device" :placeholder="zhCN.trackEvent.deviceId" />
      <input v-model="filters.from" data-testid="filter-from" type="datetime-local" />
      <input v-model="filters.to" data-testid="filter-to" type="datetime-local" />
      <button v-auth="PERMS.TRACK_EVENT_QUERY" type="button" data-testid="debug-query" @click="load">
        {{ zhCN.common.query }}
      </button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="debug-table">
      <thead>
        <tr>
          <th>{{ zhCN.trackEvent.source }}</th>
          <th>{{ zhCN.trackEvent.eventCode }}</th>
          <th>{{ zhCN.trackEvent.userId }}</th>
          <th>{{ zhCN.trackEvent.deviceId }}</th>
          <th>{{ zhCN.trackEvent.registered }}</th>
          <th>{{ zhCN.trackEvent.simulated }}</th>
          <th>{{ zhCN.trackEvent.serverTime }}</th>
          <th>{{ zhCN.trackEvent.events }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in records" :key="row.id">
          <td>{{ row.source }}</td>
          <td>{{ row.eventCode }}</td>
          <td>{{ row.userId }}</td>
          <td>{{ row.deviceId }}</td>
          <td>{{ row.registered ? zhCN.common.enabled : zhCN.common.disabled }}</td>
          <td>{{ row.simulated ? zhCN.common.enabled : zhCN.common.disabled }}</td>
          <td>{{ formatDateTime(row.serverTime) }}</td>
          <td data-testid="debug-events">{{ eventsText(row) }}</td>
        </tr>
      </tbody>
    </table>
    <div class="pager">
      <span>{{ zhCN.common.total }} {{ total }}</span>
      <button type="button" :disabled="page <= 1" @click="page -= 1; load()">{{ zhCN.common.page }} -</button>
      <span>{{ page }}</span>
      <button type="button" :disabled="page * pageSize >= total" @click="page += 1; load()">{{ zhCN.common.page }} +</button>
    </div>
  </section>
</template>
