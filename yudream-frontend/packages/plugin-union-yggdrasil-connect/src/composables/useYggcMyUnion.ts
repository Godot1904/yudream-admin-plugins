import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { YggcUnionOverview } from '../types'
import { useFaModal, useFaToast } from '@yudream/components'
import { reactive, ref } from 'vue'
import { createYggcApi } from '../api/yggc-api'

/** 安全等级徽章语义（原插件 union.twig）。 */
export function securityLevelBadge(level: unknown): { text: string, variant: 'default' | 'secondary' | 'destructive' } {
  const numeric = Number(level)
  if (Number.isFinite(numeric)) {
    if (numeric >= 3) {
      return { text: `SL${numeric}`, variant: 'default' }
    }
    if (numeric >= 1) {
      return { text: `SL${numeric}`, variant: 'secondary' }
    }
    return { text: `SL${numeric}`, variant: 'destructive' }
  }
  return { text: 'SL?', variant: 'secondary' }
}

export function useYggcMyUnion(sdk: YuDreamPluginSdk) {
  const api = createYggcApi(sdk)
  const toast = useFaToast()
  const confirm = useFaModal()
  const loading = ref(false)
  const overview = ref<YggcUnionOverview | null>(null)
  const error = ref('')
  /** 每个角色的绑定令牌（uuid → token），bind 按钮生成后填入。 */
  const tokens = reactive<Record<string, string>>({})
  /** 每个角色的绑定令牌输入框。 */
  const tokenInputs = reactive<Record<string, string>>({})

  async function load() {
    loading.value = true
    error.value = ''
    try {
      overview.value = await api.unionOverview()
    }
    catch (e: unknown) {
      error.value = e instanceof Error ? e.message : String(e)
    }
    finally {
      loading.value = false
    }
  }

  /** 获取跨站绑定令牌（主站生成，发给目标站管理员/持有者录入）。 */
  async function fetchToken(uuid: string) {
    const result = await api.unionBind(uuid)
    if (result.token) {
      tokens[uuid] = result.token
      toast.success('已生成绑定令牌，请在目标站输入该令牌完成绑定')
    }
    else {
      toast.error('上游未返回令牌')
    }
  }

  /** 用目标站生成的令牌把角色绑定到本站。 */
  async function bindTo(uuid: string) {
    const token = tokenInputs[uuid]
    if (!token || !token.trim()) {
      toast.error('请先粘贴目标站生成的绑定令牌')
      return
    }
    await api.unionBindTo(uuid, token.trim())
    toast.success('跨站绑定成功')
    await load()
  }

  function unbind(uuid: string) {
    confirm.confirm({
      title: '解除跨站绑定',
      content: '解除后本站角色将从 MUA Union 绑定中退出，其他站点的关联将失效。确认继续吗？',
      onConfirm: async () => {
        await api.unionUnbind(uuid)
        toast.success('已解除跨站绑定')
        await load()
      },
    })
  }

  function remapUuid(me: string, target: string) {
    confirm.confirm({
      title: '请求 UUID 重映射',
      content: '绑定后 UUID 会同步到所有用户中心；未使用 MUA 认证的 Minecraft 服务器中，与该 UUID 关联的玩家数据可能失效。确认继续吗？',
      onConfirm: async () => {
        await api.unionRemapUuid(me, target)
        toast.success('UUID 重映射请求已提交')
        await load()
      },
    })
  }

  return reactive({
    loading, overview, error, tokens, tokenInputs,
    load, fetchToken, bindTo, unbind, remapUuid,
  })
}
