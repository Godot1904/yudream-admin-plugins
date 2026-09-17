import type { YggcEligibleClientView, YggcKeyPairInfo, YggcSettings, YggcUnionDiagnosis, YggcUnionLocalState, YggcUnionPrivateKeySyncResult, YggcUnionProfileSyncState } from '../types'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { useFaModal, useFaToast } from '@yudream/components'
import { reactive, ref } from 'vue'
import { createYggcApi } from '../api/yggc-api'

/** 与后端 YggcSettings.defaults() 保持一致的默认值。 */
function defaultSettings(): YggcSettings {
  return {
    uuidAlgorithm: 'v3',
    tokenExpire: 259200,
    tokenRefreshExpire: 604800,
    tokensLimit: 10,
    rateLimit: 1000,
    skinDomain: '',
    searchProfileMax: 5,
    showConfigSection: true,
    enableAli: true,
    restoreApi: false,
    disableAuthserver: false,
    connectServerUrl: '',
    unionApiRoot: 'https://skin.mualliance.ltd/api/union',
    unionMemberKey: '',
    unionEnableUpdate: true,
    unionEnableOauth2: false,
    unionSyncEnabled: true,
    unionSyncIntervalMinutes: 10,
    unionSyncOnLogin: true,
    oauthAccessTtl: 604800,
    oauthRefreshTtl: 2592000,
    oauthDeviceTtl: 600,
    serverName: '',
    sharedClientId: '',
  }
}

export function useYggcSettings(sdk: YuDreamPluginSdk) {
  const api = createYggcApi(sdk)
  const toast = useFaToast()
  const confirm = useFaModal()
  const loading = ref(false)
  const saving = ref(false)
  const diagnosing = ref(false)
  const syncingKey = ref(false)
  const diagnosis = ref<YggcUnionDiagnosis | null>(null)
  const keyPairs = ref<Partial<Record<'texture' | 'token' | 'union-oauth2', YggcKeyPairInfo>>>({})
  const unionState = ref<YggcUnionLocalState | null>(null)
  const profileSyncState = ref<YggcUnionProfileSyncState | null>(null)
  const keySyncResult = ref<YggcUnionPrivateKeySyncResult | null>(null)
  const sharedCandidates = ref<YggcEligibleClientView[]>([])
  const form = reactive<YggcSettings>(defaultSettings())

  function assign(settings: YggcSettings) {
    const { keyPairs: ignored, union: ignoredUnion, profileSync: ignoredSync, ...rest } = settings
    void ignored
    void ignoredUnion
    void ignoredSync
    Object.assign(form, defaultSettings(), rest)
    if (settings.keyPairs) {
      keyPairs.value = settings.keyPairs
    }
    if (settings.union) {
      unionState.value = settings.union
    }
    if (settings.profileSync) {
      profileSyncState.value = settings.profileSync
    }
  }

  /** 只回传配置项本身：keyPairs / union / profileSync 都是只读状态，不参与保存。 */
  function payload(): Record<string, unknown> {
    const { keyPairs: ignored, union: ignoredUnion, profileSync: ignoredSync, ...rest } = form
    void ignored
    void ignoredUnion
    void ignoredSync
    return { ...rest }
  }

  async function load() {
    loading.value = true
    try {
      const settings = await api.config()
      assign(settings)
      await loadSharedCandidates()
    }
    finally {
      loading.value = false
    }
  }

  /** 可绑定为共享客户端的应用（启用中的公共客户端），供配置页下拉选择。 */
  async function loadSharedCandidates() {
    try {
      sharedCandidates.value = await api.eligibleSharedClients()
    }
    catch {
      // 下拉候选加载失败不阻塞配置展示，管理员仍可看到已保存的值
      sharedCandidates.value = []
    }
  }

  async function save() {
    saving.value = true
    try {
      assign(await api.updateConfig(payload()))
      toast.success('配置已保存')
    }
    finally {
      saving.value = false
    }
  }

  function reset() {
    confirm.confirm({
      title: '恢复默认配置',
      content: '确认把全部配置项恢复为默认值吗？该操作立即生效且无法撤销。',
      onConfirm: async () => {
        const saved = await api.resetConfig()
        assign(saved)
        toast.success('已恢复默认配置')
      },
    })
  }

  function regenerateKeyPair(usage: 'texture' | 'token') {
    confirm.confirm({
      title: '重新生成密钥对',
      content: usage === 'texture'
        ? (unionState.value?.privateKeySynced
            ? '材质签名密钥当前由 MUA 主服务器分发，本地重新生成会破坏跨站签名兼容，后端也会拒绝该操作。仍要继续吗？'
            : '确认重新生成材质签名密钥吗？已分发的旧签名将立即失效，正在线上的启动器需要重新获取元数据。')
        : '确认重新生成 ID Token 签名密钥吗？已签发的 ID Token 将立即失效，授权过的客户端需重新登录。',
      onConfirm: async () => {
        const info = await api.regenerateKeyPair(usage)
        keyPairs.value = { ...keyPairs.value, [usage]: info }
        toast.success('密钥对已重新生成')
      },
    })
  }

  /** 从 MUA 主服务器拉取签名私钥（用户信息签名密钥由主服务器生成并分发）。 */
  async function syncUnionPrivateKey() {
    syncingKey.value = true
    try {
      keySyncResult.value = await api.syncUnionPrivateKey()
      toast.success(keySyncResult.value.message || '已从上游同步签名私钥')
      await load()
    }
    finally {
      syncingKey.value = false
    }
  }

  async function diagnoseUnion() {
    diagnosing.value = true
    try {
      diagnosis.value = await api.diagnoseUnion()
    }
    finally {
      diagnosing.value = false
    }
  }

  return reactive({
    loading, saving, diagnosing, syncingKey, diagnosis, keyPairs, unionState, keySyncResult, profileSyncState,
    sharedCandidates, form,
    load, loadSharedCandidates, save, reset, regenerateKeyPair, syncUnionPrivateKey, diagnoseUnion,
  })
}

export type YggcSettingsModel = ReturnType<typeof useYggcSettings>
