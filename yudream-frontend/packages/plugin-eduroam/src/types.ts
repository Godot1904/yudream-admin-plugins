/** 公开渠道信息：凭据页展示账号填写提示与教程（不含认证服务地址与本站邮箱域）。 */
export interface EduroamPublicConfig {
  enabled: boolean
  eduDomain: string
  accountHint: string
  tutorialMarkdown: string
}

/**
 * 凭据认证结果。成功时带一次性票据，凭据页据此回宿主的第三方登录回调端点。
 */
export interface EduroamLoginResult {
  success: boolean
  email: string
  identity: string
  account: string
  ticket: string
  expiresAt: number
  reasonCode: string
  message: string
  registrationRequired: boolean
  accountCreated: boolean
}

/**
 * 登录过的 Eduroam 账号（管理端）。
 *
 * `localUserId` 是该「本站邮箱」在本地对应的账号，为空表示站内还没注册这个邮箱。
 */
export interface EduroamAccount {
  id: string
  email: string
  identity: string
  account: string
  domain: string
  status: string
  statusLabel: string
  lastLoginIp: string
  lastLoginAt: number
  loginCount: number
  firstLoginAt: number
  blockedByUserId: string
  blockReason: string
  blockedAt: number
  localUserId: string
  localUsername: string
  localNickname: string
  createdAt: number
  updatedAt: number
}

export interface EduroamAttempt {
  id: string
  email: string
  identity: string
  account: string
  domain: string
  success: boolean
  reasonCode: string
  reasonLabel: string
  message: string
  detail: string
  clientIp: string
  latencyMs: number
  createdAt: number
}

export interface EduroamSettings {
  enabled: boolean
  eduDomain: string
  storeDomain: string
  verifyEndpoint: string
  connectTimeoutSeconds: number
  requestTimeoutSeconds: number
  maxAttemptsPerHour: number
  tutorialMarkdown: string
}

export interface EduroamPage<T> {
  records: T[]
  total: number
  page: number
  size: number
}

/** 后端整型可能按字符串下发，这里统一收敛成数字。 */
export function num(value: unknown): number {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

export function formatTime(value?: number | string | null): string {
  if (value === undefined || value === null || value === '') {
    return '-'
  }
  const numeric = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return '-'
  }
  return new Date(numeric).toLocaleString('zh-CN', { hour12: false })
}

/** 账号状态标签配色：禁止登录用危险态。 */
export function statusVariant(status: string): 'default' | 'destructive' {
  return status === 'BLOCKED' ? 'destructive' : 'default'
}

/** 后端抛出的业务错误统一是 { message }，取不出来时回落到兜底文案。 */
export function errorMessage(error: unknown, fallback = '操作失败'): string {
  const message = (error as { response?: { data?: { message?: string } }, message?: string })?.response?.data?.message
    || (error as { message?: string })?.message
  return message && message.trim() ? message : fallback
}
