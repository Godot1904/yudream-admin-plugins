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
