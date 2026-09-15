import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type {
  EduroamAccount,
  EduroamAttempt,
  EduroamLoginResult,
  EduroamPage,
  EduroamPublicConfig,
  EduroamSettings,
} from '../types'

function query(params: Record<string, string | number | boolean | undefined | null>) {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value))
    }
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}

export function createEduroamApi(sdk: YuDreamPluginSdk) {
  const { http } = sdk

  return {
    // ---- 公开端：第三方登录凭据页 ----
    publicConfig: () => http.get<EduroamPublicConfig>('/public/config'),
    login: (account: string, password: string, state: string) =>
      http.post<EduroamLoginResult>('/public/login', { account, password, state }),

    // ---- 管理端：登录账号台账 ----
    accounts: (status: string, keyword: string, page: number, size: number) =>
      http.get<EduroamPage<EduroamAccount>>(`/admin/accounts${query({ status, keyword, page, size })}`),
    account: (id: string) => http.get<EduroamAccount>(`/admin/accounts/${encodeURIComponent(id)}`),
    blockAccount: (id: string, reason: string) =>
      http.post<EduroamAccount>(`/admin/accounts/${encodeURIComponent(id)}/block`, { reason }),
    unblockAccount: (id: string) =>
      http.post<EduroamAccount>(`/admin/accounts/${encodeURIComponent(id)}/unblock`, {}),
    deleteAccount: (id: string) =>
      http.request<{ deleted: boolean }>(`/admin/accounts/${encodeURIComponent(id)}`, { method: 'DELETE' }),

    // ---- 管理端：尝试审计 ----
    attempts: (keyword: string, success: string, page: number, size: number) =>
      http.get<EduroamPage<EduroamAttempt>>(`/admin/attempts${query({ keyword, success, page, size })}`),

    // ---- 管理端：配置 ----
    settings: () => http.get<EduroamSettings>('/admin/settings'),
    saveSettings: (data: EduroamSettings) =>
      http.request<EduroamSettings>('/admin/settings', { method: 'PUT', data }),
  }
}

export type EduroamApi = ReturnType<typeof createEduroamApi>
