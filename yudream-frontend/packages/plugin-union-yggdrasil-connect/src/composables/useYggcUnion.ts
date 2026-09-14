import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { YggcUnionServer, YggcUnionStatus, YggcUnionSyncResult } from '../types'
import { useFaModal, useFaToast } from '@yudream/components'
import { computed, reactive, ref, watch } from 'vue'
import { createYggcApi } from '../api/yggc-api'

/** 皮肤站展示名称（原插件从 {bs_root}/api/yggdrasil 的 meta.serverName 拉取）。 */
const serverNames = reactive<Record<string, string>>({})

async function resolveServerName(server: YggcUnionServer) {
  if (!server.bs_root || serverNames[server.bs_root]) {
    return
  }
  try {
    const response = await fetch(`${server.bs_root.replace(/\/$/, '')}/api/yggdrasil`)
    if (!response.ok) {
      return
    }
    const data = await response.json()
    serverNames[server.bs_root] = String(data?.meta?.serverName ?? '')
  }
  catch {
    // 站点不可达时保持为空，仅展示 bs_root
  }
}

export function useYggcUnion(sdk: YuDreamPluginSdk) {
  const api = createYggcApi(sdk)
  const toast = useFaToast()
  const confirm = useFaModal()
  const loading = ref(false)
  const syncingKey = ref(false)
  const syncingList = ref(false)
  const syncingProfiles = ref(false)
  const status = ref<YggcUnionStatus | null>(null)
  const lastSyncResult = ref<YggcUnionSyncResult | null>(null)

  async function load() {
    loading.value = true
    try {
      status.value = await api.unionStatus()
      status.value.serverList?.servers?.forEach(resolveServerName)
    }
    finally {
      loading.value = false
    }
  }

  async function syncPrivateKey() {
    syncingKey.value = true
    try {
      const result = await api.syncUnionPrivateKey()
      toast.success(result.message || '已从上游同步签名私钥')
      await load()
    }
    finally {
      syncingKey.value = false
    }
  }

  async function syncServerList() {
    syncingList.value = true
    try {
      await api.syncUnionServerList()
      toast.success('已同步皮肤站列表')
      await load()
    }
    finally {
      syncingList.value = false
    }
  }

  function syncProfiles() {
    confirm.confirm({
      title: '全量同步角色',
      content: '将把本站全部角色（uuid → 角色名）推送到 MUA 主服务器，覆盖主服务器上的本站角色索引。确认继续吗？',
      onConfirm: async () => {
        syncingProfiles.value = true
        try {
          lastSyncResult.value = await api.syncUnionProfiles()
          toast.success(lastSyncResult.value.message || '全量同步已推送')
        }
        finally {
          syncingProfiles.value = false
        }
      },
    })
  }

  return reactive({
    loading, syncingKey, syncingList, syncingProfiles, status, lastSyncResult, serverNames,
    load, syncPrivateKey, syncServerList, syncProfiles,
  })
}

export function useYggcBlacklist(sdk: YuDreamPluginSdk) {
  const api = createYggcApi(sdk)
  const toast = useFaToast()
  const confirm = useFaModal()
  const querying = ref(false)
  const creating = ref(false)
  const error = ref('')
  /** 搜索关键词（q），按邮箱 / 角色名关键词过滤。 */
  const form = reactive({ q: '' })
  /** 新增表单：email + reason 都是主服务器必填项。 */
  const create = reactive({ email: '', reason: '' })
  /** 全量拉取回来的所有记录（上游固定每页 15 条且忽略 per_page，这里循环拉完再本地分页）。 */
  const records = ref<Record<string, unknown>[]>([])
  const pager = reactive({ page: 1, size: 15, total: 0 })

  const pagedRecords = computed(() => {
    const start = (pager.page - 1) * pager.size
    return records.value.slice(start, start + pager.size)
  })

  watch(() => pager.size, () => {
    pager.page = 1
  })
  watch(() => pager.total, () => {
    const maxPage = Math.max(1, Math.ceil(pager.total / Math.max(1, pager.size)))
    if (pager.page > maxPage) {
      pager.page = maxPage
    }
  })

  /** 全量拉取：循环拉完上游所有页，合并后交给本地分页。 */
  async function query() {
    querying.value = true
    error.value = ''
    try {
      const collected: Record<string, unknown>[] = []
      let page = 1
      let lastPage = 1
      do {
        const params: Record<string, string> = { page: String(page) }
        if (form.q) {
          params.q = form.q
        }
        const res = await api.blacklistQuery(params)
        const rows = extractRecords(res)
        if (rows.length === 0 && page > 1) {
          break
        }
        collected.push(...rows)
        lastPage = lastPageOf(res)
        page += 1
      } while (page <= lastPage)
      records.value = collected
      pager.total = collected.length
      pager.page = 1
    }
    catch (e: unknown) {
      error.value = messageOf(e, '查询黑名单失败')
      toast.error(error.value)
      records.value = []
      pager.total = 0
    }
    finally {
      querying.value = false
    }
  }

  async function submitCreate() {
    creating.value = true
    error.value = ''
    try {
      if (!create.email.trim()) {
        throw new Error('请填写要拉黑的邮箱地址')
      }
      // 主服务器对 reason 也做必填校验（缺失会返回 422），前端先拦一道，避免提交后才报错。
      if (!create.reason.trim()) {
        throw new Error('请填写拉黑原因，主服务器要求必填')
      }
      const payload: Record<string, unknown> = { email: create.email.trim(), reason: create.reason.trim() }
      await api.blacklistCreate(payload)
      toast.success('已提交黑名单记录')
      create.email = ''
      create.reason = ''
      await query()
    }
    catch (e: unknown) {
      error.value = messageOf(e, '提交黑名单记录失败')
      toast.error(error.value)
    }
    finally {
      creating.value = false
    }
  }

  function invalidate(id: string) {
    confirm.confirm({
      title: '使黑名单记录失效',
      content: `确认使黑名单记录 ${id} 失效吗？该记录将不再拦截角色登录。`,
      onConfirm: async () => {
        try {
          await api.blacklistInvalidate(id)
          toast.success('记录已失效')
          await query()
        }
        catch (e: unknown) {
          reportFailure(e, '使记录失效失败')
        }
      },
    })
  }

  function remove(id: string) {
    confirm.confirm({
      title: '删除黑名单记录',
      content: `确认删除黑名单记录 ${id} 吗？该操作不可恢复。`,
      onConfirm: async () => {
        try {
          await api.blacklistDelete(id)
          toast.success('记录已删除')
          await query()
        }
        catch (e: unknown) {
          reportFailure(e, '删除记录失败')
        }
      },
    })
  }

  /** 失效 / 删除失败时既要提示，也要把原因留在页面上，避免「点了没反应」。 */
  function reportFailure(e: unknown, fallback: string) {
    error.value = messageOf(e, fallback)
    toast.error(error.value)
  }

  function messageOf(e: unknown, fallback: string) {
    const text = e instanceof Error ? e.message : String(e ?? '')
    return text.trim() || fallback
  }

  return reactive({
    querying, creating, error, form, create, records, pager, pagedRecords,
    query, submitCreate, invalidate, remove,
  })
}

/** 从上游 Laravel 分页响应里取记录数组。 */
function extractRecords(result: Record<string, unknown>): Record<string, unknown>[] {
  if (!result) {
    return []
  }
  for (const key of ['data', 'records', 'list', 'blacklist', 'result']) {
    const value = result[key]
    if (Array.isArray(value)) {
      return value as Record<string, unknown>[]
    }
  }
  return []
}

/** 上游 last_page（数字或字符串）；缺失时视为 1，避免循环失控。 */
function lastPageOf(result: Record<string, unknown>): number {
  const raw = result?.last_page ?? result?.lastPage
  const value = Number(raw)
  return Number.isFinite(value) && value > 0 ? Math.floor(value) : 1
}
