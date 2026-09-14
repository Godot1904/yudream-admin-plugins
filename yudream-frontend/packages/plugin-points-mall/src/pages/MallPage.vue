<script setup lang="ts">
import type { PointsMallModel } from '../composables/usePointsMall'
import { FaButton, FaCard, FaIcon, FaInput, FaModal, FaNumberField, FaPageHeader, FaPageMain, FaPagination, FaTag, FaTextarea } from '@yudream/components'
import { computed } from 'vue'
import { assetLabel, hasAsset, num, perUserLimitLabel, stockLabel } from '../types'

const props = defineProps<{ model: PointsMallModel }>()

const redeemVisible = computed({
  get: () => Boolean(props.model.redeemForm.itemId),
  set: (value: boolean) => {
    if (!value) {
      props.model.redeemForm.itemId = ''
    }
  },
})

const target = computed(() => props.model.items.find(item => item.id === props.model.redeemForm.itemId) || null)
const quantity = computed(() => Math.max(1, num(props.model.redeemForm.quantity)))
const totalPoints = computed(() => num(target.value?.pricePoints) * quantity.value)
const unit = computed(() => (target.value ? assetLabel(target.value) : '积分'))
const balance = computed(() => (target.value ? props.model.balanceOf(target.value.assetCode) : 0))
const enough = computed(() => totalPoints.value <= balance.value)

/** 已选好结算资产、还在架、商城也开着，才给兑换入口。 */
function redeemable(item: { assetCode: string, available: boolean }): boolean {
  return Boolean(item.available && hasAsset(item) && props.model.overview?.enabled)
}

function stateLabel(item: { assetCode: string, available: boolean, enabled: boolean }): string {
  if (!hasAsset(item)) {
    return '未设置资产'
  }
  if (item.available) {
    return '可兑换'
  }
  return item.enabled ? '已兑完' : '已下架'
}

function stateVariant(item: { assetCode: string, available: boolean }): 'secondary' | 'destructive' {
  return item.available && hasAsset(item) ? 'secondary' : 'destructive'
}
</script>

<template>
  <FaPageHeader title="积分商城" class="mb-0">
    <FaButton variant="outline" :loading="model.loading" @click="model.loadOverview(); model.loadItems()">
      <FaIcon name="i-ri:refresh-line" />
      刷新
    </FaButton>
  </FaPageHeader>

  <FaPageMain>
    <div class="pm-metrics">
      <FaCard v-for="asset in model.overview?.assets ?? []" :key="asset.code">
        <span>我的{{ asset.name || asset.code }}</span>
        <strong>{{ asset.balance }}</strong>
        <small>资产代码 {{ asset.code }}</small>
      </FaCard>
      <FaCard>
        <span>在架商品</span>
        <strong>{{ model.overview?.itemCount ?? '-' }}</strong>
        <small>可兑换的条目数</small>
      </FaCard>
      <FaCard>
        <span>待发放</span>
        <strong>{{ model.overview?.pendingCount ?? '-' }}</strong>
        <small>管理员还没交付</small>
      </FaCard>
      <FaCard>
        <span>待确认收货</span>
        <strong>{{ model.overview?.deliveredCount ?? '-' }}</strong>
        <small>已发放，等你确认</small>
      </FaCard>
      <FaCard>
        <span>累计兑换</span>
        <strong>{{ model.overview?.totalCount ?? '-' }}</strong>
        <small>含已完成与已取消</small>
      </FaCard>
    </div>

    <FaCard v-if="model.overview && !model.overview.enabled" class="mt-4">
      <strong>商城当前未开放</strong>
      <p class="pm-hint">管理员关闭了积分商城，暂时不能兑换；已有兑换的发放与确认不受影响。</p>
    </FaCard>

    <FaCard v-else-if="model.overview?.notice" class="mt-4">
      <strong>兑换须知</strong>
      <p class="pm-hint">{{ model.overview.notice }}</p>
    </FaCard>

    <section class="pm-panel mt-4">
      <div class="pm-toolbar">
        <FaInput
          v-model="model.keyword"
          class="pm-search"
          placeholder="搜索商品名称或说明"
          clearable
          @keydown.enter="model.loadItems(true)"
          @clear="model.loadItems(true)"
        />
        <div class="pm-actions">
          <FaButton variant="outline" @click="model.keyword = ''; model.loadItems(true)">重置</FaButton>
          <FaButton @click="model.loadItems(true)">
            <FaIcon name="i-ri:search-line" />
            查询
          </FaButton>
        </div>
      </div>

      <div v-if="model.loading && !model.items.length" class="pm-empty">正在加载商品…</div>
      <div v-else-if="!model.items.length" class="pm-empty">暂无商品，等管理员上架后再来看看。</div>
      <div v-else class="pm-grid">
        <FaCard v-for="item in model.items" :key="item.id" class="pm-card">
          <div v-if="item.imageUrl" class="pm-cover">
            <img :src="model.thumbUrl(item.imageUrl)" :alt="item.name" loading="lazy">
          </div>
          <div class="pm-card-head">
            <strong>{{ item.name }}</strong>
            <FaTag :variant="stateVariant(item)">
              {{ stateLabel(item) }}
            </FaTag>
          </div>
          <p class="pm-desc">{{ item.description || '暂无说明' }}</p>
          <div class="pm-meta">
            <span class="pm-price">{{ item.pricePoints }} {{ hasAsset(item) ? assetLabel(item) : '（未设置结算资产）' }}</span>
            <span>{{ stockLabel(item) }}</span>
            <span>{{ perUserLimitLabel(item) }}</span>
          </div>
          <div class="pm-card-actions">
            <FaButton
              :disabled="!redeemable(item)"
              @click="model.openRedeem(item)"
            >
              <FaIcon name="i-ri:gift-line" />
              兑换
            </FaButton>
          </div>
        </FaCard>
      </div>

      <FaPagination
        v-model:page="model.itemPager.page"
        v-model:size="model.itemPager.size"
        :total="model.itemPager.total"
        class="mt-3"
        @page-change="model.loadItems()"
        @size-change="model.loadItems(true)"
      />
    </section>

    <FaModal
      v-model="redeemVisible"
      title="兑换商品"
      class="max-w-[560px]"
      :show-confirm-button="false"
      :show-cancel-button="false"
    >
      <div v-if="target" class="pm-form">
        <div v-if="target.imageUrl" class="pm-cover pm-cover-preview">
          <img :src="model.thumbUrl(target.imageUrl)" :alt="target.name">
        </div>
        <div class="pm-form-row">
          <div><span>商品</span><strong>{{ target.name }}</strong></div>
          <div><span>单价</span><strong>{{ target.pricePoints }} {{ unit }}</strong></div>
        </div>
        <label>
          <span>兑换数量</span>
          <FaNumberField v-model="model.redeemForm.quantity" :min="1" :max="999" class="w-full" />
        </label>
        <label>
          <span>备注（可选）</span>
          <FaTextarea v-model="model.redeemForm.remark" class="w-full" placeholder="例如：希望发放到哪个角色" />
        </label>
        <div class="pm-summary">
          <span>需要 {{ totalPoints }} {{ unit }}</span>
          <span>当前 {{ balance }} {{ unit }}</span>
        </div>
        <p v-if="!enough" class="pm-error">{{ unit }}不足，还差 {{ totalPoints - balance }}</p>
        <div class="pm-actions">
          <FaButton variant="outline" @click="model.redeemForm.itemId = ''">取消</FaButton>
          <FaButton :loading="model.saving" :disabled="!enough" @click="model.redeem()">确认兑换</FaButton>
        </div>
      </div>
    </FaModal>
  </FaPageMain>
</template>
