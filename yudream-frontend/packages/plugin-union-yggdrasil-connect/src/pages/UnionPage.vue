<script setup lang="ts">
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import type { TableColumn } from '@yudream/components'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { YggcUnionBindEntry, YggcUnionProfileDetail, YggcUnionServer } from '../types'
import { FaButton, FaCard, FaIcon, FaPageHeader, FaPageMain, FaPagination, FaResponsiveTable, FaTag } from '@yudream/components'
import { computed, onMounted, ref, watch } from 'vue'
import { securityLevelBadge, useYggcMyUnion } from '../composables/useYggcMyUnion'

const props = defineProps<{
  sdk: YuDreamPluginSdk
  route?: RouteLocationNormalizedLoaded
}>()

void props.route

const model = useYggcMyUnion(props.sdk)

const security = computed(() => securityLevelBadge(model.overview?.securityLevel))

/** detail.bind 中排除自己（本站）后即为其他已绑定站点。 */
function otherBinds(bind?: YggcUnionBindEntry[]): YggcUnionBindEntry[] {
  return (bind ?? []).filter(entry => (entry.bind_status ?? 0) !== 0)
}

function backendCode(entry: { backend_scopes?: { self?: string } }): string {
  return entry.backend_scopes?.self ?? '-'
}

/** 角色跨站绑定表行。 */
interface BindRow {
  key: string
  name: string
  code: string
  backend: string
  mappedUuid: string
  originUuid: string
  remark: string
  remapTarget: string
  self: boolean
}

function bindRows(profile: { uuid: string, name: string, detail: YggcUnionProfileDetail }): BindRow[] {
  const detail = profile.detail
  const rows: BindRow[] = [{
    key: 'self',
    name: detail.bind_mapped_name ?? detail.name ?? profile.name,
    code: backendCode(detail),
    backend: detail.backend ?? '-',
    mappedUuid: detail.mapped_uuid ?? profile.uuid,
    originUuid: detail.uuid ?? profile.uuid,
    remark: '本站角色',
    remapTarget: detail.mapped_uuid ?? profile.uuid,
    self: true,
  }]
  for (const entry of otherBinds(detail.bind)) {
    rows.push({
      key: String(entry.internal_id ?? entry.uuid ?? entry.backend ?? Math.random()),
      name: entry.bind_mapped_name ?? '-',
      code: backendCode(entry),
      backend: entry.backend ?? '-',
      mappedUuid: entry.uuid ?? '-',
      originUuid: entry.mapped_uuid ?? '-',
      remark: '已绑定站点',
      remapTarget: entry.mapped_uuid ?? entry.uuid ?? '',
      self: false,
    })
  }
  return rows
}

const bindColumns: TableColumn<BindRow>[] = [
  { accessorKey: 'name', header: '联邦名称', width: 160 },
  { accessorKey: 'code', header: '站点代码', width: 110 },
  { accessorKey: 'backend', header: '站点', width: 220 },
  { id: 'uuids', header: 'UUID', width: 260 },
  { accessorKey: 'remark', header: '备注', width: 100 },
  { id: 'actions', header: '操作', width: 120 },
]

/** 同名角色（未绑定）表行。 */
interface DupRow {
  key: string
  name: string
  code: string
  backend: string
}

function dupRows(profile: { duplicateNames: YggcUnionBindEntry[] }): DupRow[] {
  return profile.duplicateNames.map(dup => ({
    key: String(dup.internal_id ?? dup.backend ?? Math.random()),
    name: dup.bind_mapped_name ?? '-',
    code: backendCode(dup),
    backend: dup.backend ?? '-',
  }))
}

const dupColumns: TableColumn<DupRow>[] = [
  { accessorKey: 'name', header: '联邦名称', width: 160 },
  { accessorKey: 'code', header: '站点代码', width: 110 },
  { accessorKey: 'backend', header: '站点', width: 220 },
]

/** MUA 皮肤站列表（前端分页）。 */
interface ServerRow {
  key: string
  code: string
  bs_root: string
}

const serverRows = computed<ServerRow[]>(() => {
  const servers: YggcUnionServer[] = model.overview?.serverList ?? []
  return servers.map(server => ({
    key: server.bs_root || server.code || `${server.code}-${Math.random()}`,
    code: server.code || '-',
    bs_root: server.bs_root || '',
  }))
})

const serverColumns: TableColumn<ServerRow>[] = [
  { accessorKey: 'code', header: '站点代码', width: 160 },
  { accessorKey: 'bs_root', header: '站点地址', width: 420 },
]

const page = ref(1)
const pageSize = ref(10)
const total = computed(() => serverRows.value.length)
const pagedRows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return serverRows.value.slice(start, start + pageSize.value)
})
watch(total, (newTotal) => {
  const maxPage = Math.max(1, Math.ceil(newTotal / pageSize.value))
  if (page.value > maxPage) {
    page.value = maxPage
  }
})

onMounted(model.load)
</script>

<template>
  <section class="yggc-home">
    <FaPageHeader title="跨站角色" description="Minecraft 高校联盟（MUA）跨站绑定、UUID 重映射与皮肤站列表。">
      <FaTag :variant="security.variant">{{ security.text }}</FaTag>
      <FaButton variant="outline" :loading="model.loading" @click="model.load">
        <FaIcon name="i-ri:refresh-line" />
        刷新
      </FaButton>
    </FaPageHeader>

    <FaPageMain>
      <p class="yggc-help">
        安全等级 SL3 表示本站满足 MUA Union 全部安全要求（私钥由主服务器分发、成员回调带主机签名校验）。
        绑定后角色 UUID 会同步到所有用户中心；未使用 MUA 认证的服务器中与旧 UUID 关联的玩家数据可能失效。
      </p>

      <p v-if="model.error" class="yggc-consent-error">{{ model.error }}</p>

      <section v-for="profile in model.overview?.profiles ?? []" :key="profile.uuid" class="yggc-panel">
        <div class="yggc-table-toolbar">
          <div>
            <strong>角色：{{ profile.name }}</strong>
            <span class="yggc-code">{{ profile.uuid }}</span>
          </div>
        </div>

        <div v-if="Object.keys(profile.detail ?? {}).length" class="yggc-form">
          <FaResponsiveTable
            row-key="key"
            table-root-class="rounded-lg overflow-hidden"
            table-class="min-w-[860px]"
            border
            stripe
            :columns="bindColumns"
            :data="bindRows(profile)"
          >
            <template #cell-code="{ row }">
              <span>{{ row.original.code }}</span>
            </template>
            <template #cell-backend="{ row }">
              <code class="yggc-code">{{ row.original.backend }}</code>
            </template>
            <template #cell-uuids="{ row }">
              <div class="flex flex-col gap-1 text-xs">
                <div>映射：<code>{{ row.original.mappedUuid }}</code></div>
                <div>原始：<code>{{ row.original.originUuid }}</code></div>
              </div>
            </template>
            <template #cell-actions="{ row }">
              <FaButton
                size="sm" variant="destructive"
                @click="model.remapUuid(profile.uuid, row.original.remapTarget)"
              >
                重映射 UUID
              </FaButton>
            </template>
            <template #card="{ row }">
              <FaCard class="w-full">
                <div class="flex flex-col gap-2 text-sm">
                  <div class="flex items-center justify-between gap-2">
                    <span class="text-base font-semibold break-all">{{ row.name }}</span>
                    <FaTag :variant="row.self ? 'default' : 'secondary'">{{ row.remark }}</FaTag>
                  </div>
                  <div class="flex gap-2">
                    <span class="shrink-0 text-secondary-foreground/60">站点代码</span>
                    <span class="break-all">{{ row.code }}</span>
                  </div>
                  <div class="flex gap-2">
                    <span class="shrink-0 text-secondary-foreground/60">站点</span>
                    <code class="break-all">{{ row.backend }}</code>
                  </div>
                  <div class="flex flex-col gap-1">
                    <div class="flex gap-2">
                      <span class="shrink-0 text-secondary-foreground/60">映射 UUID</span>
                      <code class="break-all text-xs">{{ row.mappedUuid }}</code>
                    </div>
                    <div class="flex gap-2">
                      <span class="shrink-0 text-secondary-foreground/60">原始 UUID</span>
                      <code class="break-all text-xs">{{ row.originUuid }}</code>
                    </div>
                  </div>
                  <FaButton size="sm" variant="destructive" @click="model.remapUuid(profile.uuid, row.remapTarget)">
                    重映射 UUID
                  </FaButton>
                </div>
              </FaCard>
            </template>
          </FaResponsiveTable>

          <div v-if="profile.detail.bind_status === undefined || profile.detail.bind_status > 0" class="yggc-form">
            <label>
              <span>绑定令牌（粘贴目标站生成的令牌）</span>
              <div class="yggc-device-input">
                <input v-model="model.tokenInputs[profile.uuid]" class="yggc-input" placeholder="目标站「获取绑定令牌」生成的令牌">
                <FaButton size="sm" @click="model.bindTo(profile.uuid)">绑定到本站</FaButton>
              </div>
            </label>
            <div class="yggc-actions">
              <FaButton size="sm" variant="outline" @click="model.fetchToken(profile.uuid)">获取本站绑定令牌</FaButton>
            </div>
            <div v-if="model.tokens[profile.uuid]" class="yggc-diagnosis">
              <p class="yggc-help">把下面的令牌粘贴到目标站点的跨站绑定输入框：</p>
              <pre class="yggc-diagnosis-body">{{ model.tokens[profile.uuid] }}</pre>
            </div>
          </div>
          <div v-else class="yggc-actions">
            <FaButton size="sm" variant="destructive" @click="model.unbind(profile.uuid)">解除跨站绑定</FaButton>
          </div>

          <div v-if="profile.duplicateNames.length">
            <p class="yggc-help">以下站点存在同名角色（未绑定）：</p>
            <FaResponsiveTable
              row-key="key"
              table-root-class="rounded-lg overflow-hidden"
              table-class="min-w-[520px]"
              border
              stripe
              :columns="dupColumns"
              :data="dupRows(profile)"
            >
              <template #cell-backend="{ row }">
                <code class="yggc-code">{{ row.original.backend }}</code>
              </template>
            </FaResponsiveTable>
          </div>
        </div>
        <div v-else class="yggc-form">
          <p class="yggc-help">尚未在 MUA 主服务器查询到该角色的跨站数据（可能未同步或上游不可达）。</p>
        </div>
      </section>

      <section v-if="model.overview && !model.overview.profiles?.length" class="yggc-panel">
        <p class="yggc-help">你在本站还没有角色，先到皮肤库创建角色后再使用跨站绑定。</p>
      </section>

      <section v-if="serverRows.length" class="yggc-panel">
        <FaResponsiveTable
          row-key="key"
          table-root-class="rounded-lg overflow-hidden"
          table-class="min-w-[560px]"
          border
          stripe
          :columns="serverColumns"
          :data="pagedRows"
        >
          <template #toolbar>
            <div class="yggc-table-toolbar">
              <div>
                <strong>MUA 皮肤站列表</strong>
                <span>MUA 全部成员站点。</span>
              </div>
              <FaTag variant="secondary">{{ total }} 个站点</FaTag>
            </div>
          </template>
          <template #cell-code="{ row }">
            <code>{{ row.original.code }}</code>
          </template>
          <template #cell-bs_root="{ row }">
            <a v-if="row.original.bs_root" :href="row.original.bs_root" target="_blank" rel="noopener">
              <code>{{ row.original.bs_root }}</code>
            </a>
            <span v-else>-</span>
          </template>
          <template #card="{ row }">
            <FaCard class="w-full">
              <div class="flex flex-col gap-2 text-sm">
                <div class="flex items-center justify-between gap-2">
                  <code class="text-sm font-semibold break-all">{{ row.code }}</code>
                </div>
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">站点地址</span>
                  <a v-if="row.bs_root" :href="row.bs_root" target="_blank" rel="noopener" class="break-all">{{ row.bs_root }}</a>
                  <span v-else>-</span>
                </div>
              </div>
            </FaCard>
          </template>
        </FaResponsiveTable>
        <FaPagination
          v-model:page="page"
          v-model:size="pageSize"
          :total="total"
          :sizes="[10, 20, 30, 50]"
          layout="total, sizes, ->, pager"
          class="yggc-pagination"
        />
      </section>
    </FaPageMain>
  </section>
</template>
