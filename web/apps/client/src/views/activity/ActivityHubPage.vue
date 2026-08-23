<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { Empty, NavBar, showToast } from "vant";
import { isOk } from "@mkt/shared";
import { fetchAdPosition, type PortalAdMaterialView } from "@/api/ad";
import FallbackImage from "@/components/FallbackImage.vue";
import { zhCN } from "@/locales/zh-CN";
import { TRACK, track } from "@/tracking";
import { resolveAdJump } from "@/utils/ad-jump";

defineOptions({ name: "ActivityHubPage" });

const POSITION = "home_banner";

const router = useRouter();
const materials = ref<PortalAdMaterialView[]>([]);
const loaded = ref(false);

async function load(): Promise<void> {
  try {
    const result = await fetchAdPosition(POSITION);
    materials.value = isOk(result) && result.data ? (result.data.materials ?? []) : [];
  } catch {
    materials.value = [];
  } finally {
    loaded.value = true;
  }
}

async function onClick(material: PortalAdMaterialView): Promise<void> {
  track(TRACK.AD_CAROUSEL_CLICK, {
    positionCode: POSITION,
    materialTrackId: material.trackId,
  });
  const jump = resolveAdJump(material);
  if (!jump) {
    showToast(zhCN.ad.jumpUnavailable);
    return;
  }
  if (jump.kind === "link") {
    window.open(jump.target, "_blank", "noopener");
    return;
  }
  if (jump.kind === "scheme") {
    window.location.href = jump.target;
    return;
  }
  await router.push(jump.target);
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="activity-hub">
    <NavBar :title="zhCN.mine.activityHub" left-arrow @click-left="router.back()" />
    <Empty v-if="loaded && materials.length === 0" :description="zhCN.activity.empty" data-testid="activity-hub-empty" />
    <div v-else data-testid="activity-hub-list">
      <button
        v-for="item in materials"
        :key="item.materialId"
        type="button"
        class="activity-hub__banner"
        data-testid="activity-hub-banner"
        @click="onClick(item)"
      >
        <FallbackImage :src="item.imageUrl" :alt="item.title || zhCN.ad.carousel" />
        <span v-if="item.title" class="activity-hub__title">{{ item.title }}</span>
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
.activity-hub__banner {
  position: relative;
  display: block;
  overflow: hidden;
  width: calc(100% - 32px);
  margin: 12px 16px 0;
  padding: 0;
  border: 0;
  border-radius: var(--portal-radius-lg);
  background: var(--portal-surface);
  box-shadow: var(--portal-shadow);
}
.activity-hub__banner :deep(.fallback-image),
.activity-hub__banner :deep(img) {
  width: 100%;
  height: 148px;
  border-radius: 0;
  object-fit: cover;
}
.activity-hub__title {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  padding: 24px 14px 12px;
  background: linear-gradient(180deg, transparent 0%, rgba(15, 23, 42, 0.72) 100%);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  text-align: left;
}
</style>
