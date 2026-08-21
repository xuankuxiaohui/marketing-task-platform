<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { showToast, Swipe, SwipeItem } from "vant";
import { isOk } from "@mkt/shared";
import { fetchAdPosition, type PortalAdMaterialView } from "@/api/ad";
import FallbackImage from "@/components/FallbackImage.vue";
import { zhCN } from "@/locales/zh-CN";
import { TRACK, track } from "@/tracking";
import { resolveAdJump } from "@/utils/ad-jump";

defineOptions({ name: "AdCarousel" });

const props = defineProps<{
  positionCode: string;
}>();

const router = useRouter();
const materials = ref<PortalAdMaterialView[]>([]);
const intervalMs = ref(5000);
const index = ref(0);
let exposureTimer: ReturnType<typeof setTimeout> | undefined;
const exposed = new Set<string>();

const visible = computed(() => materials.value.length > 0);

async function load(): Promise<void> {
  try {
    const result = await fetchAdPosition(props.positionCode);
    if (!isOk(result) || !result.data) {
      materials.value = [];
      return;
    }
    materials.value = result.data.materials ?? [];
    const seconds = result.data.carouselIntervalSeconds ?? 5;
    intervalMs.value = Math.max(2, seconds) * 1000;
  } catch {
    materials.value = [];
  }
}

function fireExposure(material: PortalAdMaterialView | undefined): void {
  if (!material || exposed.has(material.trackId)) {
    return;
  }
  exposed.add(material.trackId);
  track(TRACK.AD_CAROUSEL_EXPOSURE, {
    positionCode: props.positionCode,
    materialTrackId: material.trackId,
  });
}

function scheduleExposure(i: number): void {
  if (exposureTimer != null) {
    clearTimeout(exposureTimer);
  }
  const material = materials.value[i];
  exposureTimer = setTimeout(() => fireExposure(material), 1000);
}

function onChange(i: number): void {
  index.value = i;
  scheduleExposure(i);
}

async function onClick(material: PortalAdMaterialView): Promise<void> {
  track(TRACK.AD_CAROUSEL_CLICK, {
    positionCode: props.positionCode,
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
  void load().then(() => scheduleExposure(0));
});

watch(
  () => props.positionCode,
  () => {
    exposed.clear();
    void load().then(() => scheduleExposure(0));
  },
);

onBeforeUnmount(() => {
  if (exposureTimer != null) {
    clearTimeout(exposureTimer);
  }
});
</script>

<template>
  <section v-if="visible" class="ad-carousel" data-testid="ad-carousel">
    <Swipe :autoplay="intervalMs" indicator-color="#1989fa" @change="onChange">
      <SwipeItem v-for="item in materials" :key="item.materialId">
        <button type="button" class="ad-carousel__slide" data-testid="ad-carousel-slide" @click="onClick(item)">
          <FallbackImage :src="item.imageUrl" :alt="item.title || zhCN.ad.carousel" />
        </button>
      </SwipeItem>
    </Swipe>
  </section>
</template>

<style scoped>
.ad-carousel {
  margin: 8px 12px 0;
  overflow: hidden;
  border-radius: 8px;
}
.ad-carousel__slide {
  display: block;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
}
.ad-carousel :deep(.fallback-image) {
  width: 100%;
  height: 140px;
}
.ad-carousel :deep(img) {
  width: 100%;
  height: 140px;
  object-fit: cover;
}
</style>
