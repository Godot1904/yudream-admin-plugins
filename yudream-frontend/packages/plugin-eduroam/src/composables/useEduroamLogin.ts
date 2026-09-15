import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { EduroamLoginResult, EduroamPublicConfig } from '../types'
import { useFaToast } from '@yudream/components'
import { computed, reactive, ref } from 'vue'
import { createEduroamApi } from '../api/eduroam-api'
import { errorMessage } from '../types'

/**
 * 第三方登录凭据页的状态与流程。
 *
 * <p>密码只存在于这个 composable 的内存里，提交后立刻清空；不进缓存、不进 URL、不落库。
 * 认证成功后由页面带着票据回宿主的第三方登录回调端点，本页不签发任何登录态。
 */
export function useEduroamLogin(sdk: YuDreamPluginSdk) {
  const api = createEduroamApi(sdk)
  const toast = useFaToast()

  const loading = ref(false)
  const submitting = ref(false)
  const config = ref<EduroamPublicConfig | null>(null)
  const error = ref('')
  const account = ref('')
  const password = ref('')
  const result = ref<EduroamLoginResult | null>(null)

  const enabled = computed(() => Boolean(config.value?.enabled))

  async function loadConfig() {
    loading.value = true
    error.value = ''
    try {
      config.value = await api.publicConfig()
    }
    catch (cause) {
      error.value = errorMessage(cause, '加载 Eduroam 登录配置失败')
    }
    finally {
      loading.value = false
    }
  }

  /**
   * 提交凭据。成功返回带票据的结果（调用方随即跳回宿主回调端点），失败返回 null 并展示原因。
   */
  async function submit(state: string): Promise<EduroamLoginResult | null> {
    const name = account.value.trim()
    if (!name) {
      error.value = '请填写 Eduroam 账号'
      return null
    }
    if (!password.value) {
      error.value = '请填写 Eduroam 密码'
      return null
    }
    if (!state) {
      error.value = '登录会话已失效，请从登录页的「Eduroam 认证」入口重新进入'
      return null
    }
    submitting.value = true
    error.value = ''
    result.value = null
    try {
      const payload = await api.login(name, password.value, state)
      result.value = payload
      if (!payload.success) {
        error.value = payload.message || 'Eduroam 认证未通过'
      }
      return payload
    }
    catch (cause) {
      error.value = errorMessage(cause, 'Eduroam 登录失败')
      toast.error(error.value)
      return null
    }
    finally {
      // 无论成功失败都清掉密码：失败重试时重新输入，页面上不留明文。
      password.value = ''
      submitting.value = false
    }
  }

  function reset() {
    result.value = null
    error.value = ''
    password.value = ''
  }

  return reactive({
    loading, submitting, config, error, account, password, result,
    enabled, loadConfig, submit, reset,
  })
}

export type EduroamLoginModel = ReturnType<typeof useEduroamLogin>
