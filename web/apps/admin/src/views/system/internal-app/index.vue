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
    <div class="admin-page__header">
      <h2>{{ zhCN.app.title }}</h2>
      <el-button type="primary" v-auth="PERMS.APP_ADD" data-testid="app-create" @click="formOpen = true">
        {{ zhCN.app.add }}
      </el-button>
    </div>
    <p v-if="secretOnce" class="secret-once" data-testid="secret-once">
      {{ zhCN.app.secretOnce }}：<code data-testid="secret-value">{{ secretOnce }}</code>
    </p>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <div v-else-if="records.length === 0" data-testid="page-empty" class="page-empty">
      <span>{{ zhCN.common.empty }}</span>
      <el-button v-auth="PERMS.APP_ADD" text type="primary" @click="formOpen = true">
        {{ zhCN.app.add }}
      </el-button>
    </div>
    <el-table v-else :data="records" class="data-table admin-table" data-testid="internal-app-table" size="small" stripe>
      <el-table-column :label="zhCN.app.appId">
        <template #default="{ row }">{{ row.appId }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.app.appName">
        <template #default="{ row }">{{ row.appName }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.status">
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'success' : 'info'"
            :class="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'status-tag--on' : 'status-tag--off'"
          >
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="zhCN.app.prevExpireAt">
        <template #default="{ row }">{{ formatDateTime(row.prevExpireAt) }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.createdAt">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.actions" min-width="240">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button text v-auth="PERMS.APP_EDIT" data-testid="app-rotate" @click="onRotate(row)">
              {{ zhCN.app.rotate }}
            </el-button>
            <el-button text
              v-if="row.status === STATUS.ENABLED"
              v-auth="PERMS.APP_EDIT"
              data-testid="app-disable"
              @click="askDisable(row)"
            >
              {{ zhCN.common.disable }}
            </el-button>
            <el-button text
              v-if="row.status === STATUS.DISABLED"
              v-auth="PERMS.APP_EDIT"
              data-testid="app-enable"
              @click="onEnable(row)"
            >
              {{ zhCN.common.enable }}
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <span>{{ zhCN.common.total }} {{ total }}</span>
      <el-button :disabled="page <= 1" @click="page -= 1; load()">{{ zhCN.common.page }} -</el-button>
      <span>{{ page }}</span>
      <el-button :disabled="page * pageSize >= total" @click="page += 1; load()">{{ zhCN.common.page }} +</el-button>
    </div>
    <FormDialog :visible="formOpen" :title="zhCN.app.add" :saving="saving" @submit="submitCreate" @cancel="formOpen = false">
      <el-form-item :label="zhCN.app.appName">
        <el-input v-model="appName" data-testid="app-name" required />
      </el-form-item>
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
