<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { PointsMallModel } from '../composables/usePointsMall'
import type { MallItem } from '../types'
import { FaButton, FaIcon, FaImageUpload, FaInput, FaModal, FaNumberField, FaPageHeader, FaPageMain, FaPagination, FaResponsiveTable, FaSearchBar, FaSelect, FaSwitch, FaTag, FaTextarea } from '@yudream/components'
import { computed, ref, watch } from 'vue'
import { assetLabel, formatTime, hasAsset, perUserLimitLabel, stockLabel } from '../types'

const props = defineProps<{ model: PointsMallModel }>()

const modalVisible = ref(false)

// FaImageUpload 内部通过 push/splice 原地改数组，不会触发 update:modelValue；
// 用独立 ref 接住组件持有的数组引用，再 watch 双向同步 form.imageUrl。
const coverList = ref<string[]>([])

watch(coverList, (list) => {
  const last = list.length ? list[list.length - 1] : ''
  if (last !== props.model.itemForm.imageUrl) {
    props.model.itemForm.imageUrl = last
  }
}, { deep: true })

watch(() => props.model.itemForm.imageUrl, (value) => {
  const current = coverList.value[coverList.value.length - 1] || ''
  if (value !== current) {
    coverList.value = value ? [value] : []
  }
})

const enabledOptions = [
  { label: '全部状态', value: '' },
  { label: '已上架', value: 'true' },
  { label: '已下架', value: 'false' },
]

const columns: TableColumn<MallItem>[] = [
  { accessorKey: 'name', header: '商品', width: 260 },
  { accessorKey: 'assetCode', header: '结算资产', width: 140 },
  { accessorKey: 'pricePoints', header: '所需积分', width: 110, align: 'right' },
  { accessorKey: 'stock', header: '库存', width: 110, align: 'center' },
  { accessorKey: 'perUserLimit', header: '限兑', width: 110, align: 'center' },
  { accessorKey: 'enabled', header: '状态', width: 100, align: 'center' },
  { accessorKey: 'sort', header: '排序', width: 80, align: 'center' },
  { accessorKey: 'updatedAt', header: '更新时间', width: 180 },
  { id: 'operation', header: '操作', width: 250, align: 'center', fixed: 'right' },
]

const coverUpload = computed(() => async (options: { file: File }) => props.model.uploadCover(options.file))

function afterUpload(response: unknown) {
  return typeof response === 'string' ? response : ''
}

function openCreate() {
  props.model.openCreateItem()
  coverList.value = []
  modalVisible.value = true
}

function openEdit(item: MallItem) {
  props.model.openEditItem(item)
  coverList.value = item.imageUrl ? [item.imageUrl] : []
  modalVisible.value = true
}

async function submit() {
  const saved = await props.model.saveItem()
  if (saved) {
    modalVisible.value = false
  }
}
</script>

<template>
  <FaPageHeader title="商品管理" class="mb-0">
    <div class="pm-actions pm-actions-inline">
      <FaButton variant="outline" :loading="model.loading" @click="model.loadAdminItems()">
        <FaIcon name="i-ri:refresh-line" />
        刷新
      </FaButton>
      <FaButton @click="openCreate">
        <FaIcon name="i-ri:add-line" />
        新增商品
      </FaButton>
    </div>
  </FaPageHeader>

  <FaPageMain>
    <FaResponsiveTable
      v-loading="model.loading"
      row-key="id"
      table-root-class="max-w-full overflow-x-auto rounded-lg"
      table-class="min-w-[1340px]"
      border
      stripe
      column-visibility
      :columns="columns"
      :data="model.adminItems"
      empty-text="还没有商品"
    >
      <template #toolbar>
        <FaSearchBar class="w-full">
          <div class="pm-filters">
            <FaInput
              v-model="model.itemFilters.keyword"
              class="pm-search"
              placeholder="搜索商品名称或说明"
              clearable
              @keydown.enter="model.loadAdminItems(true)"
              @clear="model.loadAdminItems(true)"
            />
            <FaSelect
              v-model="model.itemFilters.enabled"
              :options="enabledOptions"
              class="pm-filter-select"
              @change="model.loadAdminItems(true)"
            />
            <div class="pm-actions">
              <FaButton variant="outline" @click="model.itemFilters.keyword = ''; model.itemFilters.enabled = ''; model.loadAdminItems(true)">重置</FaButton>
              <FaButton @click="model.loadAdminItems(true)">
                <FaIcon name="i-ri:search-line" />
                查询
              </FaButton>
            </div>
          </div>
        </FaSearchBar>
      </template>
      <template #cell-name="{ row }">
        <div class="pm-cell-item">
          <span v-if="row.original.imageUrl" class="pm-cell-cover">
            <img :src="model.thumbUrl(row.original.imageUrl)" :alt="row.original.name" loading="lazy">
          </span>
          <span class="pm-cell-main">
            <strong>{{ row.original.name }}</strong>
            <span class="pm-table-sub">{{ row.original.description || '暂无说明' }}</span>
          </span>
        </div>
      </template>
      <template #cell-assetCode="{ row }">
        <template v-if="hasAsset(row.original)">
          <strong>{{ assetLabel(row.original) }}</strong>
          <div class="pm-table-sub">{{ row.original.assetCode }}</div>
        </template>
        <template v-else>
          <FaTag variant="destructive">未设置</FaTag>
          <div class="pm-table-sub">请编辑商品并选择结算资产</div>
        </template>
      </template>
      <template #cell-stock="{ row }">{{ stockLabel(row.original) }}</template>
      <template #cell-perUserLimit="{ row }">{{ perUserLimitLabel(row.original) }}</template>
      <template #cell-enabled="{ row }">
        <FaTag :variant="row.original.enabled ? 'secondary' : 'destructive'">{{ row.original.enabled ? '已上架' : '已下架' }}</FaTag>
      </template>
      <template #cell-updatedAt="{ row }">{{ formatTime(row.original.updatedAt) }}</template>
      <template #cell-operation="{ row }">
        <div class="flex-center gap-2">
          <FaButton size="sm" variant="outline" @click="openEdit(row.original)">编辑</FaButton>
          <FaButton size="sm" variant="outline" @click="model.toggleItem(row.original)">
            {{ row.original.enabled ? '下架' : '上架' }}
          </FaButton>
          <FaButton size="sm" variant="destructive" @click="model.confirmDeleteItem(row.original)">删除</FaButton>
        </div>
      </template>
    </FaResponsiveTable>

    <FaPagination
      v-model:page="model.adminItemPager.page"
      v-model:size="model.adminItemPager.size"
      :total="model.adminItemPager.total"
      class="mt-3"
      @page-change="model.loadAdminItems()"
      @size-change="model.loadAdminItems(true)"
    />

    <FaModal
      v-model="modalVisible"
      :title="model.itemForm.id ? '编辑商品' : '新增商品'"
      class="max-w-[760px]"
      :show-confirm-button="false"
      :show-cancel-button="false"
    >
      <div class="pm-form">
        <div class="pm-form-grid two">
          <label><span>商品名称</span><FaInput v-model="model.itemForm.name" class="w-full" placeholder="例如：自定义称号兑换券" /></label>
          <label><span>排序（越小越前）</span><FaNumberField v-model="model.itemForm.sort" :min="0" class="w-full" /></label>
        </div>
        <label><span>商品说明</span><FaTextarea v-model="model.itemForm.description" class="w-full" placeholder="发放方式、使用范围等" /></label>
        <div class="pm-form-grid two">
          <label>
            <span>结算资产</span>
            <FaSelect v-model="model.itemForm.assetCode" :options="model.assetOptions" class="w-full" placeholder="选择用哪种资产结算" />
            <small class="pm-hint">来自钱包插件的资产列表；不同商品可以用不同资产结算。</small>
          </label>
          <div class="pm-cover-field">
            <span>商品封面（上传到对象存储）</span>
            <FaImageUpload
              :model-value="coverList"
              :max="1"
              :width="200"
              :height="120"
              :disabled="model.uploadingCover"
              :http-request="coverUpload"
              :after-upload="afterUpload"
            />
          </div>
        </div>
        <div class="pm-form-grid three">
          <label><span>所需积分</span><FaNumberField v-model="model.itemForm.pricePoints" :min="0" class="w-full" /></label>
          <label><span>库存（-1 不限量）</span><FaNumberField v-model="model.itemForm.stock" :min="-1" class="w-full" /></label>
          <label><span>每人限兑（0 不限）</span><FaNumberField v-model="model.itemForm.perUserLimit" :min="0" class="w-full" /></label>
        </div>
        <label class="pm-switch"><span>上架状态</span><FaSwitch v-model="model.itemForm.enabled" /></label>
        <p class="pm-hint">商品一旦有兑换记录就不能删除，只能下架；改价与改库存只影响之后的兑换，已产生的记录按当时的快照结算。</p>
        <div class="pm-actions pm-actions-end">
          <FaButton variant="outline" @click="modalVisible = false">取消</FaButton>
          <FaButton :loading="model.saving" @click="submit">保存</FaButton>
        </div>
      </div>
    </FaModal>
  </FaPageMain>
</template>
