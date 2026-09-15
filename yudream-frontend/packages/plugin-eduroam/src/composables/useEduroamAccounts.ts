import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { EduroamAccount, EduroamPage } from '../types'
import { useFaModal, useFaToast } from '@yudream/components'
import { reactive, ref } from 'vue'
import { createEduroamApi } from '../api/eduroam-api'
import { errorMessage, num } from '../types'

/** 管理端：登录过的校园网账号台账、封禁 / 解封 / 删除。 */
export function useEduroamAccounts(sdk: YuDreamPluginSdk) {
  const api = createEduroamApi(sdk)
  const toast = useFaToast()
  const confirm = useFaModal()

  const loading = ref(false)
  const saving = ref(false)
  const rows = ref<EduroamAccount[]>([])
  const pager = reactive({ page: 1, size: 10, total: 0 })
  const filters = reactive({ status: '', keyword: '' })

  async function load(resetPage = false) {
    loading.value = true
    if (resetPage) {
      pager.page = 1
    }
    try {
      const page: EduroamPage<EduroamAccount> = await api.accounts(
        filters.status, filters.keyword, pager.page, pager.size,
      )
      rows.value = (page?.records ?? []).map(normalize)
      pager.total = num(page?.total)
      clampPage()
    }
    catch (cause) {
      toast.error(errorMessage(cause, '加载登录账号失败'))
    }
    finally {
      loading.value = false
    }
  }

  /** 封禁：由页面弹窗收集原因后调用（原因会写入记录，便于事后追溯）。 */
  async function block(row: EduroamAccount, reason: string) {
    saving.value = true
    try {
      await api.blockAccount(row.id, reason)
      toast.success('已禁止该账号登录')
      await load()
      return true
    }
    catch (cause) {
      toast.error(errorMessage(cause, '禁止登录失败'))
      return false
    }
    finally {
      saving.value = false
    }
  }

  function unblock(row: EduroamAccount) {
    confirm.confirm({
      title: '解除禁止',
      content: `确认允许「${row.email}」再次用 Eduroam 登录吗？`,
      onConfirm: async () => {
        saving.value = true
        try {
          await api.unblockAccount(row.id)
          toast.success('已解除禁止')
          await load()
        }
        catch (cause) {
          toast.error(errorMessage(cause, '解除禁止失败'))
        }
        finally {
          saving.value = false
        }
      },
    })
  }

  function remove(row: EduroamAccount) {
    confirm.confirm({
      title: '删除账号记录',
      content: `确认删除「${row.email}」的登录记录吗？删除后该账号下次登录会重新建档，封禁状态一并丢失，尝试审计仍会保留。`,
      onConfirm: async () => {
        saving.value = true
        try {
          await api.deleteAccount(row.id)
          toast.success('记录已删除')
          await load()
        }
        catch (cause) {
          toast.error(errorMessage(cause, '删除失败'))
        }
        finally {
          saving.value = false
        }
      },
    })
  }

  function clampPage() {
    const maxPage = Math.max(1, Math.ceil(pager.total / Math.max(1, pager.size)))
    if (pager.page > maxPage) {
      pager.page = maxPage
    }
  }

  function normalize(raw: EduroamAccount): EduroamAccount {
    return {
      ...raw,
      lastLoginAt: num(raw.lastLoginAt),
      loginCount: num(raw.loginCount),
      firstLoginAt: num(raw.firstLoginAt),
      blockedAt: num(raw.blockedAt),
      createdAt: num(raw.createdAt),
      updatedAt: num(raw.updatedAt),
    }
  }

  return reactive({
    loading, saving, rows, pager, filters,
    load, block, unblock, remove,
  })
}

export type EduroamAccountsModel = ReturnType<typeof useEduroamAccounts>
