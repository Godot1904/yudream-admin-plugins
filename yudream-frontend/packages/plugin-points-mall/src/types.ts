export interface PageResult<T> {
  records: T[]
  total: number
}

export interface MallItem {
  id: string
  name: string
  description: string
  imageUrl: string
  assetCode: string
  assetName: string
  assetSymbol: string
  pricePoints: number
  stock: number
  perUserLimit: number
  enabled: boolean
  sort: number
  available: boolean
  createdAt: number
  updatedAt: number
}

export type MallRedemptionStatus = 'PENDING' | 'DELIVERED' | 'COMPLETED' | 'CANCELLED'

/** 发放凭证：图片直接展示，其他类型给下载链接。 */
export interface MallDeliveryProof {
  url: string
  filename: string
  contentType: string
  size: number
  image: boolean
}

export interface MallRedemption {
  id: string
  itemId: string
  itemName: string
  userId: string
  userName: string
  assetCode: string
  quantity: number
  unitPoints: number
  totalPoints: number
  status: MallRedemptionStatus
  statusLabel: string
  remark: string
  proofs: MallDeliveryProof[]
  deliveredByUserId: string
  deliveryNote: string
  deliveredAt: number
  confirmedAt: number
  cancelNote: string
  createdAt: number
  updatedAt: number
}

/** 商城设置：只有商城开关与兑换须知，结算资产按商品设置。 */
export interface MallSettings {
  enabled: boolean
  notice: string
}

/** 概览里的一项资产余额。 */
export interface MallAssetBalance {
  code: string
  name: string
  symbol: string
  balance: string
}

export interface MallOverview {
  enabled: boolean
  assets: MallAssetBalance[]
  itemCount: number
  pendingCount: number
  deliveredCount: number
  totalCount: number
  notice: string
}

export interface MallAssetOption {
  code: string
  name: string
  symbol: string
  scale: number
  enabled: boolean
  money: boolean
}

export interface MallUserOption {
  id: string
  username: string
  nickname?: string
}

export interface MallItemForm {
  id: string
  name: string
  description: string
  imageUrl: string
  assetCode: string
  pricePoints: number
  stock: number
  perUserLimit: number
  enabled: boolean
  sort: number
}

export interface MallRedeemForm {
  itemId: string
  quantity: number
  remark: string
}

export interface MallSettingsForm {
  enabled: boolean
  notice: string
}

/** 发放时提交的凭证，字段对应上传接口返回的文件信息。 */
export interface MallProofInput {
  url: string
  filename: string
  contentType: string
  size: number
}

/** 后端把整型按字符串下发（宿主统一把 Long/整型序列化成字符串），这里统一收敛成数字。 */
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

export function formatSize(value?: number | string | null): string {
  const size = num(value)
  if (size <= 0) {
    return '-'
  }
  if (size < 1024) {
    return `${size} B`
  }
  if (size < 1024 * 1024) {
    return `${Math.max(1, Math.round(size / 1024))} KB`
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

/** 库存展示：-1 是不限量，0 是已兑完。 */
export function stockLabel(item: Pick<MallItem, 'stock' | 'available'>): string {
  const stock = num(item.stock)
  if (stock < 0) {
    return '不限量'
  }
  if (stock <= 0) {
    return '已兑完'
  }
  return `剩 ${stock} 件`
}

export function perUserLimitLabel(item: Pick<MallItem, 'perUserLimit'>): string {
  const limit = num(item.perUserLimit)
  return limit > 0 ? `每人 ${limit} 件` : '不限件数'
}

/** 商品是否已经选好结算资产：老版本的商品没有这个字段，需要在管理端补选。 */
export function hasAsset(item: Pick<MallItem, 'assetCode'>): boolean {
  return Boolean(item.assetCode && item.assetCode.trim())
}

/** 商品的计价单位：优先用钱包里的符号，取不到就回落成资产代码或名称。 */
export function assetLabel(item: Pick<MallItem, 'assetSymbol' | 'assetName' | 'assetCode'>): string {
  return item.assetSymbol || item.assetName || item.assetCode || '未设置结算资产'
}

export function statusVariant(status: MallRedemptionStatus): 'default' | 'secondary' | 'destructive' | 'outline' {
  if (status === 'COMPLETED') {
    return 'default'
  }
  if (status === 'DELIVERED') {
    return 'outline'
  }
  return status === 'CANCELLED' ? 'destructive' : 'secondary'
}

/** 后端抛出的业务错误统一是 { message }，取不出来时回落到兜底文案。 */
export function errorMessage(error: unknown, fallback = '操作失败'): string {
  const message = (error as { response?: { data?: { message?: string } }, message?: string })?.response?.data?.message
    || (error as { message?: string })?.message
  return message && message.trim() ? message : fallback
}
