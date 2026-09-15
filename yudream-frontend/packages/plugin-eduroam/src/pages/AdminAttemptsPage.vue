<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { EduroamAttempt } from '../types'
import {
  FaButton,
  FaIcon,
  FaInput,
  FaPageHeader,
  FaPageMain,
  FaPagination,
  FaResponsiveTable,
  FaSearchBar,
  FaSelect,
  FaTag,
} from '@yudream/components'
import { onMounted } from 'vue'
import { useEduroamAttempts } from '../composables/useEduroamAttempts'
import { formatTime } from '../types'

const props = defineProps<{ sdk: YuDreamPluginSdk }>()

const model = useEduroamAttempts(props.sdk)

const resultOptions = [
  { label: '全部结果', value: '' },
  { label: '认证通过', value: 'true' },
  { label: '认证失败', value: 'false' },
]

const columns: TableColumn<EduroamAttempt>[] = [
  { accessorKey: 'createdAt', header: '时间', width: 170 },
  { accessorKey: 'success', header: '结果', width: 100, align: 'center' },
  { accessorKey: 'identity', header: 'Eduroam 账号', width: 220 },
  { accessorKey: 'email', header: '本站邮箱', width: 220 },
  { accessorKey: 'reasonLabel', header: '原因', width: 160 },
  { accessorKey: 'clientIp', header: '来源 IP', width: 140 },
  { accessorKey: 'latencyMs', header: '耗时', width: 90, align: 'right' },
  { accessorKey: 'detail', header: '上游摘要', width: 320 },
]

onMounted(() => model.load(true))
</script>

<template>
  <FaPageHeader title="Eduroam 尝试审计" description="每一次 Eduroam 登录尝试（成功与失败）都留档，便于排查某位同学为什么一直登录不成功。" class="mb-0">
    <FaButton variant="outline" :loading="model.loading" @click="model.load()">
      <FaIcon name="i-ri:refresh-line" />
      刷新
    </FaButton>
  </FaPageHeader>

  <FaPageMain>
    <FaResponsiveTable
      v-loading="model.loading"
      row-key="id"
      table-root-class="max-w-full overflow-x-auto rounded-lg"
      table-class="min-w-[1560px]"
      border
      stripe
      column-visibility
      :columns="columns"
      :data="model.rows"
      empty-text="还没有核验尝试记录"
    >
      <template #toolbar>
        <FaSearchBar class="w-full">
          <div class="eduroam-filters">
            <FaInput
              v-model="model.filters.keyword"
              class="eduroam-search"
              placeholder="搜索账号 / 邮箱 / 来源 IP"
              clearable
              @keydown.enter="model.load(true)"
              @clear="model.load(true)"
            />
            <FaSelect
              v-model="model.filters.success"
              :options="resultOptions"
              class="eduroam-filter-select"
              @change="model.load(true)"
            />
            <div class="eduroam-actions">
              <FaButton variant="outline" @click="model.filters.keyword = ''; model.filters.success = ''; model.load(true)">重置</FaButton>
              <FaButton @click="model.load(true)">
                <FaIcon name="i-ri:search-line" />
                查询
              </FaButton>
            </div>
          </div>
        </FaSearchBar>
      </template>

      <template #cell-createdAt="{ row }">{{ formatTime(row.original.createdAt) }}</template>
      <template #cell-success="{ row }">
        <FaTag :variant="row.original.success ? 'default' : 'destructive'">
          {{ row.original.success ? '通过' : '失败' }}
        </FaTag>
      </template>
      <template #cell-identity="{ row }"><span class="break-all">{{ row.original.identity || '-' }}</span></template>
      <template #cell-email="{ row }"><span class="break-all">{{ row.original.email || '-' }}</span></template>
      <template #cell-reasonLabel="{ row }">
        <span class="break-all">{{ row.original.reasonLabel || '-' }}</span>
        <div v-if="row.original.message" class="eduroam-sub">{{ row.original.message }}</div>
      </template>
      <template #cell-clientIp="{ row }">{{ row.original.clientIp || '-' }}</template>
      <template #cell-latencyMs="{ row }">{{ row.original.latencyMs }} ms</template>
      <template #cell-detail="{ row }">
        <span class="eduroam-detail">{{ row.original.detail || '-' }}</span>
      </template>

      <template #card="{ row }">
        <div class="flex flex-col gap-2 text-sm">
          <div class="flex items-center justify-between gap-2">
            <span class="text-base font-semibold break-all">{{ row.identity || row.email || '未解析账号' }}</span>
            <FaTag :variant="row.success ? 'default' : 'destructive'">{{ row.success ? '通过' : '失败' }}</FaTag>
          </div>
          <div class="eduroam-sub">{{ formatTime(row.createdAt) }} · {{ row.clientIp || '-' }} · {{ row.latencyMs }} ms</div>
          <div v-if="row.reasonLabel" class="eduroam-sub">原因：{{ row.reasonLabel }}</div>
          <div v-if="row.message" class="eduroam-sub break-all">{{ row.message }}</div>
          <div v-if="row.detail" class="eduroam-detail">{{ row.detail }}</div>
        </div>
      </template>
    </FaResponsiveTable>

    <FaPagination
      v-model:page="model.pager.page"
      v-model:size="model.pager.size"
      :total="model.pager.total"
      :sizes="[20, 50, 100]"
      class="mt-3"
      @page-change="model.load()"
      @size-change="model.load(true)"
    />
  </FaPageMain>
</template>
