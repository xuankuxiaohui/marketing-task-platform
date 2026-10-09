import { defineStore } from "pinia";
import { ref, watch } from "vue";
import type { PrizeCardView } from "@/api/prize";
import { useSessionStore } from "./session";

export const usePrizePreviewStore = defineStore("prizePreview", () => {
  const prize = ref<PrizeCardView | null>(null);
  const session = useSessionStore();

  watch(() => session.token, () => {
    prize.value = null;
  }, { flush: "sync" });

  function set(next: PrizeCardView | null): void {
    prize.value = next;
  }

  return { prize, set };
});
