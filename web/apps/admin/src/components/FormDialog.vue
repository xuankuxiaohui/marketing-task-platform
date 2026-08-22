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
  <div v-if="visible" class="form-mask" data-testid="form-dialog" @click="onMaskClick">
    <el-form class="form-card" label-position="top" @submit.prevent="emit('submit')" @click.stop>
      <header class="form-card__header">
        <h3>{{ title }}</h3>
        <el-button class="form-card__close" text @click="emit('cancel')">×</el-button>
      </header>
      <div class="form-card__body">
        <slot />
      </div>
      <div class="form-card__actions">
        <el-button data-testid="form-cancel" @click="emit('cancel')">{{ zhCN.common.cancel }}</el-button>
        <el-button type="primary" native-type="submit" data-testid="form-submit" :disabled="saving">
          {{ zhCN.common.save }}
        </el-button>
      </div>
    </el-form>
  </div>
</template>

<style scoped>
.form-mask {
  position: fixed;
  inset: 0;
  background: var(--el-overlay-color-lighter, rgba(0, 0, 0, 0.5));
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 30;
}
.form-card {
  background: var(--el-bg-color, #fff);
  min-width: 420px;
  max-width: 560px;
  max-height: 90vh;
  overflow: auto;
  padding: 0;
  border-radius: var(--el-border-radius-base, 4px);
  box-shadow: var(--el-box-shadow, 0 12px 32px 4px rgba(0, 0, 0, 0.04), 0 8px 20px rgba(0, 0, 0, 0.08));
}
.form-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
}
.form-card h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}
.form-card__close {
  min-width: 24px;
  font-size: 18px;
  color: var(--el-text-color-secondary, #909399);
}
.form-card__body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
}
.form-card__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 0;
  padding: 12px 16px 16px;
  border-top: 1px solid var(--el-border-color-lighter, #ebeef5);
}
</style>
