import { defineStore } from "pinia";
import { ref } from "vue";
import type { PrizeCardView } from "@/api/prize";

export const usePrizePreviewStore = defineStore("prizePreview", () => {
  const prize = ref<PrizeCardView | null>(null);

  function set(next: PrizeCardView | null): void {
    prize.value = next;
  }

  return { prize, set };
});
