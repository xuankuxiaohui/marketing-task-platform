<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Button, Empty, NavBar, showFailToast, showSuccessToast } from "vant";
import { isFail, isOk } from "@mkt/shared";
import { fetchActivities, fetchActivityDetail, postParticipate, type PortalActivityDetailView } from "@/api/activity";
import { zhCN } from "@/locales/zh-CN";

defineOptions({ name: "ActivityPage" });

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const detail = ref<PortalActivityDetailView | null>(null);
const result = ref<string | null>(null);

const activityId = computed(() => {
  const raw = route.query.id;
  if (typeof raw === "string" && raw) {
    return Number(raw);
  }
  return null;
});

async function load(): Promise<void> {
  loading.value = true;
  try {
    let id = activityId.value;
    if (id == null || !Number.isFinite(id)) {
      const list = await fetchActivities();
      if (isOk(list) && list.data && list.data.length > 0) {
        id = list.data[0].id;
      }
    }
    if (id == null || !Number.isFinite(id)) {
      detail.value = null;
      return;
    }
    const response = await fetchActivityDetail(id);
    if (isFail(response)) {
      showFailToast(response.message);
      detail.value = null;
      return;
    }
    detail.value = response.data ?? null;
  } finally {
    loading.value = false;
  }
}

function submodulePath(type: string, refId: number): string {
  if (type === "TASK") {
    return `/task/${refId}`;
  }
  if (type === "SIGNIN") {
    return "/signin";
  }
  return "/mine/prizes";
}

async function onParticipate(): Promise<void> {
  if (!detail.value) {
    return;
  }
  const response = await postParticipate(detail.value.id);
  if (isFail(response)) {
    showFailToast(response.message);
    return;
  }
  result.value = response.data?.result ?? "";
  if (response.data?.result === "PASS") {
    showSuccessToast(zhCN.activity.joined);
  } else {
    showFailToast(zhCN.activity.rejected);
  }
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="activity-page">
    <NavBar :title="zhCN.activity.title" left-arrow @click-left="router.back()" />
    <Empty v-if="!loading && !detail" :description="zhCN.activity.empty" data-testid="activity-empty" />
    <div v-else-if="detail" data-testid="activity-detail">
      <h2 data-testid="activity-name">{{ detail.name }}</h2>
      <!-- richText is server-sanitized (R22); do not bind unsanitized HTML -->
      <!-- eslint-disable-next-line vue/no-v-html -->
      <div class="activity-html" data-testid="activity-html" v-html="detail.richText" />
      <ul v-if="detail.submodules.length" data-testid="activity-submodules">
        <li v-for="item in detail.submodules" :key="`${item.type}-${item.refId}`">
          <button type="button" @click="router.push(submodulePath(item.type, item.refId))">
            {{ item.type }} #{{ item.refId }}
          </button>
        </li>
      </ul>
      <p v-if="result" data-testid="activity-result">{{ result }}</p>
      <Button type="primary" block data-testid="activity-join" @click="onParticipate">
        {{ zhCN.activity.join }}
      </Button>
    </div>
  </section>
</template>

<style scoped>
.activity-html {
  padding: 12px 16px;
  font-size: 14px;
  line-height: 1.6;
}
</style>
