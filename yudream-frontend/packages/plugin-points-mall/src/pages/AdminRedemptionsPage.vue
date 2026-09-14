<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { PointsMallModel } from '../composables/usePointsMall'
import type { MallProofInput, MallRedemption } from '../types'
import { FaButton, FaIcon, FaImageUpload, FaInput, FaModal, FaPageHeader, FaPageMain, FaPagination, FaResponsiveTable, FaSearchBar, FaSelect, FaTag, FaTextarea, useFaImagePreview } from '@yudream/components'
import { computed, ref } from 'vue'
import { formatTime, statusVariant } from '../types'

const props = defineProps<{ model: PointsMallModel }>()

const preview = useFaImagePreview()

const deliverVisible = ref(false)
const deliverTarget = ref<MallRedemption | null>(null)
const deliverNote = ref('')
const proofList = ref<string[]>([])
// FaImageUpload 只回传地址，凭证的文件名/类型/大小在上传时另外记下来，提交时按地址取回。
const proofRecords = new Map<string, MallProofInput>()

const cancelVisible = ref(false)
const cancelTarget = ref<MallRedemption | null>(null)
const cancelReason = ref('')

/** 用户筛选的关键字：只用来刷新下面那个用户下拉的候选，筛选条件本身是选中的用户 ID。 */
const userKeyword = ref('')

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '待发放', value: 'PENDING' },
  { label: '待确认收货', value: 'DELIVERED' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已取消', value: 'CANCELLED' },
]

const itemFilterOptions = computed(() => [
  { label: '全部商品', value: '' },
  ...props.model.itemOptions.map(item => ({ label: item.name, value: item.id })),
])

const userFilterOptions = computed(() => [
  { label: '全部用户', value: '' },
  ...props.model.userOptions.map(user => ({
    label: user.nickname ? `${user.nickname}（${user.username}）` : user.username,
    value: user.id,
  })),
])

const columns: TableColumn<MallRedemption>[] = [
  { accessorKey: 'itemName', header: '商品', width: 180 },
  { accessorKey: 'userName', header: '兑换人', width: 160 },
  { accessorKey: 'quantity', header: '数量', width: 90, align: 'center' },
  { accessorKey: 'totalPoints', header: '消耗积分', width: 110, align: 'right' },
  { accessorKey: 'status', header: '状态', width: 120, align: 'center' },
  { accessorKey: 'proofs', header: '发放凭证', width: 150 },
  { accessorKey: 'remark', header: '备注', width: 190 },
  { accessorKey: 'createdAt', header: '兑换时间', width: 180 },
  { id: 'operation', header: '操作', width: 190, align: 'center', fixed: 'right' },
]

function searchUsers() {
  props.model.searchUsers(userKeyword.value)
}

function resetFilters() {
  userKeyword.value = ''
  props.model.redemptionFilters.userId = ''
  props.model.redemptionFilters.itemId = ''
  props.model.redemptionFilters.status = ''
  props.model.searchUsers('')
  props.model.loadAdminRedemptions(true)
}

function openDeliver(redemption: MallRedemption) {
  deliverTarget.value = redemption
  deliverNote.value = ''
  proofList.value = []
  proofRecords.clear()
  deliverVisible.value = true
}

const proofUpload = computed(() => async (options: { file: File }) => {
  const proof = await props.model.uploadProof(options.file)
  proofRecords.set(proof.url, proof)
  return proof.url
})

function afterUpload(response: unknown) {
  return typeof response === 'string' ? response : ''
}

async function submitDeliver() {
  if (!deliverTarget.value) {
    return
  }
  if (!proofList.value.length) {
    return
  }
  const proofs = proofList.value.map(url => proofRecords.get(url) ?? { url, filename: '', contentType: '', size: 0 })
  const delivered = await props.model.deliverRedemption(deliverTarget.value, deliverNote.value, proofs)
  if (delivered) {
    deliverVisible.value = false
    deliverTarget.value = null
  }
}

function openCancel(redemption: MallRedemption) {
  cancelTarget.value = redemption
  cancelReason.value = ''
  cancelVisible.value = true
}

async function submitCancel() {
  if (!cancelTarget.value) {
    return
  }
  await props.model.cancelRedemption(cancelTarget.value, cancelReason.value)
  cancelVisible.value = false
  cancelTarget.value = null
}

function previewProof(url: string) {
  preview.open([props.model.imageUrl(url)])
}
</script>

<template>
  <FaPageHeader title="兑换记录" class="mb-0">
    <FaButton variant="outline" :loading="model.loading" @click="model.loadAdminRedemptions()">
      <FaIcon name="i-ri:refresh-line" />
      刷新
    </FaButton>
  </FaPageHeader>

  <FaPageMain>
    <FaResponsiveTable
      v-loading="model.loading"
      row-key="id"
      table-root-class="max-w-full overflow-x-auto rounded-lg"
      table-class="min-w-[1380px]"
      border
      stripe
      column-visibility
      :columns="columns"
      :data="model.adminRedemptions"
      empty-text="还没有兑换记录"
    >
      <template #toolbar>
        <FaSearchBar class="w-full">
          <div class="pm-filters">
            <FaInput
              v-model="userKeyword"
              class="pm-search"
              placeholder="搜索用户（昵称 / 用户名）"
              clearable
              @keydown.enter="searchUsers"
              @clear="searchUsers"
            />
            <FaButton variant="outline" @click="searchUsers">
              <FaIcon name="i-ri:user-search-line" />
              搜索用户
            </FaButton>
            <FaSelect
              v-model="model.redemptionFilters.userId"
              :options="userFilterOptions"
              class="pm-filter-select"
              @change="model.loadAdminRedemptions(true)"
            />
            <FaSelect
              v-model="model.redemptionFilters.itemId"
              :options="itemFilterOptions"
              class="pm-filter-select"
              @change="model.loadAdminRedemptions(true)"
            />
            <FaSelect
              v-model="model.redemptionFilters.status"
              :options="statusOptions"
              class="pm-filter-select"
              @change="model.loadAdminRedemptions(true)"
            />
            <div class="pm-actions">
              <FaButton variant="outline" @click="resetFilters">重置</FaButton>
              <FaButton @click="model.loadAdminRedemptions(true)">
                <FaIcon name="i-ri:search-line" />
                查询
              </FaButton>
            </div>
          </div>
        </FaSearchBar>
      </template>
      <template #cell-itemName="{ row }">
        <strong>{{ row.original.itemName }}</strong>
        <div class="pm-table-sub">{{ row.original.assetCode }} · 单价 {{ row.original.unitPoints }} × {{ row.original.quantity }}</div>
      </template>
      <template #cell-userName="{ row }">
        <strong>{{ row.original.userName }}</strong>
        <div class="pm-table-sub">ID {{ row.original.userId }}</div>
      </template>
      <template #cell-status="{ row }">
        <FaTag :variant="statusVariant(row.original.status)">{{ row.original.statusLabel }}</FaTag>
        <div v-if="row.original.confirmedAt" class="pm-table-sub">用户已确认</div>
      </template>
      <template #cell-proofs="{ row }">
        <div v-if="row.original.proofs.length" class="pm-proofs">
          <template v-for="proof in row.original.proofs" :key="proof.url">
            <button v-if="proof.image" type="button" class="pm-proof-thumb" @click="previewProof(proof.url)">
              <img :src="model.thumbUrl(proof.url)" :alt="proof.filename || '发放凭证'">
            </button>
            <a v-else class="pm-hint" :href="model.imageUrl(proof.url)" target="_blank" rel="noreferrer">{{ proof.filename || '下载凭证' }}</a>
          </template>
        </div>
        <span v-else class="pm-muted">—</span>
      </template>
      <template #cell-remark="{ row }">
        <span>{{ row.original.remark || '-' }}</span>
        <div v-if="row.original.deliveryNote" class="pm-table-sub">发放备注：{{ row.original.deliveryNote }}</div>
        <div v-if="row.original.cancelNote" class="pm-table-sub">取消原因：{{ row.original.cancelNote }}</div>
      </template>
      <template #cell-createdAt="{ row }">{{ formatTime(row.original.createdAt) }}</template>
      <template #cell-operation="{ row }">
        <div class="flex-center gap-2">
          <template v-if="row.original.status === 'PENDING'">
            <FaButton size="sm" variant="outline" @click="openDeliver(row.original)">发放</FaButton>
            <FaButton size="sm" variant="destructive" @click="openCancel(row.original)">取消退款</FaButton>
          </template>
          <span v-else class="pm-muted">{{ row.original.status === 'DELIVERED' ? '等待用户确认' : '已处理' }}</span>
        </div>
      </template>
    </FaResponsiveTable>

    <FaPagination
      v-model:page="model.adminRedemptionPager.page"
      v-model:size="model.adminRedemptionPager.size"
      :total="model.adminRedemptionPager.total"
      class="mt-3"
      @page-change="model.loadAdminRedemptions()"
      @size-change="model.loadAdminRedemptions(true)"
    />

    <FaModal v-model="deliverVisible" title="发放兑换" class="max-w-[640px]" :show-confirm-button="false" :show-cancel-button="false">
      <div class="pm-form">
        <p class="pm-hint">
          确认已把「{{ deliverTarget?.itemName }}」× {{ deliverTarget?.quantity }} 交付给 {{ deliverTarget?.userName }}。
          发放必须上传凭证，用户看到凭证后才能确认收到；发放不涉及积分变动。
        </p>
        <div class="pm-cover-field">
          <span>发放凭证（图片，最多 6 张）</span>
          <FaImageUpload
            :model-value="proofList"
            :max="6"
            :width="150"
            :height="100"
            :disabled="model.saving"
            :http-request="proofUpload"
            :after-upload="afterUpload"
          />
          <small class="pm-hint">凭证会存到宿主对象存储（S3 桶），兑换记录里长期可见。</small>
        </div>
        <label>
          <span>发放备注（可选）</span>
          <FaTextarea v-model="deliverNote" class="w-full" placeholder="例如：已当面交付 / 已发放到角色 A" />
        </label>
        <p v-if="!proofList.length" class="pm-error">请至少上传一张凭证再发放。</p>
        <div class="pm-actions pm-actions-end">
          <FaButton variant="outline" @click="deliverVisible = false">取消</FaButton>
          <FaButton :loading="model.saving" :disabled="!proofList.length" @click="submitDeliver">确认发放</FaButton>
        </div>
      </div>
    </FaModal>

    <FaModal v-model="cancelVisible" title="取消并退款" class="max-w-[520px]" :show-confirm-button="false" :show-cancel-button="false">
      <div class="pm-form">
        <p class="pm-hint">
          取消这次兑换会把 {{ cancelTarget?.totalPoints }} {{ cancelTarget?.assetCode }} 退回给
          {{ cancelTarget?.userName }}，并归还库存。已发放的记录不能取消。
        </p>
        <label>
          <span>取消原因</span>
          <FaInput v-model="cancelReason" placeholder="例如：库存不足 / 用户申请" />
        </label>
        <div class="pm-actions pm-actions-end">
          <FaButton variant="outline" @click="cancelVisible = false">再想想</FaButton>
          <FaButton variant="destructive" :loading="model.saving" @click="submitCancel">确认取消并退款</FaButton>
        </div>
      </div>
    </FaModal>
  </FaPageMain>
</template>
