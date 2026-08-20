<script setup lang="ts">
import { onMounted, ref } from "vue";
import {
  createInternalApp,
  disableInternalApp,
  enableInternalApp,
  pageInternalApps,
  rotateInternalAppSecret,
  type InternalAppView,
} from "@/api/identity";
import ConfirmDialog from "@/components/ConfirmDialog.vue";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { PERMS, STATUS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

defineOptions({ name: "InternalAppPage" });

const records = ref<InternalAppView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const formOpen = ref(false);
const saving = ref(false);
const appName = ref("");
const secretOnce = ref("");
const confirm = ref<{ message: string; run: () => Promise<void> } | null>(null);

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await pageInternalApps({ page: page.value, pageSize });
  const parsed = okOrFeedback(result);
  loading.value = false;
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  records.value = parsed.data?.records ?? [];
  total.value = parsed.data?.total ?? 0;
}

async function submitCreate(): Promise<void> {
  saving.value = true;
  const result = await createInternalApp({ appName: appName.value });
  saving.value = false;
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  secretOnce.value = parsed.data?.secret ?? "";
  formOpen.value = false;
  appName.value = "";
  await load();
}

async function onRotate(row: InternalAppView): Promise<void> {
  if (row.id == null) {
    return;
  }
  const result = await rotateInternalAppSecret(row.id);
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  secretOnce.value = parsed.data?.secret ?? "";
  await load();
}

function askDisable(row: InternalAppView): void {
  if (row.id == null) {
    return;
  }
  confirm.value = {
    message: zhCN.confirm.disable,
    run: async () => {
      const result = await disableInternalApp(row.id as number);
      const parsed = okOrFeedback(result);
      if (!parsed.ok) {
        feedback.value = parsed.feedback;
        return;
      }
      await load();
    },
  };
}

async function onEnable(row: InternalAppView): Promise<void> {
  if (row.id == null) {
    return;
  }
  const result = await enableInternalApp(row.id);
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  await load();
}

async function onConfirm(): Promise<void> {
  const current = confirm.value;
  confirm.value = null;
  await current?.run();
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="internal-app-page">
    <h2>{{ zhCN.app.title }}</h2>
    <div class="admin-toolbar">
      <button v-auth="PERMS.APP_ADD" type="button" data-testid="app-create" @click="formOpen = true">
        {{ zhCN.app.add }}
      </button>
    </div>
    <p v-if="secretOnce" class="secret-once" data-testid="secret-once">
      {{ zhCN.app.secretOnce }}：<code data-testid="secret-value">{{ secretOnce }}</code>
    </p>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="internal-app-table">
      <thead>
        <tr>
          <th>{{ zhCN.app.appId }}</th>
          <th>{{ zhCN.app.appName }}</th>
          <th>{{ zhCN.common.status }}</th>
          <th>{{ zhCN.app.prevExpireAt }}</th>
          <th>{{ zhCN.common.createdAt }}</th>
          <th>{{ zhCN.common.actions }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in records" :key="row.id">
          <td>{{ row.appId }}</td>
          <td>{{ row.appName }}</td>
          <td>{{ row.status }}</td>
          <td>{{ formatDateTime(row.prevExpireAt) }}</td>
          <td>{{ formatDateTime(row.createdAt) }}</td>
          <td class="row-actions">
            <button v-auth="PERMS.APP_EDIT" type="button" data-testid="app-rotate" @click="onRotate(row)">
              {{ zhCN.app.rotate }}
            </button>
            <button
              v-if="row.status === STATUS.ENABLED"
              v-auth="PERMS.APP_EDIT"
              type="button"
              data-testid="app-disable"
              @click="askDisable(row)"
            >
              {{ zhCN.common.disable }}
            </button>
            <button
              v-if="row.status === STATUS.DISABLED"
              v-auth="PERMS.APP_EDIT"
              type="button"
              data-testid="app-enable"
              @click="onEnable(row)"
            >
              {{ zhCN.common.enable }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>
    <div class="pager">
      <span>{{ zhCN.common.total }} {{ total }}</span>
      <button type="button" :disabled="page <= 1" @click="page -= 1; load()">{{ zhCN.common.page }} -</button>
      <span>{{ page }}</span>
      <button type="button" :disabled="page * pageSize >= total" @click="page += 1; load()">{{ zhCN.common.page }} +</button>
    </div>
    <FormDialog :visible="formOpen" :title="zhCN.app.add" :saving="saving" @submit="submitCreate" @cancel="formOpen = false">
      <label class="field">
        <span>{{ zhCN.app.appName }}</span>
        <input v-model="appName" data-testid="app-name" required />
      </label>
    </FormDialog>
    <ConfirmDialog
      :visible="confirm != null"
      :message="confirm?.message ?? ''"
      @confirm="onConfirm"
      @cancel="confirm = null"
    />
  </section>
</template>

<style scoped>
.secret-once {
  background: #fffbeb;
  border: 1px solid #fcd34d;
  border-radius: 6px;
  padding: 8px 12px;
}
</style>
