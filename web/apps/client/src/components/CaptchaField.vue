<script setup lang="ts">
import { Field } from "vant";
import { zhCN } from "@/locales/zh-CN";

defineOptions({ name: "CaptchaField" });

defineProps<{
  modelValue: string;
  image: string;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: string];
  refresh: [];
}>();
</script>

<template>
  <div class="captcha-field">
    <Field
      :model-value="modelValue"
      :label="zhCN.login.captcha"
      maxlength="8"
      autocomplete="off"
      data-testid="login-captcha"
      @update:model-value="emit('update:modelValue', $event)"
    />
    <button
      class="captcha-field__image"
      type="button"
      data-testid="login-captcha-refresh"
      :aria-label="zhCN.login.captchaAlt"
      @click="emit('refresh')"
    >
      <img v-if="image" data-testid="login-captcha-image" :src="image" :alt="zhCN.login.captchaAlt" />
    </button>
  </div>
</template>

<style scoped>
.captcha-field {
  display: flex;
  align-items: stretch;
  background: #fff;
}
.captcha-field :deep(.van-cell) {
  flex: 1;
}
.captcha-field__image {
  width: 110px;
  margin: 8px 12px 8px 0;
  padding: 0;
  border: 1px solid #ebedf0;
  background: #f7f8fa;
  border-radius: 4px;
}
.captcha-field__image img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
</style>
