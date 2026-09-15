import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { EduroamSettings } from '../types'
import { useFaToast } from '@yudream/components'
import { reactive, ref } from 'vue'
import { createEduroamApi } from '../api/eduroam-api'
import { errorMessage, num } from '../types'

/** 管理端：渠道配置。 */
export function useEduroamSettings(sdk: YuDreamPluginSdk) {
  const api = createEduroamApi(sdk)
  const toast = useFaToast()

  const loading = ref(false)
  const saving = ref(false)
  const form = reactive<EduroamSettings>({
    enabled: true,
    eduDomain: '',
    storeDomain: '',
    verifyEndpoint: '',
    connectTimeoutSeconds: 5,
    requestTimeoutSeconds: 20,
    maxAttemptsPerHour: 10,
    tutorialMarkdown: '',
  })

  async function load() {
    loading.value = true
    try {
      assign(await api.settings())
    }
    catch (cause) {
      toast.error(errorMessage(cause, '加载认证设置失败'))
    }
    finally {
      loading.value = false
    }
  }

  async function save() {
    saving.value = true
    try {
      assign(await api.saveSettings({
        enabled: form.enabled,
        eduDomain: form.eduDomain,
        storeDomain: form.storeDomain,
        verifyEndpoint: form.verifyEndpoint,
        connectTimeoutSeconds: num(form.connectTimeoutSeconds),
        requestTimeoutSeconds: num(form.requestTimeoutSeconds),
        maxAttemptsPerHour: num(form.maxAttemptsPerHour),
        tutorialMarkdown: form.tutorialMarkdown,
      }))
      toast.success('认证设置已保存')
      return true
    }
    catch (cause) {
      toast.error(errorMessage(cause, '保存认证设置失败'))
      return false
    }
    finally {
      saving.value = false
    }
  }

  function assign(settings: EduroamSettings | null | undefined) {
    if (!settings) {
      return
    }
    form.enabled = Boolean(settings.enabled)
    form.eduDomain = settings.eduDomain ?? ''
    form.storeDomain = settings.storeDomain ?? ''
    form.verifyEndpoint = settings.verifyEndpoint ?? ''
    form.connectTimeoutSeconds = num(settings.connectTimeoutSeconds)
    form.requestTimeoutSeconds = num(settings.requestTimeoutSeconds)
    form.maxAttemptsPerHour = num(settings.maxAttemptsPerHour)
    form.tutorialMarkdown = settings.tutorialMarkdown ?? ''
  }

  return reactive({
    loading, saving, form,
    load, save,
  })
}

export type EduroamSettingsModel = ReturnType<typeof useEduroamSettings>
