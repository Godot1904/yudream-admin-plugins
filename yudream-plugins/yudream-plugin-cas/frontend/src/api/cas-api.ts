import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { ConnectivityResult, GateConfig, OidcRegisterResult, SsoSettings, StudentMapping, StudentPage, StudentPrefill, StudentProfile } from '../types'

export function createCasApi(sdk: YuDreamPluginSdk) {
  const { http } = sdk

  return {
    settings: () => http.get<SsoSettings>('/admin/settings'),
    saveSettings: (data: SsoSettings, clientSecret?: string) =>
      http.request<SsoSettings>('/admin/settings', {
        method: 'PUT',
        data: {
          enabled: data.enabled,
          protocol: data.protocol,
          displayName: data.displayName,
          icon: data.icon,
          casBaseUrl: data.casBaseUrl,
          loginPath: data.loginPath,
          validatePath: data.validatePath,
          oidcIssuer: data.oidcIssuer,
          oidcAuthorizePath: data.oidcAuthorizePath,
          oidcTokenPath: data.oidcTokenPath,
          oidcUserinfoPath: data.oidcUserinfoPath,
          oidcJwksPath: data.oidcJwksPath,
          oidcRegisterPath: data.oidcRegisterPath,
          clientId: data.clientId,
          clientSecret: clientSecret || undefined,
          scopes: data.scopes,
          callbackUrl: data.callbackUrl,
        },
      }),
    test: () => http.post<ConnectivityResult>('/admin/test', {}),
    registerOidc: (clientName?: string) =>
      http.post<OidcRegisterResult>('/admin/oidc/register', { clientName }),
    mapping: () => http.get<StudentMapping>('/admin/mapping'),
    saveMapping: (data: StudentMapping) =>
      http.request<StudentMapping>('/admin/mapping', { method: 'PUT', data }),
    students: (page: number, size: number, keyword?: string) =>
      http.get<StudentPage>(`/admin/students?page=${page}&size=${size}${keyword ? `&keyword=${encodeURIComponent(keyword)}` : ''}`),
    studentDetail: (socialUid: string) =>
      http.get<StudentProfile>(`/admin/students/detail?socialUid=${encodeURIComponent(socialUid)}`),
    gate: () => http.get<GateConfig>('/public/gate'),
    myProfile: (socialUid: string) =>
      http.get<StudentPrefill>(`/me/profile?socialUid=${encodeURIComponent(socialUid)}`),
  }
}

export type CasApi = ReturnType<typeof createCasApi>
