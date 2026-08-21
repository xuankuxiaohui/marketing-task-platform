<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { showToast } from "vant";
import { isOk } from "@mkt/shared";
import { fetchAdPosition, type PortalAdMaterialView } from "@/api/ad";
import FallbackImage from "@/components/FallbackImage.vue";
import { zhCN } from "@/locales/zh-CN";
import { TRACK, track } from "@/tracking";
import { resolveAdJump } from "@/utils/ad-jump";

defineOptions({ name: "AdSplash" });

const POSITION = "app_splash";

const router = useRouter();
const material = ref<PortalAdMaterialView | null>(null);
const remain = ref(3);
const open = ref(false);
let tick: ReturnType<typeof setInterval> | undefined;

function close(): void {
  open.value = false;
  material.value = null;
  if (tick != null) {
    clearInterval(tick);
    tick = undefined;
  }
}

async function onClick(): Promise<void> {
  const current = material.value;
  if (!current) {
    return;
  }
  track(TRACK.AD_SPLASH_CLICK, { positionCode: POSITION, materialTrackId: current.trackId });
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
    remain.value = result.data.splashDurationSeconds ?? 3;
    track(TRACK.AD_SPLASH_EXPOSURE, { positionCode: POSITION, materialTrackId: first.trackId });
    tick = setInterval(() => {
      remain.value -= 1;
      if (remain.value <= 0) {
        close();
      }
    }, 1000);
  } catch {
    close();
  }
});

onBeforeUnmount(() => close());
</script>

<template>
  <div v-if="open && material" class="ad-splash" data-testid="ad-splash" @click="onClick">
    <FallbackImage :src="material.imageUrl" :alt="material.title || zhCN.ad.splash" />
    <button type="button" class="ad-splash__skip" data-testid="ad-splash-skip" @click.stop="close">
      {{ zhCN.ad.skip }} {{ remain }}
    </button>
  </div>
</template>

<style scoped>
.ad-splash {
  position: fixed;
  inset: 0;
  z-index: 30;
  background: #000;
}
.ad-splash :deep(.fallback-image),
.ad-splash :deep(img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.ad-splash__skip {
  position: absolute;
  top: 16px;
  right: 16px;
  padding: 4px 10px;
  border: 0;
  border-radius: 12px;
  background: rgb(0 0 0 / 45%);
  color: #fff;
}
</style>
