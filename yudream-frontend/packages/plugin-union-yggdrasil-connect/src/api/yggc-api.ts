import type {
  YggcAuthorizeContext,
  YggcClientPage,
  YggcClientView,
  YggcDeviceContext,
  YggcEligibleClientView,
  YggcGrantGroup,
  YggcKeyPairInfo,
  YggcSettings,
  YggcStatus,
  YggcTokenPage,
  YggcUnionBlacklistResult,
  YggcUnionDiagnosis,
  YggcUnionOverview,
  YggcUnionPrivateKeySyncResult,
  YggcUnionStatus,
  YggcUnionSyncResult,
} from '../types'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'

export type AuthorizeParams = Record<string, string>

function buildQuery(params: Record<string, string | number | boolean | undefined | null>) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value))
    }
  })
  const value = search.toString()
  return value ? `?${value}` : ''
}

export function createYggcApi(sdk: YuDreamPluginSdk) {
  return {
    apiUrl: (path = '/') => sdk.http.url(path),
    // ---- 管理端 ----
    status: () => sdk.http.get<YggcStatus>('/admin/status'),
    clients: (keyword: string, page: number, size: number) =>
      sdk.http.get<YggcClientPage>(`/admin/clients${buildQuery({ keyword, page, size })}`),
    eligibleSharedClients: () =>
      sdk.http.get<YggcEligibleClientView[]>('/admin/shared-clients'),
    createClient: (data: Record<string, unknown>) => sdk.http.post<YggcClientView>('/admin/clients', data),
    updateClient: (id: string, data: Record<string, unknown>) =>
      sdk.http.request<YggcClientView>(`/admin/clients/${encodeURIComponent(id)}`, { method: 'PUT', data }),
    deleteClient: (id: string) =>
      sdk.http.request(`/admin/clients/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    resetClientSecret: (id: string) =>
      sdk.http.post<YggcClientView>(`/admin/clients/${encodeURIComponent(id)}/secret`),
    tokens: (page: number, size: number) =>
      sdk.http.get<YggcTokenPage>(`/admin/tokens${buildQuery({ page, size })}`),
    revokeToken: (token: string) =>
      sdk.http.request(`/admin/tokens/${encodeURIComponent(token)}`, { method: 'DELETE' }),
    // ---- 管理端：插件配置 ----
    config: () => sdk.http.get<YggcSettings>('/admin/config'),
    updateConfig: (data: Record<string, unknown>) =>
      sdk.http.request<YggcSettings>('/admin/config', { method: 'PUT', data }),
    resetConfig: () => sdk.http.post<YggcSettings>('/admin/config/reset'),
    regenerateKeyPair: (usage: 'texture' | 'token' | 'union-oauth2') =>
      sdk.http.post<YggcKeyPairInfo>(`/admin/config/keypair/${usage}`),
    diagnoseUnion: () => sdk.http.post<YggcUnionDiagnosis>('/admin/config/union/diagnose'),
    // ---- 管理端：MUA Union 同步 ----
    unionStatus: () => sdk.http.get<YggcUnionStatus>('/admin/union/status'),
    syncUnionPrivateKey: () =>
      sdk.http.post<YggcUnionPrivateKeySyncResult>('/admin/union/sync-privatekey'),
    syncUnionServerList: () =>
      sdk.http.post<YggcUnionSyncResult>('/admin/union/sync-serverlist'),
    syncUnionProfiles: () => sdk.http.post<YggcUnionSyncResult>('/admin/union/sync-profiles'),
    reconcileUnionProfiles: () => sdk.http.post<YggcUnionSyncResult>('/admin/union/reconcile-profiles'),
    // ---- 管理端：MUA 黑名单代理 ----
    blacklistQuery: (params: Record<string, string>) =>
      sdk.http.get<YggcUnionBlacklistResult>(`/admin/union/blacklist${buildQuery(params)}`),
    blacklistCreate: (data: Record<string, unknown>) =>
      sdk.http.post<YggcUnionBlacklistResult>('/admin/union/blacklist', data),
    blacklistInvalidate: (id: string) =>
      sdk.http.request<YggcUnionBlacklistResult>(`/admin/union/blacklist/invalidate/${encodeURIComponent(id)}`, { method: 'PUT' }),
    blacklistDelete: (id: string) =>
      sdk.http.request<YggcUnionBlacklistResult>(`/admin/union/blacklist/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    // ---- 用户端：MUA 跨站角色 ----
    unionOverview: () => sdk.http.get<YggcUnionOverview>('/me/union/overview'),
    unionBind: (uuid: string) =>
      sdk.http.post<{ token?: string }>('/me/union/bind', { uuid }),
    unionBindTo: (uuid: string, token: string) =>
      sdk.http.post('/me/union/bindto', { uuid, token }),
    unionUnbind: (uuid: string) => sdk.http.post('/me/union/unbind', { uuid }),
    unionRemapUuid: (me: string, target: string) =>
      sdk.http.post('/me/union/remapuuid', { me, target }),
    // ---- 用户端 ----
    myGrants: () => sdk.http.get<YggcGrantGroup[]>('/me/grants'),
    revokeMyToken: (token: string) =>
      sdk.http.request(`/me/grants/tokens/${encodeURIComponent(token)}`, { method: 'DELETE' }),
    revokeMyClient: (clientId: string) =>
      sdk.http.request(`/me/grants/clients/${encodeURIComponent(clientId)}`, { method: 'DELETE' }),
    authorizeContext: (params: AuthorizeParams) =>
      sdk.http.get<YggcAuthorizeContext>(`/me/authorize${buildQuery(params)}`),
    authorizeDecision: (params: AuthorizeParams, data: Record<string, unknown>) =>
      sdk.http.post<{ redirectUrl: string }>(`/me/authorize${buildQuery(params)}`, data),
    deviceContext: (userCode: string) =>
      sdk.http.get<YggcDeviceContext>(`/me/device${buildQuery({ user_code: userCode })}`),
    deviceDecision: (data: Record<string, unknown>) =>
      sdk.http.post<{ status: string }>('/me/device', data),
  }
}
