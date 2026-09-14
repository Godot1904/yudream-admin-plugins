<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { PointsMallModel } from '../composables/usePointsMall'
import type { MallRedemption } from '../types'
import { FaButton, FaIcon, FaInput, FaModal, FaPageHeader, FaPageMain, FaPagination, FaResponsiveTable, FaTag, useFaImagePreview } from '@yudream/components'
import { ref } from 'vue'
import { formatTime, statusVariant } from '../types'

const props = defineProps<{ model: PointsMallModel }>()

const preview = useFaImagePreview()
const cancelVisible = ref(false)
const cancelTarget = ref<MallRedemption | null>(null)
const cancelReason = ref('')

const columns: TableColumn<MallRedemption>[] = [
  { accessorKey: 'itemName', header: '商品', width: 200 },
  { accessorKey: 'quantity', header: '数量', width: 90, align: 'center' },
  { accessorKey: 'totalPoints', header: '消耗积分', width: 120, align: 'right' },
  { accessorKey: 'status', header: '状态', width: 120, align: 'center' },
  { accessorKey: 'proofs', header: '发放凭证', width: 160 },
  { accessorKey: 'remark', header: '备注', width: 200 },
  { accessorKey: 'createdAt', header: '兑换时间', width: 180 },
  { id: 'operation', header: '操作', width: 130, align: 'center', fixed: 'right' },
]

function openCancel(redemption: MallRedemption) {
  cancelTarget.value = redemption
  cancelReason.value = ''
  cancelVisible.value = true
}

async function submitCancel() {
  if (!cancelTarget.value) {
    return
  }
  await props.model.cancelMyRedemption(cancelTarget.value, cancelReason.value)
  cancelVisible.value = false
  cancelTarget.value = null
}

function previewProof(url: string) {
  preview.open([props.model.imageUrl(url)])
}
</script>

<template>
  <FaPageHeader title="我的兑换" class="mb-0">
    <FaButton variant="outline" :loading="model.loading" @click="model.loadMyRedemptions()">
      <FaIcon name="i-ri:refresh-line" />
      刷新
    </FaButton>
  </FaPageHeader>

  <FaPageMain>
    <p class="pm-hint">
      这里只显示你自己的兑换。尚未发放的可以自行取消（积分按原单退回）；管理员发放后请查看凭证并点「确认收到」。
    </p>

    <FaResponsiveTable
      v-loading="model.loading"
      class="mt-4"
      row-key="id"
      table-root-class="max-w-full overflow-x-auto rounded-lg"
      table-class="min-w-[1120px]"
      border
      stripe
      column-visibility
      :columns="columns"
      :data="model.myRedemptions"
      empty-text="还没有兑换记录"
    >
      <template #cell-itemName="{ row }">
        <strong>{{ row.original.itemName }}</strong>
        <div class="pm-table-sub">{{ row.original.assetCode }} · 单价 {{ row.original.unitPoints }}</div>
      </template>
      <template #cell-status="{ row }">
        <FaTag :variant="statusVariant(row.original.status)">{{ row.original.statusLabel }}</FaTag>
        <div v-if="row.original.confirmedAt" class="pm-table-sub">确认于 {{ formatTime(row.original.confirmedAt) }}</div>
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
          <FaButton
            v-if="row.original.status === 'DELIVERED'"
            size="sm"
            variant="outline"
            :loading="model.saving"
            @click="model.confirmReceipt(row.original)"
          >
            确认收到
          </FaButton>
          <FaButton
            v-else-if="row.original.status === 'PENDING'"
            size="sm"
            variant="destructive"
            @click="openCancel(row.original)"
          >
            取消兑换
          </FaButton>
          <span v-else class="pm-muted">—</span>
        </div>
      </template>
      <template #card="{ row }">
        <div class="pm-card-list">
          <div class="pm-card-head">
            <strong>{{ row.itemName }}</strong>
            <FaTag :variant="statusVariant(row.status)">{{ row.statusLabel }}</FaTag>
          </div>
          <div class="pm-meta">
            <span>{{ row.quantity }} 件</span>
            <span>消耗 {{ row.totalPoints }} {{ row.assetCode }}</span>
            <span>{{ formatTime(row.createdAt) }}</span>
          </div>
          <p v-if="row.remark" class="pm-hint">备注：{{ row.remark }}</p>
          <p v-if="row.deliveryNote" class="pm-hint">发放备注：{{ row.deliveryNote }}</p>
          <p v-if="row.cancelNote" class="pm-hint">取消原因：{{ row.cancelNote }}</p>
          <div v-if="row.proofs.length" class="pm-proofs">
            <template v-for="proof in row.proofs" :key="proof.url">
              <button v-if="proof.image" type="button" class="pm-proof-thumb" @click="previewProof(proof.url)">
                <img :src="model.thumbUrl(proof.url)" :alt="proof.filename || '发放凭证'">
              </button>
              <a v-else class="pm-hint" :href="model.imageUrl(proof.url)" target="_blank" rel="noreferrer">{{ proof.filename || '下载凭证' }}</a>
            </template>
          </div>
          <div class="pm-actions">
            <FaButton
              v-if="row.status === 'DELIVERED'"
              size="sm"
              variant="outline"
              :loading="model.saving"
              @click="model.confirmReceipt(row)"
            >
              确认收到
            </FaButton>
            <FaButton v-else-if="row.status === 'PENDING'" size="sm" variant="destructive" @click="openCancel(row)">取消兑换</FaButton>
          </div>
        </div>
      </template>
    </FaResponsiveTable>

    <FaPagination
      v-model:page="model.redemptionPager.page"
      v-model:size="model.redemptionPager.size"
      :total="model.redemptionPager.total"
      class="mt-3"
      @page-change="model.loadMyRedemptions()"
      @size-change="model.loadMyRedemptions(true)"
    />

    <FaModal v-model="cancelVisible" title="取消兑换" class="max-w-[520px]" :show-confirm-button="false" :show-cancel-button="false">
      <div class="pm-form">
        <p class="pm-hint">
          取消「{{ cancelTarget?.itemName }}」这次兑换后，{{ cancelTarget?.totalPoints }} 积分会退回原资产，库存一并归还。
        </p>
        <label>
          <span>取消原因</span>
          <FaInput v-model="cancelReason" placeholder="例如：不想要了" />
        </label>
        <div class="pm-actions">
          <FaButton variant="outline" @click="cancelVisible = false">再想想</FaButton>
          <FaButton variant="destructive" :loading="model.saving" @click="submitCancel">确认取消</FaButton>
        </div>
      </div>
    </FaModal>
  </FaPageMain>
</template>
