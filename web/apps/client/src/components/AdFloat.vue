<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { showToast } from "vant";
import { isOk } from "@mkt/shared";
import { dismissAdMaterial, fetchAdPosition, type PortalAdMaterialView } from "@/api/ad";
import FallbackImage from "@/components/FallbackImage.vue";
import { zhCN } from "@/locales/zh-CN";
import { TRACK, track } from "@/tracking";
import { resolveAdJump } from "@/utils/ad-jump";

defineOptions({ name: "AdFloat" });

const POSITION = "home_float";

const router = useRouter();
const material = ref<PortalAdMaterialView | null>(null);

async function close(): Promise<void> {
  const current = material.value;
  material.value = null;
  if (!current) {
    return;
  }
  try {
    await dismissAdMaterial(current.materialId, POSITION);
  } catch {
    /* local hide still applies */
  }
}

async function onClick(): Promise<void> {
  const current = material.value;
  if (!current) {
    return;
  }
  track(TRACK.AD_FLOAT_CLICK, { positionCode: POSITION, materialTrackId: current.trackId });
  const jump = resolveAdJump(current);
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

onMounted(async () => {
  try {
    const result = await fetchAdPosition(POSITION);
    if (!isOk(result) || !result.data?.materials?.length) {
      return;
    }
    const first = result.data.materials[0];
    material.value = first;
    track(TRACK.AD_FLOAT_EXPOSURE, { positionCode: POSITION, materialTrackId: first.trackId });
  } catch {
    material.value = null;
  }
});
</script>

<template>
  <div v-if="material" class="ad-float" data-testid="ad-float">
    <button type="button" class="ad-float__close" data-testid="ad-float-close" @click="close">
      {{ zhCN.ad.close }}
    </button>
    <button type="button" class="ad-float__hit" data-testid="ad-float-hit" @click="onClick">
      <FallbackImage :src="material.imageUrl" :alt="material.title || zhCN.ad.float" />
    </button>
  </div>
</template>

<style scoped>
.ad-float {
  position: fixed;
  right: 12px;
  bottom: 88px;
  z-index: 20;
}
.ad-float__hit {
  display: block;
  padding: 0;
  border: 0;
  background: transparent;
}
.ad-float__hit :deep(.fallback-image),
.ad-float__hit :deep(img) {
  width: 64px;
  height: 64px;
  border-radius: 32px;
  object-fit: cover;
}
.ad-float__close {
  position: absolute;
  top: -10px;
  right: -6px;
  z-index: 1;
  padding: 2px 6px;
  border: 0;
  border-radius: 10px;
  background: rgb(0 0 0 / 55%);
  color: #fff;
  font-size: 10px;
}
</style>
