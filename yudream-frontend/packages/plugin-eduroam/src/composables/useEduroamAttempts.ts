import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { EduroamAttempt, EduroamPage } from '../types'
import { useFaToast } from '@yudream/components'
import { reactive, ref } from 'vue'
import { createEduroamApi } from '../api/eduroam-api'
import { errorMessage, num } from '../types'

/** 管理端：核验尝试审计（成功与失败都记，便于排查「为什么某位同学一直失败」）。 */
export function useEduroamAttempts(sdk: YuDreamPluginSdk) {
  const api = createEduroamApi(sdk)
  const toast = useFaToast()

  const loading = ref(false)
  const rows = ref<EduroamAttempt[]>([])
  const pager = reactive({ page: 1, size: 20, total: 0 })
  const filters = reactive({ keyword: '', success: '' })

  async function load(resetPage = false) {
    loading.value = true
    if (resetPage) {
      pager.page = 1
    }
    try {
      const page: EduroamPage<EduroamAttempt> = await api.attempts(
        filters.keyword, filters.success, pager.page, pager.size,
      )
      rows.value = (page?.records ?? []).map(normalize)
      pager.total = num(page?.total)
      clampPage()
    }
    catch (cause) {
      toast.error(errorMessage(cause, '加载尝试审计失败'))
    }
    finally {
      loading.value = false
    }
  }

  function clampPage() {
    const maxPage = Math.max(1, Math.ceil(pager.total / Math.max(1, pager.size)))
    if (pager.page > maxPage) {
      pager.page = maxPage
    }
  }

  function normalize(raw: EduroamAttempt): EduroamAttempt {
    return {
      ...raw,
      success: Boolean(raw.success),
      latencyMs: num(raw.latencyMs),
      createdAt: num(raw.createdAt),
    }
  }

  return reactive({
    loading, rows, pager, filters,
    load,
  })
}

export type EduroamAttemptsModel = ReturnType<typeof useEduroamAttempts>
