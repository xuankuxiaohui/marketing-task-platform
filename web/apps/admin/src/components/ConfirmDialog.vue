<script setup lang="ts">
import { onMounted, onUnmounted } from "vue";
import { zhCN } from "@/locales/zh-CN";

defineOptions({ name: "ConfirmDialog" });

const props = withDefaults(
  defineProps<{
    visible: boolean;
    title?: string;
    message: string;
  }>(),
  {
    title: undefined,
  },
);

const emit = defineEmits<{
  confirm: [];
  cancel: [];
}>();

function onKeydown(event: KeyboardEvent): void {
  if (event.key === "Escape" && props.visible) {
    emit("cancel");
  }
}

onMounted(() => {
  window.addEventListener("keydown", onKeydown);
});

onUnmounted(() => {
  window.removeEventListener("keydown", onKeydown);
});
</script>

<template>
  <a-modal
    :open="visible"
    :title="title || zhCN.confirm.title"
    :mask-closable="true"
    :keyboard="false"
    :destroy-on-close="true"
    :get-container="false"
    :width="416"
    @ok="emit('confirm')"
    @cancel="emit('cancel')"
  >
    <div data-testid="confirm-dialog">
      <p class="confirm-dialog__message" data-testid="confirm-message">{{ message }}</p>
    </div>
    <template #footer>
      <a-button data-testid="confirm-cancel" @click="emit('cancel')">{{ zhCN.common.cancel }}</a-button>
      <a-button type="primary" data-testid="confirm-ok" @click="emit('confirm')">
        {{ zhCN.common.confirm }}
      </a-button>
    </template>
  </a-modal>
</template>

<style scoped>
.confirm-dialog__message {
  margin: 0;
  color: rgba(0, 0, 0, 0.88);
  font-size: 14px;
  line-height: 1.5714;
}
</style>
