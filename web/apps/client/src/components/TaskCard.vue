<script setup lang="ts">
import { computed } from "vue";
import { Button, Tag } from "vant";
import type { TaskCardView } from "@/api/task";
import FallbackImage from "@/components/FallbackImage.vue";
import { zhCN } from "@/locales/zh-CN";
import { formatRewardPreview } from "@/utils/reward-preview";
import { taskButtonState } from "@/utils/task-button";

defineOptions({ name: "TaskCard" });

const props = defineProps<{
  task: TaskCardView;
}>();

const emit = defineEmits<{
  open: [];
  action: [];
}>();

const button = computed(() => taskButtonState(props.task.userStatus));
const reward = computed(() => formatRewardPreview(props.task.rewardPreview));
</script>

<template>
  <article class="task-card" data-testid="task-card">
    <button class="task-card__body" type="button" data-testid="task-card-open" @click="emit('open')">
      <FallbackImage :src="task.iconUrl" :alt="task.name ?? zhCN.home.title" />
      <span class="task-card__meta">
        <span class="task-card__title">
          <strong>{{ task.name }}</strong>
          <Tag v-if="task.badgeText" type="primary" plain>{{ task.badgeText }}</Tag>
        </span>
        <span v-if="reward" class="task-card__reward" data-testid="task-card-reward">{{ reward }}</span>
      </span>
    </button>
    <Button
      size="small"
      :type="button.disabled ? 'default' : 'primary'"
      :disabled="button.disabled"
      :data-testid="`task-card-action-${task.taskId}`"
      @click="emit('action')"
    >
      {{ button.label }}
    </Button>
  </article>
</template>

<style scoped>
.task-card {
  display: flex;
  gap: 8px;
  align-items: center;
  margin: 0 16px 12px;
  padding: 12px;
  border-radius: 12px;
  background: #fff;
}
.task-card__body {
  display: flex;
  flex: 1;
  gap: 12px;
  align-items: center;
  min-width: 0;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
}
.task-card__meta {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}
.task-card__title {
  display: flex;
  gap: 6px;
  align-items: center;
}
.task-card__title strong {
  overflow: hidden;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.task-card__reward {
  color: #ee0a24;
  font-size: 13px;
}
</style>
