<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { Popup, showToast } from "vant";
import { isOk } from "@mkt/shared";
import { fetchAdPosition, type PortalAdMaterialView } from "@/api/ad";
import FallbackImage from "@/components/FallbackImage.vue";
import { zhCN } from "@/locales/zh-CN";
import { TRACK, track } from "@/tracking";
import { resolveAdJump } from "@/utils/ad-jump";

defineOptions({ name: "AdPopup" });

const POSITION = "home_popup";

const router = useRouter();
const material = ref<PortalAdMaterialView | null>(null);
const open = ref(false);

function close(): void {
  open.value = false;
}

async function onClick(): Promise<void> {
  const current = material.value;
  if (!current) {
    return;
  }
  track(TRACK.AD_POPUP_CLICK, { positionCode: POSITION, materialTrackId: current.trackId });
  const jump = resolveAdJump(current);
  close();
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
    open.value = true;
    track(TRACK.AD_POPUP_EXPOSURE, { positionCode: POSITION, materialTrackId: first.trackId });
  } catch {
    open.value = false;
  }
});
</script>

<template>
  <Popup v-model:show="open" round closeable :style="{ width: '80%' }" data-testid="ad-popup" @click-close-icon="close">
    <button v-if="material" type="button" class="ad-popup__hit" data-testid="ad-popup-hit" @click="onClick">
      <FallbackImage :src="material.imageUrl" :alt="material.title || zhCN.ad.popup" />
    </button>
  </Popup>
</template>

<style scoped>
.ad-popup__hit {
  display: block;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
}
.ad-popup__hit :deep(.fallback-image),
.ad-popup__hit :deep(img) {
  width: 100%;
  height: 220px;
  object-fit: cover;
}
</style>
