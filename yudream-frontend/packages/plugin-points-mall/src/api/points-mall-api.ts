import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type {
  MallAssetOption,
  MallDeliveryProof,
  MallItem,
  MallOverview,
  MallProofInput,
  MallRedemption,
  MallSettings,
  PageResult,
} from '../types'

/**
 * 插件 API 封装。
 *
 * <p>用户端只打 /me/**，管理员端只打 /admin/**，两边的路径不共享：用户端永远带不出别人的数据。
 */
export function createPointsMallApi(sdk: YuDreamPluginSdk) {
  function query(params: Record<string, string | number | boolean | undefined>) {
    const search = new URLSearchParams()
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== '') {
        search.set(key, String(value))
      }
    })
    const value = search.toString()
    return value ? `?${value}` : ''
  }

  const user = {
    overview: () => sdk.http.get<MallOverview>('/me/overview'),
    items: (keyword = '', page = 1, size = 12) => sdk.http.get<PageResult<MallItem>>(`/me/items${query({ keyword, page, size })}`),
    redemptions: (page = 1, size = 10) => sdk.http.get<PageResult<MallRedemption>>(`/me/redemptions${query({ page, size })}`),
    redeem: (data: { itemId: string, quantity: number, remark: string }) => sdk.http.post<MallRedemption>('/me/redemptions', data),
    cancelRedemption: (redemptionId: string, reason: string) => sdk.http.post<MallRedemption>(
      `/me/redemptions/${encodeURIComponent(redemptionId)}/cancel`, { reason }),
    confirmRedemption: (redemptionId: string) => sdk.http.post<MallRedemption>(
      `/me/redemptions/${encodeURIComponent(redemptionId)}/confirm`, {}),
  }

  const admin = {
    items: (keyword = '', enabled = '', page = 1, size = 10) => sdk.http.get<PageResult<MallItem>>(
      `/admin/items${query({ keyword, enabled, page, size })}`),
    createItem: (data: Record<string, unknown>) => sdk.http.post<MallItem>('/admin/items', data),
    updateItem: (itemId: string, data: Record<string, unknown>) => sdk.http.request<MallItem>(
      `/admin/items/${encodeURIComponent(itemId)}`, { method: 'PUT', data }),
    setItemEnabled: (itemId: string, enabled: boolean) => sdk.http.post<MallItem>(
      `/admin/items/${encodeURIComponent(itemId)}/enabled`, { enabled }),
    deleteItem: (itemId: string) => sdk.http.request(`/admin/items/${encodeURIComponent(itemId)}`, { method: 'DELETE' }),
    redemptions: (filters: Record<string, string>, page = 1, size = 10) => sdk.http.get<PageResult<MallRedemption>>(
      `/admin/redemptions${query({ ...filters, page, size })}`),
    deliverRedemption: (redemptionId: string, data: { note: string, files: MallProofInput[] }) =>
      sdk.http.post<MallRedemption>(`/admin/redemptions/${encodeURIComponent(redemptionId)}/deliver`, data),
    cancelRedemption: (redemptionId: string, reason: string) => sdk.http.post<MallRedemption>(
      `/admin/redemptions/${encodeURIComponent(redemptionId)}/cancel`, { reason }),
    settings: () => sdk.http.get<MallSettings>('/admin/settings'),
    saveSettings: (data: { enabled: boolean, notice: string }) => sdk.http.request<MallSettings>(
      '/admin/settings', { method: 'PUT', data }),
    assets: () => sdk.http.get<MallAssetOption[]>('/admin/assets'),
  }

  /** 封面图与发放凭证统一走宿主文件接口：文件落在对象存储（S3 桶），返回地址再保存到业务数据里。 */
  const files = {
    upload: (file: File) => sdk.files.uploadImage(file, { module: 'points-mall', publicAccess: true }),
    url: (path?: string) => (path ? sdk.files.assetUrl(path) : ''),
    thumb: (path?: string) => (path ? sdk.files.thumbUrl(path, { maxEdge: 400 }) : ''),
  }

  /** 用户选择用宿主 SDK 的权威用户检索，不让管理员手输用户 ID。 */
  const users = {
    search: (keyword = '', page = 1, size = 20) => sdk.users.search({ keyword, page, size }),
  }

  return { user, admin, files, users }
}

export type PointsMallProof = MallDeliveryProof
