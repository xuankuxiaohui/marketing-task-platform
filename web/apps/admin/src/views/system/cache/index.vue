<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { evictCache, fetchCacheStats, type CacheStatsView } from "@/api/system";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import { PERMS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { buildCacheEvictCommand, CACHE_EVICT_LEVELS, SESSION_NAMESPACE } from "@/utils/cache-evict";
import { okOrFeedback, writeOrFeedback, type PageFeedback } from "@/utils/feedback";
import { adminRowKey } from "@/utils/table";

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
  const parsed = writeOrFeedback(result);
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
    <div class="admin-page__header">
      <h2>{{ zhCN.cache.title }}</h2>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="success" class="hint" data-testid="cache-success">{{ success }}</p>
    <a-table size="small" :loading="loading" :data-source="records" class="data-table admin-table" data-testid="cache-table" :pagination="false" :row-key="adminRowKey">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty" />
      </template>

      <a-table-column :title="zhCN.cache.namespace" :width="240">
        <template #default="{ record: row }">
          {{ row.namespace }}
            <span v-if="row.namespace === SESSION_NAMESPACE" class="hint">({{ zhCN.cache.sessionForbidden }})</span>
        </template>
      </a-table-column>
      <a-table-column :title="zhCN.cache.keyCount">
        <template #default="{ record: row }">{{ row.keyCount }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.cache.hitRate">
        <template #default="{ record: row }">{{ row.hitRate }}</template>
      </a-table-column>
    </a-table>
    <a-form class="evict-form" data-testid="cache-evict-form" @submit.prevent="submitEvict">
      <a-form-item :label="zhCN.cache.level">
        <a-select v-model:value="form.level" data-testid="evict-level" @change="onLevelChange">
        <a-select-option v-for="item in CACHE_EVICT_LEVELS" :key="item" :value="item">{{ item }}</a-select-option>
      </a-select>
      </a-form-item>
      <a-form-item :label="zhCN.cache.namespace">
        <a-select v-model:value="form.namespace" data-testid="evict-namespace">
        <a-select-option value="">{{ zhCN.cache.namespace }}</a-select-option>
        <a-select-option v-for="row in records" :key="row.namespace" :value="row.namespace">{{ row.namespace }}</a-select-option>
      </a-select>
      </a-form-item>
      <a-form-item v-if="showPrefix" :label="zhCN.cache.prefix">
        <a-input v-model:value="form.prefix" data-testid="evict-prefix" />
      </a-form-item>
      <a-form-item v-if="showKey" :label="zhCN.cache.key">
        <a-input v-model:value="form.key" data-testid="evict-key" />
      </a-form-item>
      <a-button v-auth="PERMS.CACHE_EVICT" html-type="submit" type="primary" data-testid="cache-evict">{{ zhCN.cache.evict }}</a-button>
    </a-form>
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
