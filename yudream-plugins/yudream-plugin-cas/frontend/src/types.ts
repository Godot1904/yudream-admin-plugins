export interface SsoSettings {
  enabled: boolean
  protocol: 'CAS' | 'OIDC'
  displayName: string
  icon: string
  casBaseUrl: string
  loginPath: string
  validatePath: string
  oidcIssuer: string
  oidcAuthorizePath: string
  oidcTokenPath: string
  oidcUserinfoPath: string
  oidcJwksPath: string
  oidcRegisterPath: string
  clientId: string
  clientSecretConfigured: boolean
  scopes: string
  callbackUrl: string
  /** 登录前是否走本站预热页（先跨站请求拿网关会话 cookie 再跳认证地址）。 */
  loginWarmup: boolean
  ready: boolean
}

export interface ConnectivityResult {
  ok: boolean
  message: string
}

export interface OidcRegisterResult {
  clientId: string
  clientSecretIssued: boolean
}

/** 学生信息映射与访问控制配置（独立于 SsoSettings 存储）。 */
export interface StudentMapping {
  requireBinding: boolean
  nameKey: string
  deptKey: string
  majorKey: string
  gradeKey: string
  classKey: string
}

/**
 * 该外部账号在本站的绑定查询结果（宿主 SPI 2.29.0 findByExternalIdentity）。
 * available=false 表示宿主不支持查询或查询失败，此时 message 说明原因，页面按「无法查询」降级展示。
 */
export interface ExternalBinding {
  available: boolean
  bound: boolean
  message?: string
  userId?: string
  username?: string
  nickname?: string
  email?: string
  phone?: string
  avatar?: string
  status?: string
}

/** CAS/OIDC 登录时 upsert 的学生档案。 */
export interface StudentProfile {
  socialUid: string
  name: string
  dept: string
  major: string
  grade: string
  className: string
  email: string
  phone: string
  protocol: string
  rawAttributes: string
  firstSeenAt: number
  lastSeenAt: number
  loginCount: number
  /** 该学工号绑定的本站账号（管理端列表/详情附加）。 */
  binding?: ExternalBinding
}

export interface StudentPage {
  items: StudentProfile[]
  total: number
  page: number
  size: number
}

/** 公开门禁配置：前端全局挂件读取。 */
export interface GateConfig {
  requireBinding: boolean
  providerCode: string
  type: string
  displayName: string
}

/** 宿主 /api/user/me/external-accounts 返回的绑定记录（仅用到的字段）。 */
export interface HostExternalAccount {
  providerCode?: string
  platformType?: string
  socialUid?: string
}

/** /me/profile 返回的档案预填字段（最小字段集，不含邮箱/电话/原始属性）。 */
export interface StudentPrefill {
  studentNo: string
  studentName: string
  className: string
  college: string
  major: string
  grade: string
}

export function errorMessage(error: unknown, fallback = '操作失败') {
  if (error && typeof error === 'object') {
    const record = error as { message?: string, data?: { message?: string }, response?: { data?: { message?: string } } }
    return record.response?.data?.message || record.data?.message || record.message || fallback
  }
  return fallback
}
