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
import { adminStatusLabel } from "@/utils/status-label";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";
import { ADMIN_PAGE_SIZE, adminPagination, adminRowKey } from "@/utils/table";

defineOptions({ name: "InternalAppPage" });

const records = ref<InternalAppView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ADMIN_PAGE_SIZE;
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


function onTableChange(pag: { current?: number }): void {
  page.value = pag.current ?? 1;
  void load();
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="internal-app-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.app.title }}</h2>
      <a-button type="primary" v-auth="PERMS.APP_ADD" data-testid="app-create" @click="formOpen = true">
        {{ zhCN.app.add }}
      </a-button>
    </div>
    <p v-if="secretOnce" class="secret-once" data-testid="secret-once">
      {{ zhCN.app.secretOnce }}：<code data-testid="secret-value">{{ secretOnce }}</code>
    </p>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="records" class="data-table admin-table" data-testid="internal-app-table" :pagination="adminPagination(page, pageSize, total)" :row-key="adminRowKey" @change="onTableChange">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty">
<a-button v-auth="PERMS.APP_ADD" type="primary" size="small" @click="formOpen = true">
        {{ zhCN.app.add }}
      </a-button>
        </a-empty>
      </template>

      <a-table-column :title="zhCN.app.appId">
        <template #default="{ record: row }">{{ row.appId }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.app.appName">
        <template #default="{ record: row }">{{ row.appName }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.status">
        <template #default="{ record: row }">
          <a-tag :color="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'success' : 'default'" :class="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'status-tag--on' : 'status-tag--off'">
            {{ adminStatusLabel(row.status) }}
          </a-tag>
        </template>
      </a-table-column>
      <a-table-column :title="zhCN.app.prevExpireAt">
        <template #default="{ record: row }">{{ formatDateTime(row.prevExpireAt) }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.createdAt">
        <template #default="{ record: row }">{{ formatDateTime(row.createdAt) }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.actions" :width="240">
        <template #default="{ record: row }">
          <div class="row-actions">
            <a-button size="small" v-auth="PERMS.APP_EDIT" data-testid="app-rotate" @click="onRotate(row)">
              {{ zhCN.app.rotate }}
            </a-button>
            <a-button size="small" v-if="row.status === STATUS.ENABLED" v-auth="PERMS.APP_EDIT" data-testid="app-disable" @click="askDisable(row)">
              {{ zhCN.common.disable }}
            </a-button>
            <a-button size="small" v-if="row.status === STATUS.DISABLED" v-auth="PERMS.APP_EDIT" data-testid="app-enable" @click="onEnable(row)">
              {{ zhCN.common.enable }}
            </a-button>
          </div>
        </template>
      </a-table-column>
    </a-table>
    <FormDialog :visible="formOpen" :title="zhCN.app.add" :saving="saving" @submit="submitCreate" @cancel="formOpen = false">
      <a-form-item :label="zhCN.app.appName">
        <a-input v-model:value="appName" data-testid="app-name" required />
      </a-form-item>
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
  background: #fffbe6;
  border: 1px solid #ffe58f;
  border-radius: 6px;
  padding: 8px 12px;
}
</style>
