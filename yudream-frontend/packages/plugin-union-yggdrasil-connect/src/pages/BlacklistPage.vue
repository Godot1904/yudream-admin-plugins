<script setup lang="ts">
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import type { TableColumn } from '@yudream/components'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { FaButton, FaCard, FaPageHeader, FaPageMain, FaResponsiveTable, FaTag } from '@yudream/components'
import { computed, onMounted } from 'vue'
import { useYggcBlacklist } from '../composables/useYggcUnion'

const props = defineProps<{
  sdk: YuDreamPluginSdk
  route?: RouteLocationNormalizedLoaded
}>()

void props.route

const model = useYggcBlacklist(props.sdk)

/** 从上游响应（Laravel 分页结构）中提取记录列表。 */
function records(): Record<string, unknown>[] {
  const result = model.result
  if (!result) {
    return []
  }
  for (const key of ['data', 'records', 'list', 'blacklist', 'result']) {
    const value = result[key]
    if (Array.isArray(value)) {
      return value as Record<string, unknown>[]
    }
  }
  return []
}

function text(record: Record<string, unknown>, ...keys: string[]): string {
  for (const key of keys) {
    const value = record[key]
    if (value !== undefined && value !== null && value !== '') {
      return String(value)
    }
  }
  return '-'
}

/** 表格展示行：按上游字段（id/email/source/reason/created_at/valid_until）平铺。 */
interface BlacklistRow {
  key: string
  id: string
  email: string
  source: string
  reason: string
  createdAt: string
  validUntil: string
}

const tableRows = computed<BlacklistRow[]>(() => records().map((record) => {
  const id = text(record, 'id', 'ID')
  return {
    key: id !== '-' ? id : `${record.email ?? ''}-${Math.random()}`,
    id,
    email: text(record, 'email', 'name', 'player'),
    source: text(record, 'source', 'server', 'backend', 'from'),
    reason: text(record, 'reason', 'description', 'note'),
    createdAt: text(record, 'created_at'),
    validUntil: text(record, 'valid_until'),
  }
}))

const columns: TableColumn<BlacklistRow>[] = [
  { accessorKey: 'id', header: 'ID', width: 70 },
  { accessorKey: 'email', header: '邮箱', width: 220 },
  { accessorKey: 'source', header: '来源站点', width: 120 },
  { accessorKey: 'reason', header: '原因', width: 220 },
  { accessorKey: 'createdAt', header: '创建时间', width: 160 },
  { accessorKey: 'validUntil', header: '有效期至', width: 160 },
  { id: 'actions', header: '操作', width: 160 },
]

onMounted(model.query)
</script>

<template>
  <section class="yggc-home">
    <FaPageHeader title="联合黑名单" description="代理 Minecraft 高校联盟（MUA）主服务器黑名单查询、新增与失效管理。" />

    <FaPageMain>
      <div class="yggc-settings-grid">
        <FaCard title="查询黑名单" description="代理 MUA 主服务器 /blacklist/query" content-class="yggc-card-content">
          <div class="yggc-form-grid two">
            <label>
              <span>关键词</span>
              <input v-model="model.form.q" class="yggc-input" placeholder="按邮箱 / 角色名关键词查询">
            </label>
            <label>
              <span>页码</span>
              <input v-model="model.form.page" class="yggc-input" placeholder="页码，默认 1">
            </label>
          </div>
          <div class="yggc-actions">
            <FaButton :loading="model.querying" @click="model.query">查询</FaButton>
          </div>
          <p class="yggc-help">参数原样透传至 MUA 主服务器（q 为关键词、page 为页码），以主服务器接口为准。</p>
        </FaCard>

        <FaCard title="新增黑名单记录" description="POST /blacklist/restful" content-class="yggc-card-content">
          <div class="yggc-form-grid two">
            <label>
              <span>邮箱</span>
              <input v-model="model.create.email" class="yggc-input" placeholder="要拉黑的邮箱地址">
            </label>
            <label>
              <span>原因</span>
              <input v-model="model.create.reason" class="yggc-input" placeholder="拉黑原因（主服务器要求必填）">
            </label>
          </div>
          <div class="yggc-actions">
            <FaButton :loading="model.creating" @click="model.submitCreate">提交到主服务器</FaButton>
          </div>
          <p class="yggc-help">上游按邮箱维度拉黑（对齐 PHP 成员插件契约），邮箱与原因都是必填项；提交后自动刷新查询结果。</p>
        </FaCard>
      </div>

      <p v-if="model.error" class="yggc-consent-error">{{ model.error }}</p>

      <FaResponsiveTable
        v-if="tableRows.length"
        row-key="key"
        table-root-class="rounded-lg overflow-hidden"
        table-class="min-w-[880px]"
        border
        stripe
        :columns="columns"
        :data="tableRows"
      >
        <template #toolbar>
          <div class="yggc-table-toolbar">
            <div>
              <strong>查询结果</strong>
              <span>MUA 主服务器返回的黑名单记录。</span>
            </div>
            <FaTag variant="secondary">{{ tableRows.length }} 条记录</FaTag>
          </div>
        </template>
        <template #cell-email="{ row }">
          <span class="break-all">{{ row.original.email }}</span>
        </template>
        <template #cell-actions="{ row }">
          <div class="yggc-actions">
            <FaButton size="sm" variant="outline" :disabled="row.original.id === '-'" @click="model.invalidate(row.original.key)">使失效</FaButton>
            <FaButton size="sm" variant="destructive" :disabled="row.original.id === '-'" @click="model.remove(row.original.key)">删除</FaButton>
          </div>
        </template>
        <template #card="{ row }">
          <FaCard class="w-full">
            <div class="flex flex-col gap-2 text-sm">
              <div class="flex items-center justify-between gap-2">
                <span class="text-base font-semibold break-all">{{ row.email }}</span>
                <FaTag variant="secondary">#{{ row.id }}</FaTag>
              </div>
              <div class="flex gap-2">
                <span class="shrink-0 text-secondary-foreground/60">来源站点</span>
                <span class="break-all">{{ row.source }}</span>
              </div>
              <div class="flex gap-2">
                <span class="shrink-0 text-secondary-foreground/60">原因</span>
                <span class="break-all">{{ row.reason }}</span>
              </div>
              <div class="flex gap-2">
                <span class="shrink-0 text-secondary-foreground/60">创建时间</span>
                <span class="break-all">{{ row.createdAt }}</span>
              </div>
              <div class="flex gap-2">
                <span class="shrink-0 text-secondary-foreground/60">有效期至</span>
                <span class="break-all">{{ row.validUntil }}</span>
              </div>
              <div class="flex gap-2">
                <FaButton size="sm" variant="outline" :disabled="row.id === '-'" @click="model.invalidate(row.key)">使失效</FaButton>
                <FaButton size="sm" variant="destructive" :disabled="row.id === '-'" @click="model.remove(row.key)">删除</FaButton>
              </div>
            </div>
          </FaCard>
        </template>
      </FaResponsiveTable>
      <FaCard v-else-if="model.result" title="查询结果" description="MUA 主服务器原始响应">
        <pre class="yggc-diagnosis-body">{{ JSON.stringify(model.result, null, 2) }}</pre>
      </FaCard>
      <FaCard v-else title="查询结果" description="MUA 主服务器返回的黑名单记录。">
        <p class="yggc-help">暂无数据，先执行一次查询。</p>
      </FaCard>
    </FaPageMain>
  </section>
</template>
