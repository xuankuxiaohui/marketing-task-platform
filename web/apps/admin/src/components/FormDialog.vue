<script setup lang="ts">
import { zhCN } from "@/locales/zh-CN";

defineOptions({ name: "FormDialog" });

defineProps<{
  visible: boolean;
  title: string;
  saving?: boolean;
}>();

const emit = defineEmits<{
  submit: [];
  cancel: [];
}>();
</script>

<template>
  <div v-if="visible" class="form-mask" data-testid="form-dialog">
    <el-form class="form-card" label-position="top" @submit.prevent="emit('submit')">
      <h3>{{ title }}</h3>
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
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 30;
}
.form-card {
  background: #fff;
  min-width: 420px;
  max-width: 560px;
  max-height: 90vh;
  overflow: auto;
  padding: 20px;
  border-radius: 8px;
}
.form-card h3 {
  margin: 0 0 12px;
}
.form-card__body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.form-card__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}
</style>
