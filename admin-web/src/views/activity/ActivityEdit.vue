<template>
  <el-card>
    <template #header>
      <div class="header">
        <div>
          <span class="page-title">{{ isNew ? '新建活动' : '编辑活动' }}</span>
          <p class="page-sub">配置活动信息、规则和子模块</p>
        </div>
        <div class="header-actions">
          <el-button @click="$router.push('/activities')">返回列表</el-button>
          <el-button v-if="!isNew && form.status === 'DRAFT'" type="success" @click="handlePublish" :loading="publishing">发布</el-button>
          <el-button v-if="!isNew && (form.status === 'PUBLISHED' || form.status === 'ONLINE')" type="warning" @click="handleOffline" :loading="offlining">下线</el-button>
          <el-button type="primary" @click="save" :loading="saving">保存</el-button>
        </div>
      </div>
    </template>

    <el-tabs v-model="activeTab">
      <!-- Basic Info Tab -->
      <el-tab-pane label="基本信息" name="basic">
        <el-form :model="form" label-width="120px" class="activity-form">
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="活动编码" required>
                <el-input v-model="form.code" placeholder="如 double11_2026" :disabled="!isNew" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="活动名称" required>
                <el-input v-model="form.name" placeholder="活动名称" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="灰度类型">
                <el-select v-model="form.grayType" placeholder="选择灰度类型">
                  <el-option label="全量" value="NONE" />
                  <el-option label="按比例" value="RATIO" />
                  <el-option label="白名单" value="WHITELIST" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="活动描述">
            <el-input v-model="form.description" type="textarea" :rows="2" placeholder="活动简短描述" />
          </el-form-item>

          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="开始时间" required>
                <el-date-picker v-model="form.startTime" type="datetime" placeholder="选择开始时间" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="结束时间" required>
                <el-date-picker v-model="form.endTime" type="datetime" placeholder="选择结束时间" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item v-if="form.grayType === 'RATIO'" label="灰度比例">
            <el-input-number v-model="grayConfigObj.ratio" :min="0" :max="100" placeholder="0-100" controls-position="right" style="width:200px" />
            <span style="margin-left:8px;color:var(--color-text-muted);font-size:12px">%</span>
          </el-form-item>
          <el-form-item v-if="form.grayType === 'WHITELIST'" label="白名单 Key">
            <el-input v-model="grayConfigObj.whitelistKey" placeholder="ListData 中的 listKey" style="width:300px" />
          </el-form-item>

          <el-divider content-position="left">参与规则 (JSON)</el-divider>
          <el-form-item label="规则配置">
            <el-input
              v-model="form.participationRules"
              type="textarea"
              :rows="8"
              placeholder='{"checkers":[{"type":"NEW_USER","params":{"days":7}}],"limits":[{"scope":"USER_DAILY","max":3}],"antiFraud":[{"type":"IP_RATE","params":{"maxPerIp":10,"windowSeconds":60}}]}'
            />
            <div class="field-hint">
              配置 checker 链、限流策略和防刷规则。留空表示无限制。
            </div>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- Display Rule Tab -->
      <el-tab-pane label="展示规则" name="display" v-if="!isNew">
        <div class="tab-toolbar">
          <el-button type="primary" @click="saveDisplayRule" :loading="savingRule">保存展示规则</el-button>
        </div>
        <el-input
          v-model="displayRuleContent"
          type="textarea"
          :rows="20"
          placeholder="输入展示规则内容（富文本 / Markdown）"
        />
        <div class="field-hint" v-if="displayRuleHash">
          内容 Hash: <code>{{ displayRuleHash }}</code>
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup lang="ts">
defineOptions({ name: 'ActivityEdit' })
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getActivity, createActivity, updateActivity,
  publishActivity, offlineActivity,
  getDisplayRule, updateDisplayRule,
  type Activity,
} from '../../api/activity'

const route = useRoute()
const router = useRouter()
const saving = ref(false)
const publishing = ref(false)
const offlining = ref(false)
const savingRule = ref(false)
const activeTab = ref('basic')

const id = computed(() => route.params.id ? Number(route.params.id) : null)
const isNew = computed(() => id.value === null || route.path.endsWith('/new'))

const form = reactive<Partial<Activity>>({
  code: '',
  name: '',
  description: '',
  grayType: 'NONE',
  grayConfig: undefined,
  startTime: '',
  endTime: '',
  participationRules: '',
})

const grayConfigObj = reactive<{ ratio: number; whitelistKey: string }>({ ratio: 100, whitelistKey: '' })

// Display rule
const displayRuleContent = ref('')
const displayRuleHash = ref('')

watch(() => form.grayType, () => {
  if (form.grayType === 'RATIO') {
    form.grayConfig = JSON.stringify({ ratio: grayConfigObj.ratio })
  } else if (form.grayType === 'WHITELIST') {
    form.grayConfig = JSON.stringify({ whitelistKey: grayConfigObj.whitelistKey })
  } else {
    form.grayConfig = undefined
  }
})

watch(() => grayConfigObj.ratio, () => {
  if (form.grayType === 'RATIO') {
    form.grayConfig = JSON.stringify({ ratio: grayConfigObj.ratio })
  }
})

watch(() => grayConfigObj.whitelistKey, () => {
  if (form.grayType === 'WHITELIST') {
    form.grayConfig = JSON.stringify({ whitelistKey: grayConfigObj.whitelistKey })
  }
})

onMounted(async () => {
  if (!isNew.value && id.value) {
    try {
      const { data } = await getActivity(id.value)
      const detail = data.data
      Object.assign(form, detail)
      // Parse grayConfig
      if (form.grayConfig) {
        try {
          const cfg = JSON.parse(form.grayConfig)
          if (cfg.ratio !== undefined) grayConfigObj.ratio = cfg.ratio
          if (cfg.whitelistKey) grayConfigObj.whitelistKey = cfg.whitelistKey
        } catch { /* ignore */ }
      }
      // Load display rule
      try {
        const { data: ruleData } = await getDisplayRule(id.value)
        if (ruleData.data) {
          displayRuleContent.value = ruleData.data.content || ''
          displayRuleHash.value = ruleData.data.contentHash || ''
        }
      } catch { /* no display rule yet */ }
    } catch (e: any) {
      ElMessage.error(e.response?.data?.message || '加载活动信息失败')
    }
  }
})

async function save() {
  if (!form.code?.trim() || !form.name?.trim()) {
    ElMessage.warning('请填写活动编码和名称')
    return
  }
  if (!form.startTime || !form.endTime) {
    ElMessage.warning('请填写开始和结束时间')
    return
  }
  if (form.participationRules?.trim()) {
    try {
      JSON.parse(form.participationRules)
    } catch {
      ElMessage.warning('参与规则 JSON 格式不正确')
      return
    }
  }
  if (form.grayConfig?.trim()) {
    try {
      JSON.parse(form.grayConfig)
    } catch {
      ElMessage.warning('灰度配置 JSON 格式不正确')
      return
    }
  }
  saving.value = true
  try {
    if (isNew.value) {
      await createActivity(form)
    } else {
      await updateActivity(id.value!, form)
    }
    router.push('/activities')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '保存活动失败')
  } finally {
    saving.value = false
  }
}

async function saveDisplayRule() {
  if (!id.value) return
  savingRule.value = true
  try {
    await updateDisplayRule(id.value, displayRuleContent.value)
    ElMessage.success('展示规则已保存')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '保存展示规则失败')
  } finally {
    savingRule.value = false
  }
}

async function handlePublish() {
  if (!id.value) return
  publishing.value = true
  try {
    await publishActivity(id.value)
    ElMessage.success('发布成功')
    form.status = 'PUBLISHED'
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '发布失败')
  } finally {
    publishing.value = false
  }
}

async function handleOffline() {
  if (!id.value) return
  offlining.value = true
  try {
    await offlineActivity(id.value)
    ElMessage.success('已下线')
    form.status = 'OFFLINE'
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '下线失败')
  } finally {
    offlining.value = false
  }
}
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.page-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text-primary);
}
.page-sub {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--color-text-muted);
}
.header-actions {
  display: flex;
  gap: 8px;
}
.activity-form {
  max-width: 960px;
}
.field-hint {
  font-size: 11px;
  color: var(--color-text-muted);
  margin-top: 4px;
}
.field-hint code {
  font-family: var(--font-mono);
  background: var(--color-surface-raised);
  padding: 2px 6px;
  border-radius: 3px;
}
.tab-toolbar {
  margin-bottom: 16px;
}
</style>
