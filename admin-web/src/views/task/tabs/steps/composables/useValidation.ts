import { ref } from 'vue'
import { checkStepCode } from '../../../../../api/step'
import { http } from '../../../../../api/http'

export function useValidation() {
  const codeValidating = ref(false)
  const codeFeedback = ref('')
  const codeValid = ref<boolean | null>(null)

  const exprValidating = ref(false)
  const exprFeedback = ref('')
  const exprValid = ref<boolean | null>(null)

  async function validateCode(taskId: number, code: string, excludeStepId?: number) {
    if (!code.trim()) {
      codeFeedback.value = ''
      codeValid.value = null
      return
    }
    codeValidating.value = true
    try {
      const { data } = await checkStepCode(taskId, code.trim(), excludeStepId)
      if (data.data.valid) {
        codeFeedback.value = '编码可用'
        codeValid.value = true
      } else {
        codeFeedback.value = '编码已存在'
        codeValid.value = false
      }
    } catch {
      codeFeedback.value = ''
      codeValid.value = null
    } finally {
      codeValidating.value = false
    }
  }

  async function validateExpression(expr: string) {
    if (!expr || !expr.trim()) {
      exprFeedback.value = ''
      exprValid.value = null
      return
    }
    exprValidating.value = true
    try {
      await http.post('/admin/utils/validate-filter', { expression: expr.trim() })
      exprFeedback.value = '表达式有效'
      exprValid.value = true
    } catch (e: any) {
      exprFeedback.value = e?.response?.data?.message || '校验失败'
      exprValid.value = false
    } finally {
      exprValidating.value = false
    }
  }

  function resetCode() {
    codeFeedback.value = ''
    codeValid.value = null
  }

  function resetExpr() {
    exprFeedback.value = ''
    exprValid.value = null
  }

  return {
    codeValidating,
    codeFeedback,
    codeValid,
    exprValidating,
    exprFeedback,
    exprValid,
    validateCode,
    validateExpression,
    resetCode,
    resetExpr,
  }
}
