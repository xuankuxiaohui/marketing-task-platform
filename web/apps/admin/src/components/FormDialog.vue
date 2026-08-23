<script setup lang="ts">
import { onMounted, onUnmounted } from "vue";
import { zhCN } from "@/locales/zh-CN";

defineOptions({ name: "FormDialog" });

const props = defineProps<{
  visible: boolean;
  title: string;
  saving?: boolean;
}>();

const emit = defineEmits<{
  submit: [];
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
    :title="title"
    :confirm-loading="saving"
    :mask-closable="true"
    :keyboard="false"
    :destroy-on-close="true"
    :get-container="false"
    :width="720"
    @ok="emit('submit')"
    @cancel="emit('cancel')"
  >
    <div data-testid="form-dialog">
      <a-form layout="vertical" class="admin-form-modal__form" @submit.prevent="emit('submit')">
        <div class="admin-form-modal__body" data-testid="form-dialog-body">
          <slot />
        </div>
      </a-form>
    </div>
    <template #footer>
      <a-button data-testid="form-cancel" @click="emit('cancel')">{{ zhCN.common.cancel }}</a-button>
      <a-button
        type="primary"
        html-type="submit"
        data-testid="form-submit"
        :loading="saving"
        :disabled="saving"
        @click="emit('submit')"
      >
        {{ zhCN.common.save }}
      </a-button>
    </template>
  </a-modal>
</template>

<style scoped>
.admin-form-modal__form {
  margin: 0;
}
.admin-form-modal__body {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  align-items: start;
  gap: 16px 16px;
  max-height: min(56vh, 520px);
  overflow-x: hidden;
  overflow-y: auto;
  padding: 4px 2px 8px;
}
.admin-form-modal__body :deep(.ant-form-item) {
  margin-bottom: 0;
}
.admin-form-modal__body :deep(.ant-form-item-label) {
  padding-bottom: 4px;
}
.admin-form-modal__body :deep(.ant-input),
.admin-form-modal__body :deep(.ant-input-affix-wrapper),
.admin-form-modal__body :deep(.ant-select),
.admin-form-modal__body :deep(.ant-picker),
.admin-form-modal__body :deep(textarea.ant-input) {
  width: 100%;
}
.admin-form-modal__body :deep(.ant-form-item:has(textarea)),
.admin-form-modal__body :deep(.ant-form-item:has(.ant-btn)),
.admin-form-modal__body > :deep(p),
.admin-form-modal__body > :deep(fieldset),
.admin-form-modal__body > :deep(.perm-tree-wrap),
.admin-form-modal__body > :deep(.ant-btn) {
  grid-column: 1 / -1;
}
.admin-form-modal__body > :deep(label) {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
</style>
