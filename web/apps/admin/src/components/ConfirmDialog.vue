<script setup lang="ts">
import { zhCN } from "@/locales/zh-CN";

defineOptions({ name: "ConfirmDialog" });

defineProps<{
  visible: boolean;
  title?: string;
  message: string;
}>();

const emit = defineEmits<{
  confirm: [];
  cancel: [];
}>();
</script>

<template>
  <div v-if="visible" class="confirm-mask" data-testid="confirm-dialog">
    <div class="confirm-card" role="dialog" aria-modal="true">
      <h3>{{ title || zhCN.confirm.title }}</h3>
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
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 40;
}
.confirm-card {
  background: #fff;
  min-width: 360px;
  max-width: 480px;
  padding: 20px;
  border-radius: 8px;
}
.confirm-card h3 {
  margin: 0 0 8px;
}
.confirm-card__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}
.confirm-card__ok {
  background: #2563eb;
  color: #fff;
  border: 0;
  border-radius: 4px;
  padding: 6px 12px;
  cursor: pointer;
}
</style>
