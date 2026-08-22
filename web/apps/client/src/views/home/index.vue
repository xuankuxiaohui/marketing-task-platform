<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { Button, Empty, NavBar, PullRefresh } from "vant";
import { isOk } from "@mkt/shared";
import { fetchActivities, type PortalActivityView } from "@/api/activity";
import AdCarousel from "@/components/AdCarousel.vue";
import { zhCN } from "@/locales/zh-CN";
import { showNetworkFail, showPortalFail } from "@/utils/portal-error";

defineOptions({ name: "HomePage" });

const router = useRouter();
const activities = ref<PortalActivityView[]>([]);
const refreshing = ref(false);
const loaded = ref(false);

const empty = computed(() => loaded.value && activities.value.length === 0);

async function load(): Promise<void> {
  try {
    const result = await fetchActivities();
    if (!isOk(result) || !result.data) {
      showPortalFail(result);
      activities.value = [];
      return;
    }
    activities.value = result.data;
  } catch {
    showNetworkFail();
    activities.value = [];
  } finally {
    refreshing.value = false;
    loaded.value = true;
  }
}

function onRefresh(): void {
  void load();
}

function openActivity(activity: PortalActivityView): void {
  void router.push({ path: "/activity", query: { id: String(activity.id) } });
}

function openSignin(): void {
  void router.push("/signin");
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="home-page">
    <NavBar :title="zhCN.home.title" />
    <AdCarousel position-code="home_banner" />
    <PullRefresh v-model="refreshing" @refresh="onRefresh">
      <article
        class="hub-card"
        data-testid="home-signin-card"
        role="button"
        tabindex="0"
        @click="openSignin"
      >
        <strong>{{ zhCN.home.signin }}</strong>
        <span>{{ zhCN.home.signinHint }}</span>
      </article>
      <Empty v-if="empty" :description="zhCN.home.empty" data-testid="home-empty">
        <Button type="primary" size="small" data-testid="home-retry" @click="load">
          {{ zhCN.common.retry }}
        </Button>
      </Empty>
      <div v-else data-testid="home-activity-list">
        <article
          v-for="activity in activities"
          :key="activity.id"
          class="hub-card"
          :data-testid="`home-activity-${activity.id}`"
          role="button"
          tabindex="0"
          @click="openActivity(activity)"
        >
          <strong>{{ activity.name }}</strong>
          <span v-if="activity.code">{{ activity.code }}</span>
        </article>
      </div>
    </PullRefresh>
  </section>
</template>

<style scoped>
.hub-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 12px 16px;
  padding: 12px;
  border-radius: 12px;
  background: #fff;
  text-align: left;
}
.hub-card strong {
  font-size: 15px;
}
.hub-card span {
  color: #646566;
  font-size: 13px;
}
</style>
