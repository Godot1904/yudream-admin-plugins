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
  const sitePassword = ref('')
  const confirmPassword = ref('')
  const result = ref<EduroamLoginResult | null>(null)

  const enabled = computed(() => Boolean(config.value?.enabled))
  const accountSuffix = computed(() => config.value?.eduDomain ? `@${config.value.eduDomain}` : '')
  const accountPlaceholder = computed(() => accountSuffix.value ? '学号 / 工号' : '学号@学校域名')
  const needsRegistration = computed(() => Boolean(result.value?.success
    && result.value.registrationRequired && !sdk.account.userId))

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
    if (submitting.value) return null
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

  async function register(state: string): Promise<EduroamLoginResult | null> {
    if (submitting.value || !needsRegistration.value || !result.value) return null
    if (sitePassword.value.trim().length === 0 || sitePassword.value.length < 8
      || new TextEncoder().encode(sitePassword.value).length > 72) {
      error.value = '本站密码至少 8 个字符，且 UTF-8 编码不能超过 72 字节'
      return null
    }
    if (sitePassword.value !== confirmPassword.value) {
      error.value = '两次输入的本站密码不一致'
      return null
    }
    submitting.value = true
    error.value = ''
    try {
      const payload = await api.register(result.value.ticket, state, sitePassword.value, confirmPassword.value)
      result.value = payload
      return payload
    }
    catch (cause) {
      // 写入失败时票据可能已核销，也可能已创建成功；重新认证后由服务端检查账号状态。
      result.value = null
      error.value = `${errorMessage(cause, '创建账号未完成')}。请重新进行校园认证后继续。`
      return null
    }
    finally {
      sitePassword.value = ''
      confirmPassword.value = ''
      submitting.value = false
    }
  }

  function reset() {
    result.value = null
    error.value = ''
    password.value = ''
    sitePassword.value = ''
    confirmPassword.value = ''
  }

  return reactive({
    loading, submitting, config, error, account, password, sitePassword, confirmPassword, result,
    enabled, accountSuffix, accountPlaceholder, needsRegistration, loadConfig, submit, register, reset,
  })
}

export type EduroamLoginModel = ReturnType<typeof useEduroamLogin>
