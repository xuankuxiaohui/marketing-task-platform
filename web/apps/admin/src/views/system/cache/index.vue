<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { evictCache, fetchCacheStats, type CacheStatsView } from "@/api/system";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import { PERMS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { buildCacheEvictCommand, CACHE_EVICT_LEVELS, SESSION_NAMESPACE } from "@/utils/cache-evict";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

defineOptions({ name: "CacheManagePage" });

const records = ref<CacheStatsView[]>([]);
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const success = ref("");
const form = reactive({
  level: "NAMESPACE",
  namespace: "",
  prefix: "",
  key: "",
});

const showPrefix = computed(() => form.level === "PREFIX");
const showKey = computed(() => form.level === "KEY");

function onLevelChange(): void {
  if (form.level !== "PREFIX") {
    form.prefix = "";
  }
  if (form.level !== "KEY") {
    form.key = "";
  }
}

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await fetchCacheStats();
  const parsed = okOrFeedback(result);
  loading.value = false;
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  records.value = parsed.data?.records ?? [];
}

async function submitEvict(): Promise<void> {
  success.value = "";
  feedback.value = null;
  const built = buildCacheEvictCommand(form);
  if (!built.ok) {
    feedback.value = {
      message: built.reason === "session" ? zhCN.cache.sessionForbidden : zhCN.cache.paramInvalid,
    };
    return;
  }
  const result = await evictCache(built.body);
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  success.value = zhCN.cache.evicted
    .replace("{redis}", String(parsed.data?.evictedRedis ?? 0))
    .replace("{instances}", String(parsed.data?.notifiedInstances ?? 0));
  await load();
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="cache-page">
    <h2>{{ zhCN.cache.title }}</h2>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="success" class="hint" data-testid="cache-success">{{ success }}</p>
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="cache-table">
      <thead>
        <tr>
          <th>{{ zhCN.cache.namespace }}</th>
          <th>{{ zhCN.cache.keyCount }}</th>
          <th>{{ zhCN.cache.hitRate }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in records" :key="row.namespace">
          <td>
            {{ row.namespace }}
            <span v-if="row.namespace === SESSION_NAMESPACE" class="hint">({{ zhCN.cache.sessionForbidden }})</span>
          </td>
          <td>{{ row.keyCount }}</td>
          <td>{{ row.hitRate }}</td>
        </tr>
      </tbody>
    </table>
    <form class="evict-form" data-testid="cache-evict-form" @submit.prevent="submitEvict">
      <label class="field">
        <span>{{ zhCN.cache.level }}</span>
        <select v-model="form.level" data-testid="evict-level" @change="onLevelChange">
          <option v-for="item in CACHE_EVICT_LEVELS" :key="item" :value="item">{{ item }}</option>
        </select>
      </label>
      <label class="field">
        <span>{{ zhCN.cache.namespace }}</span>
        <select v-model="form.namespace" data-testid="evict-namespace">
          <option value="">{{ zhCN.cache.namespace }}</option>
          <option v-for="row in records" :key="row.namespace" :value="row.namespace">{{ row.namespace }}</option>
        </select>
      </label>
      <label v-if="showPrefix" class="field">
        <span>{{ zhCN.cache.prefix }}</span>
        <input v-model="form.prefix" data-testid="evict-prefix" />
      </label>
      <label v-if="showKey" class="field">
        <span>{{ zhCN.cache.key }}</span>
        <input v-model="form.key" data-testid="evict-key" />
      </label>
      <button v-auth="PERMS.CACHE_EVICT" type="submit" data-testid="cache-evict">{{ zhCN.cache.evict }}</button>
    </form>
  </section>
</template>

<style scoped>
.evict-form {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-end;
  background: #fff;
  padding: 12px;
  border-radius: 8px;
}
</style>
