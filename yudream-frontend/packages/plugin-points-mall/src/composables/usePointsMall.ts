import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { useFaModal, useFaToast } from '@yudream/components'
import { computed, reactive, ref } from 'vue'
import { createPointsMallApi } from '../api/points-mall-api'
import { errorMessage, num } from '../types'
import type {
  MallAssetOption,
  MallDeliveryProof,
  MallItem,
  MallItemForm,
  MallOverview,
  MallProofInput,
  MallRedemption,
  MallRedeemForm,
  MallSettings,
  MallSettingsForm,
  MallUserOption,
  PageResult,
} from '../types'

export type PointsMallPage = 'mall' | 'my-redemptions' | 'admin-items' | 'admin-redemptions' | 'admin-settings'

function emptyItemForm(): MallItemForm {
  return {
    id: '',
    name: '',
    description: '',
    imageUrl: '',
    assetCode: '',
    pricePoints: 100,
    stock: -1,
    perUserLimit: 0,
    enabled: true,
    sort: 0,
  }
}

/**
 * 积分商城的前端状态与工作流。
 *
 * <p>用户端与管理员端的状态分开存放（items / adminItems、myRedemptions / adminRedemptions），不共用一个
 * 会因为当前账号权限而改变数据范围的缓存。
 */
export function usePointsMall(sdk: YuDreamPluginSdk) {
  const api = createPointsMallApi(sdk)
  const toast = useFaToast()
  const modal = useFaModal()

  const loading = ref(false)
  const saving = ref(false)
  const uploadingCover = ref(false)

  const overview = ref<MallOverview | null>(null)
  const items = ref<MallItem[]>([])
  const myRedemptions = ref<MallRedemption[]>([])
  const itemPager = reactive({ page: 1, size: 12, total: 0 })
  const redemptionPager = reactive({ page: 1, size: 10, total: 0 })
  const keyword = ref('')

  const redeemForm = reactive<MallRedeemForm>({ itemId: '', quantity: 1, remark: '' })

  const adminItems = ref<MallItem[]>([])
  const adminItemPager = reactive({ page: 1, size: 10, total: 0 })
  const itemFilters = reactive({ keyword: '', enabled: '' })
  const itemForm = reactive<MallItemForm>(emptyItemForm())

  const adminRedemptions = ref<MallRedemption[]>([])
  const adminRedemptionPager = reactive({ page: 1, size: 10, total: 0 })
  const redemptionFilters = reactive({ userId: '', itemId: '', status: '' })
  const itemOptions = ref<MallItem[]>([])
  const userOptions = ref<MallUserOption[]>([])

  const settings = ref<MallSettings | null>(null)
  const settingsForm = reactive<MallSettingsForm>({ enabled: true, notice: '' })
  const assets = ref<MallAssetOption[]>([])

  /** 商品表单里的结算资产选项：只列钱包里启用的资产。 */
  const assetOptions = computed(() => assets.value
    .filter(asset => asset.enabled)
    .map(asset => ({
      label: `${asset.name}（${asset.code}${asset.money ? ' · 货币' : ' · 积分'}）`,
      value: asset.code,
    })))

  // ------------------------------------------------------------------ 归一化

  function normalizeItem(raw: MallItem): MallItem {
    return {
      ...raw,
      assetCode: raw.assetCode ?? '',
      assetName: raw.assetName ?? '',
      assetSymbol: raw.assetSymbol ?? '',
      pricePoints: num(raw.pricePoints),
      stock: num(raw.stock),
      perUserLimit: num(raw.perUserLimit),
      sort: num(raw.sort),
      createdAt: num(raw.createdAt),
      updatedAt: num(raw.updatedAt),
    }
  }

  function normalizeProof(raw: MallDeliveryProof): MallDeliveryProof {
    return {
      url: raw.url ?? '',
      filename: raw.filename ?? '',
      contentType: raw.contentType ?? '',
      size: num(raw.size),
      image: Boolean(raw.image),
    }
  }

  function normalizeRedemption(raw: MallRedemption): MallRedemption {
    return {
      ...raw,
      quantity: num(raw.quantity),
      unitPoints: num(raw.unitPoints),
      totalPoints: num(raw.totalPoints),
      proofs: (raw.proofs ?? []).map(normalizeProof),
      deliveredAt: num(raw.deliveredAt),
      confirmedAt: num(raw.confirmedAt),
      createdAt: num(raw.createdAt),
      updatedAt: num(raw.updatedAt),
    }
  }

  function normalizeOverview(raw: MallOverview): MallOverview {
    return {
      ...raw,
      assets: (raw.assets ?? []).map(asset => ({
        code: asset.code ?? '',
        name: asset.name ?? '',
        symbol: asset.symbol ?? '',
        balance: asset.balance ?? '0',
      })),
      itemCount: num(raw.itemCount),
      pendingCount: num(raw.pendingCount),
      deliveredCount: num(raw.deliveredCount),
      totalCount: num(raw.totalCount),
    }
  }

  function paged<T>(result: PageResult<T> | undefined, normalize: (row: T) => T) {
    return { records: (result?.records ?? []).map(normalize), total: num(result?.total) }
  }

  /** 当前登录人在某种资产上的余额（用户端概览里已按在架商品用到的资产列好）。 */
  function balanceOf(assetCode: string): number {
    const asset = overview.value?.assets.find(item => item.code === assetCode)
    return asset ? Number(asset.balance) || 0 : 0
  }

  function assetNameOf(assetCode: string): string {
    const asset = assets.value.find(item => item.code === assetCode)
    return asset ? asset.name : assetCode
  }

  /** 对象存储里的相对地址转成可访问地址；封面与凭证都走这里。 */
  function imageUrl(path?: string): string {
    return api.files.url(path)
  }

  function thumbUrl(path?: string): string {
    return api.files.thumb(path)
  }

  // ------------------------------------------------------------------ 上传

  /** FaImageUpload 的 http-request：上传后返回可直接保存的地址。 */
  async function uploadCover(file: File): Promise<string> {
    uploadingCover.value = true
    try {
      const uploaded = await api.files.upload(file)
      return uploaded.assetUrl || uploaded.url || ''
    } catch (error) {
      toast.error(errorMessage(error, '封面上传失败'))
      throw error
    } finally {
      uploadingCover.value = false
    }
  }

  /** 发放凭证上传：返回保存到兑换记录里的字段。 */
  async function uploadProof(file: File): Promise<MallProofInput> {
    try {
      const uploaded = await api.files.upload(file)
      return {
        url: uploaded.assetUrl || uploaded.url || '',
        filename: uploaded.originalName || file.name,
        contentType: uploaded.contentType || file.type || '',
        size: num(uploaded.size) || file.size,
      }
    } catch (error) {
      toast.error(errorMessage(error, '凭证上传失败'))
      throw error
    }
  }

  // ------------------------------------------------------------------ 用户端

  async function loadOverview() {
    try {
      overview.value = normalizeOverview(await api.user.overview())
    } catch (error) {
      toast.error(errorMessage(error, '加载积分概览失败'))
    }
  }

  async function loadItems(resetPage = false) {
    loading.value = true
    if (resetPage) {
      itemPager.page = 1
    }
    try {
      const result = paged(await api.user.items(keyword.value, itemPager.page, itemPager.size), normalizeItem)
      items.value = result.records
      itemPager.total = result.total
      clampPage(itemPager)
    } catch (error) {
      toast.error(errorMessage(error, '加载商品失败'))
    } finally {
      loading.value = false
    }
  }

  async function loadMyRedemptions(resetPage = false) {
    loading.value = true
    if (resetPage) {
      redemptionPager.page = 1
    }
    try {
      const result = paged(await api.user.redemptions(redemptionPager.page, redemptionPager.size), normalizeRedemption)
      myRedemptions.value = result.records
      redemptionPager.total = result.total
      clampPage(redemptionPager)
    } catch (error) {
      toast.error(errorMessage(error, '加载兑换记录失败'))
    } finally {
      loading.value = false
    }
  }

  function openRedeem(item: MallItem) {
    if (!item.assetCode) {
      toast.warning('该商品还没有设置结算资产，请联系管理员')
      return
    }
    redeemForm.itemId = item.id
    redeemForm.quantity = 1
    redeemForm.remark = ''
  }

  async function redeem() {
    if (!redeemForm.itemId) {
      toast.warning('请选择要兑换的商品')
      return
    }
    saving.value = true
    try {
      await api.user.redeem({
        itemId: redeemForm.itemId,
        quantity: Math.max(1, num(redeemForm.quantity)),
        remark: redeemForm.remark,
      })
      toast.success('兑换成功，等待管理员发放')
      redeemForm.itemId = ''
      await Promise.all([loadOverview(), loadItems()])
    } catch (error) {
      toast.error(errorMessage(error, '兑换失败'))
    } finally {
      saving.value = false
    }
  }

  async function cancelMyRedemption(redemption: MallRedemption, reason: string) {
    saving.value = true
    try {
      await api.user.cancelRedemption(redemption.id, reason)
      toast.success('已取消，积分已退回')
      await Promise.all([loadOverview(), loadMyRedemptions()])
    } catch (error) {
      toast.error(errorMessage(error, '取消失败'))
    } finally {
      saving.value = false
    }
  }

  async function confirmReceipt(redemption: MallRedemption) {
    saving.value = true
    try {
      await api.user.confirmRedemption(redemption.id)
      toast.success('已确认收货')
      await Promise.all([loadOverview(), loadMyRedemptions()])
    } catch (error) {
      toast.error(errorMessage(error, '确认失败'))
    } finally {
      saving.value = false
    }
  }

  // ------------------------------------------------------------------ 管理员：商品

  async function loadAdminItems(resetPage = false) {
    loading.value = true
    if (resetPage) {
      adminItemPager.page = 1
    }
    try {
      const result = paged(
        await api.admin.items(itemFilters.keyword, itemFilters.enabled, adminItemPager.page, adminItemPager.size),
        normalizeItem,
      )
      adminItems.value = result.records
      adminItemPager.total = result.total
      clampPage(adminItemPager)
    } catch (error) {
      toast.error(errorMessage(error, '加载商品失败'))
    } finally {
      loading.value = false
    }
  }

  function openCreateItem() {
    Object.assign(itemForm, emptyItemForm())
    itemForm.assetCode = assetOptions.value[0]?.value || ''
  }

  function openEditItem(item: MallItem) {
    Object.assign(itemForm, {
      id: item.id,
      name: item.name,
      description: item.description,
      imageUrl: item.imageUrl,
      assetCode: item.assetCode,
      pricePoints: num(item.pricePoints),
      stock: num(item.stock),
      perUserLimit: num(item.perUserLimit),
      enabled: item.enabled,
      sort: num(item.sort),
    })
  }

  async function saveItem() {
    if (!itemForm.name.trim()) {
      toast.warning('请填写商品名称')
      return false
    }
    if (!itemForm.assetCode) {
      toast.warning('请选择结算资产')
      return false
    }
    saving.value = true
    const payload = {
      name: itemForm.name.trim(),
      description: itemForm.description,
      imageUrl: itemForm.imageUrl,
      assetCode: itemForm.assetCode,
      pricePoints: Math.max(0, num(itemForm.pricePoints)),
      stock: num(itemForm.stock),
      perUserLimit: Math.max(0, num(itemForm.perUserLimit)),
      enabled: itemForm.enabled,
      sort: Math.max(0, num(itemForm.sort)),
    }
    try {
      if (itemForm.id) {
        await api.admin.updateItem(itemForm.id, payload)
        toast.success('商品已更新')
      } else {
        await api.admin.createItem(payload)
        toast.success('商品已创建')
      }
      await loadAdminItems()
      return true
    } catch (error) {
      toast.error(errorMessage(error, '保存商品失败'))
      return false
    } finally {
      saving.value = false
    }
  }

  async function toggleItem(item: MallItem) {
    saving.value = true
    try {
      await api.admin.setItemEnabled(item.id, !item.enabled)
      toast.success(item.enabled ? '已下架' : '已上架')
      await loadAdminItems()
    } catch (error) {
      toast.error(errorMessage(error, '操作失败'))
    } finally {
      saving.value = false
    }
  }

  function confirmDeleteItem(item: MallItem) {
    modal.confirm({
      title: '删除商品',
      content: `确认删除「${item.name}」吗？已经产生兑换记录的商品只能下架，删除会被拒绝。`,
      onConfirm: async () => {
        try {
          await api.admin.deleteItem(item.id)
          toast.success('商品已删除')
          await loadAdminItems()
        } catch (error) {
          toast.error(errorMessage(error, '删除失败'))
        }
      },
    })
  }

  // ------------------------------------------------------------------ 管理员：兑换记录

  async function loadAdminRedemptions(resetPage = false) {
    loading.value = true
    if (resetPage) {
      adminRedemptionPager.page = 1
    }
    try {
      const result = paged(await api.admin.redemptions(
        { userId: redemptionFilters.userId, itemId: redemptionFilters.itemId, status: redemptionFilters.status },
        adminRedemptionPager.page,
        adminRedemptionPager.size,
      ), normalizeRedemption)
      adminRedemptions.value = result.records
      adminRedemptionPager.total = result.total
      clampPage(adminRedemptionPager)
    } catch (error) {
      toast.error(errorMessage(error, '加载兑换记录失败'))
    } finally {
      loading.value = false
    }
  }

  /** 商品与用户筛选都用系统里的权威列表，管理员不需要手输 ID。 */
  async function loadItemOptions() {
    try {
      const result = paged(await api.admin.items('', '', 1, 100), normalizeItem)
      itemOptions.value = result.records
    } catch {
      itemOptions.value = []
    }
  }

  async function searchUsers(searchKeyword: string) {
    try {
      const result = await api.users.search(searchKeyword, 1, 20)
      userOptions.value = (result ?? []).map(user => ({ id: user.id, username: user.username, nickname: user.nickname }))
    } catch {
      userOptions.value = []
    }
  }

  /** 发放：凭证由页面收集好后传进来，服务端会再校验一次「至少一张」。 */
  async function deliverRedemption(redemption: MallRedemption, note: string, proofs: MallProofInput[]) {
    saving.value = true
    try {
      await api.admin.deliverRedemption(redemption.id, { note, files: proofs })
      toast.success('已发放，等待用户确认收货')
      await loadAdminRedemptions()
      return true
    } catch (error) {
      toast.error(errorMessage(error, '发放失败'))
      return false
    } finally {
      saving.value = false
    }
  }

  async function cancelRedemption(redemption: MallRedemption, reason: string) {
    saving.value = true
    try {
      await api.admin.cancelRedemption(redemption.id, reason)
      toast.success('已取消并退回积分')
      await loadAdminRedemptions()
    } catch (error) {
      toast.error(errorMessage(error, '取消失败'))
    } finally {
      saving.value = false
    }
  }

  // ------------------------------------------------------------------ 管理员：设置

  async function loadSettings() {
    try {
      const result = await api.admin.settings()
      settings.value = result
      settingsForm.enabled = result.enabled
      settingsForm.notice = result.notice
    } catch (error) {
      toast.error(errorMessage(error, '加载商城设置失败'))
    }
  }

  async function loadAssets() {
    try {
      assets.value = await api.admin.assets()
    } catch (error) {
      toast.error(errorMessage(error, '加载钱包资产失败'))
    }
  }

  async function saveSettings() {
    saving.value = true
    try {
      const saved = await api.admin.saveSettings({ enabled: settingsForm.enabled, notice: settingsForm.notice })
      settings.value = saved
      toast.success('商城设置已保存')
      return true
    } catch (error) {
      toast.error(errorMessage(error, '保存设置失败'))
      return false
    } finally {
      saving.value = false
    }
  }

  // ------------------------------------------------------------------ 页面调度

  async function loadPage(page: PointsMallPage) {
    if (page === 'mall') {
      await Promise.all([loadOverview(), loadItems(true)])
      return
    }
    if (page === 'my-redemptions') {
      await loadMyRedemptions(true)
      return
    }
    if (page === 'admin-items') {
      await Promise.all([loadAdminItems(true), loadAssets()])
      return
    }
    if (page === 'admin-redemptions') {
      await Promise.all([loadAdminRedemptions(true), loadItemOptions(), searchUsers('')])
      return
    }
    if (page === 'admin-settings') {
      await loadSettings()
    }
  }

  function clampPage(pager: { page: number, size: number, total: number }) {
    const maxPage = Math.max(1, Math.ceil(pager.total / Math.max(1, pager.size)))
    if (pager.page > maxPage) {
      pager.page = maxPage
    }
  }

  /**
   * 用 reactive 包一层再交给页面：页面拿到的就是普通值（ref 自动解包），模板里不需要写 .value，
   * v-model 也能直接绑到 keyword / itemFilters 这些字段上。
   */
  return reactive({
    loading,
    saving,
    uploadingCover,
    // 用户端
    overview,
    items,
    itemPager,
    keyword,
    redeemForm,
    assetOptions,
    loadOverview,
    loadItems,
    openRedeem,
    redeem,
    balanceOf,
    assetNameOf,
    // 我的兑换
    myRedemptions,
    redemptionPager,
    loadMyRedemptions,
    cancelMyRedemption,
    confirmReceipt,
    // 上传与文件地址
    uploadCover,
    uploadProof,
    imageUrl,
    thumbUrl,
    // 商品管理
    adminItems,
    adminItemPager,
    itemFilters,
    itemForm,
    loadAdminItems,
    openCreateItem,
    openEditItem,
    saveItem,
    toggleItem,
    confirmDeleteItem,
    // 兑换记录
    adminRedemptions,
    adminRedemptionPager,
    redemptionFilters,
    itemOptions,
    userOptions,
    loadAdminRedemptions,
    searchUsers,
    deliverRedemption,
    cancelRedemption,
    // 设置
    settings,
    settingsForm,
    assets,
    loadSettings,
    loadAssets,
    saveSettings,
    // 调度
    loadPage,
  })
}

export type PointsMallModel = ReturnType<typeof usePointsMall>
