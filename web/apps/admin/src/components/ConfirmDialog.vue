<script setup lang="ts">
import { onMounted, onUnmounted } from "vue";
import { zhCN } from "@/locales/zh-CN";

defineOptions({ name: "ConfirmDialog" });

const props = defineProps<{
  visible: boolean;
  title?: string;
  message: string;
}>();

const emit = defineEmits<{
  confirm: [];
  cancel: [];
}>();

function onKeydown(event: KeyboardEvent): void {
  if (event.key === "Escape" && props.visible) {
    emit("cancel");
  }
}

function onMaskClick(event: MouseEvent): void {
  if (event.target === event.currentTarget) {
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
  <div v-if="visible" class="confirm-mask" data-testid="confirm-dialog" @click="onMaskClick">
    <div class="confirm-card" role="dialog" aria-modal="true" @click.stop>
      <header class="confirm-card__header">
        <h3>{{ title || zhCN.confirm.title }}</h3>
        <el-button class="confirm-card__close" text @click="emit('cancel')">×</el-button>
      </header>
      <p data-testid="confirm-message">{{ message }}</p>
      <div class="confirm-card__actions">
        <el-button data-testid="confirm-cancel" @click="emit('cancel')">{{ zhCN.common.cancel }}</el-button>
        <el-button type="primary" data-testid="confirm-ok" @click="emit('confirm')">
          {{ zhCN.common.confirm }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.confirm-mask {
  position: fixed;
  inset: 0;
  background: var(--el-overlay-color-lighter, rgba(0, 0, 0, 0.5));
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 40;
}
.confirm-card {
  background: var(--el-bg-color, #fff);
  min-width: 360px;
  max-width: 480px;
  padding: 0;
  border-radius: var(--el-border-radius-base, 4px);
  box-shadow: var(--el-box-shadow, 0 12px 32px 4px rgba(0, 0, 0, 0.04), 0 8px 20px rgba(0, 0, 0, 0.08));
}
.confirm-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
}
.confirm-card h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}
.confirm-card__close {
  min-width: 24px;
  font-size: 18px;
  color: var(--el-text-color-secondary, #909399);
}
.confirm-card p {
  margin: 0;
  padding: 16px;
  color: var(--el-text-color-regular, #606266);
  line-height: 1.5;
}
.confirm-card__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 0;
  padding: 12px 16px 16px;
  border-top: 1px solid var(--el-border-color-lighter, #ebeef5);
}
</style>
