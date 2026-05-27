import { computed } from 'vue'
import { useTabStore } from '../stores/tab'

export function useTabKeepAlive() {
  const tabStore = useTabStore()
  const include = computed(() => tabStore.cachedComponentNames)
  return { include }
}
