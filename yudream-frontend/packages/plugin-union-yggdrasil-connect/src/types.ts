export interface YggcScopeView {
  name: string
  description: string
}

export interface YggcProfileView {
  id: string
  name: string
}

export interface YggcClientView {
  id: string
  name: string
  redirectUris: string[]
  publicClient: boolean
  enabled: boolean
  createdAt: number
  secret?: string
}

/** GET /admin/shared-clients：可绑定为共享客户端的应用（启用中的公共客户端）。 */
export interface YggcEligibleClientView extends YggcClientView {
  /** 是否就是当前发现文档声明的共享客户端。 */
  shared?: boolean
}

export interface YggcTokenView {
  token: string
  clientId: string
  userId: string
  nickname: string
  profileId: string
  profileName: string
  scopes: string[]
  issuedAt: number
  expiresAt: number
}

export interface YggcClientPage {
  records: YggcClientView[]
  total: number
}

export interface YggcTokenPage {
  records: YggcTokenView[]
  total: number
}

export interface YggcAuthorizeContext {
  client: { id: string, name: string }
  scopes: YggcScopeView[]
  profiles: YggcProfileView[]
  requireProfileSelection: boolean
  user: { id: string, nickname: string }
}

export interface YggcDeviceContext extends YggcAuthorizeContext {
  status: string
}

export interface YggcGrantGroup {
  clientId: string
  clientName: string
  clientEnabled: boolean
  tokens: YggcTokenView[]
}

export interface YggcStats {
  clients: number
  tokens: number
  sessions: number
}

export interface YggcStatus {
  apiRoot: string
  textureBaseUrl: string
  accountSource: string
  skinPluginEnabled: boolean
  stats?: YggcStats
  metadata?: Record<string, unknown>
  endpoints?: string[]
}

export interface YggcEndpoint {
  method: 'GET' | 'POST' | 'PUT' | 'DELETE'
  path: string
  note: string
}

/** 插件配置（对应原 yggdrasil-connect Option 配置项）。 */
export interface YggcSettings {
  uuidAlgorithm: string
  tokenExpire: number
  tokenRefreshExpire: number
  tokensLimit: number
  rateLimit: number
  skinDomain: string
  searchProfileMax: number
  showConfigSection: boolean
  enableAli: boolean
  restoreApi: boolean
  disableAuthserver: boolean
  connectServerUrl: string
  unionApiRoot: string
  unionMemberKey: string
  unionEnableUpdate: boolean
  unionEnableOauth2: boolean
  /** 定期把本站角色同步到 MUA 主服务器（增量对账） */
  unionSyncEnabled: boolean
  /** 角色同步间隔（分钟，1 - 1440） */
  unionSyncIntervalMinutes: number
  /** 玩家登录 / 进入服务器时补推他本人的新角色 */
  unionSyncOnLogin: boolean
  oauthAccessTtl: number
  oauthRefreshTtl: number
  oauthDeviceTtl: number
  /** 认证服务器名称（meta.serverName），留空回退站点名 */
  serverName: string
  /**
   * 共享客户端标识：写入发现文档 shared_client_id，供没有内置 client_id 的
   * Yggdrasil Connect 启动器直接登录；留空则不输出该字段。
   */
  sharedClientId: string
  keyPairs?: {
    texture?: YggcKeyPairInfo
    token?: YggcKeyPairInfo
    'union-oauth2'?: YggcKeyPairInfo
  }
  union?: YggcUnionLocalState
  /** 角色同步状态（GET /admin/config 附带返回） */
  profileSync?: YggcUnionProfileSyncState
}

export interface YggcKeyPairInfo {
  usage: string
  exists: boolean
  publicKey?: string
  kid?: string
}

export interface YggcUnionDiagnosis {
  apiRoot: string
  memberKeyConfigured: boolean
  reachable: boolean
  status?: number
  latencyMs?: number
  body?: string
  message: string
}

// ---- Union 联邦 ----

/** Union 皮肤站列表条目（GET /serverlist → servers[]）。 */
export interface YggcUnionServer {
  code?: string
  bs_root?: string
}

/** 跨站绑定条目（unmapped/byname 列表与 detail.bind 列表共用结构）。 */
export interface YggcUnionBindEntry {
  internal_id?: number
  bind_mapped_name?: string
  backend_scopes?: { self?: string }
  backend?: string
  uuid?: string
  mapped_uuid?: string
  /** 0 = 正在使用的绑定（主站） */
  bind_status?: number
}

/** 单个角色的跨站详情（GET /profile/detail/{uuid}）。 */
export interface YggcUnionProfileDetail {
  internal_id?: number
  name?: string
  uuid?: string
  mapped_uuid?: string
  bind_mapped_name?: string
  backend_scopes?: { self?: string }
  backend?: string
  bind?: YggcUnionBindEntry[]
  bind_status?: number
}

/** 用户单个角色的跨站总览（后端 overview 组装）。 */
export interface YggcUnionProfileOverview {
  uuid: string
  name: string
  duplicateNames: YggcUnionBindEntry[]
  detail: YggcUnionProfileDetail
}

/** 用户跨站角色总览（GET /me/union/overview）。 */
export interface YggcUnionOverview {
  profiles: YggcUnionProfileOverview[]
  serverList: YggcUnionServer[]
  securityLevel: unknown
}

/** 签名私钥同步状态（存于 union state 集合 privatekey 文档）。 */
export interface YggcUnionPrivateKeyState {
  version?: string | null
  syncedAt?: number
  source?: string
}

/** 本地联邦状态（不触发上游请求）。 */
export interface YggcUnionLocalState {
  privateKey: YggcUnionPrivateKeyState
  privateKeySynced: boolean
  serverList: {
    servers?: YggcUnionServer[]
    version?: string | null
    syncedAt?: number
  }
  serverCount: number
}

/** 角色同步（本地角色 → MUA 主服务器）的状态与上次结果。 */
export interface YggcUnionProfileSyncState {
  /** 定时自动同步开关 */
  enabled: boolean
  /** 同步间隔（分钟） */
  intervalMinutes: number
  /** 玩家登录时补推开关 */
  onLoginPush: boolean
  /** Union API Root 与 Member Key 是否都已配置 */
  ready: boolean
  /** 当前是否有一轮同步在执行 */
  running: boolean
  /** 已确认推送到主服务器的角色数 */
  pushedCount: number
  /** 上次同步时间 */
  syncedAt?: number
  /** 上次同步方式：delta（增量对账）/ full（全量推送）/ bootstrap（首次回落全量）/ login（登录补推） */
  mode?: string
  lastResult?: {
    mode?: string
    added?: number
    renamed?: number
    removed?: number
    failed?: number
    pushedCount?: number
    message?: string
  }
  /** 最近的致命失败（上游不可达等），成功后不再保留 */
  lastError?: string
}

/** 上游连通性（GET / 公告探测）。 */
export interface YggcUnionUpstream {
  reachable: boolean
  status?: number
  latencyMs?: number
  response?: Record<string, unknown>
}

/** Union 状态总览（GET /admin/union/status）。 */
export interface YggcUnionStatus extends YggcUnionLocalState {
  apiRoot: string
  memberKeyConfigured: boolean
  upstream: YggcUnionUpstream
  profileSync: YggcUnionProfileSyncState
}

/** 从上游同步签名私钥的响应。 */
export interface YggcUnionPrivateKeySyncResult {
  privateKeyVersion?: string
  syncedAt?: number
  publicKey?: string
  message?: string
}

/** 全量角色同步的响应。 */
export interface YggcUnionSyncResult {
  profileCount?: number
  message?: string
  [key: string]: unknown
}

/** Union 黑名单代理响应（上游原样透传，形状由 Union 主服务器决定）。 */
export type YggcUnionBlacklistResult = Record<string, unknown>
