<template>
  <div>
    <el-input v-model="expression" placeholder="inProvince(['BJ']) && levelGte(3)" />
    <el-button style="margin-top: 12px" @click="validate">校验表达式</el-button>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { ref } from 'vue'
import { validateFilter } from '../../../api/filter'

const expression = ref("inProvince(['BJ'])")

async function validate() {
  await validateFilter(expression.value)
  ElMessage.success('表达式可用')
}
</script>
