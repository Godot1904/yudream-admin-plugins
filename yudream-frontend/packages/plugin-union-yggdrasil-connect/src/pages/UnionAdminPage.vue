<script setup lang="ts">
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import type { TableColumn } from '@yudream/components'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { FaButton, FaCard, FaIcon, FaPageHeader, FaPageMain, FaPagination, FaResponsiveTable, FaTag } from '@yudream/components'
import { computed, onMounted, ref, watch } from 'vue'
import { useYggcUnion } from '../composables/useYggcUnion'

const props = defineProps<{
  sdk: YuDreamPluginSdk
  route?: RouteLocationNormalizedLoaded
}>()

void props.route

const model = useYggcUnion(props.sdk)

function formatTime(value?: number) {
  return value ? new Date(value).toLocaleString() : '-'
}

function upstreamText(key: string): string {
  const response = model.status?.upstream?.response as Record<string, unknown> | undefined
  const value = response?.[key]
  return value === undefined || value === null ? '-' : String(value)
}

/** MUA 皮肤站列表（合并站点名称后供表格展示的行）。 */
interface ServerRow {
  key: string
  code: string
  bs_root: string
  name: string
}

const serverRows = computed<ServerRow[]>(() => {
  const servers = model.status?.serverList?.servers ?? []
  return servers.map(server => ({
    key: server.bs_root || server.code || `${server.code}-${Math.random()}`,
    code: server.code || '-',
    bs_root: server.bs_root || '',
    name: (server.bs_root && model.serverNames[server.bs_root]) || '…',
  }))
})

const serverColumns: TableColumn<ServerRow>[] = [
  { accessorKey: 'code', header: '站点代码', width: 140 },
  { accessorKey: 'bs_root', header: '站点地址', width: 380 },
  { accessorKey: 'name', header: '站点名称', width: 220 },
]

/** 前端分页。 */
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
    <FaPageHeader title="MUA 状态" description="Minecraft 高校联盟（MUA）主服务器状态、私钥同步与皮肤站列表。">
      <FaButton variant="outline" :loading="model.loading" @click="model.load">
        <FaIcon name="i-ri:refresh-line" />
        刷新
      </FaButton>
      <FaButton :loading="model.syncingKey" @click="model.syncPrivateKey">从上游同步签名私钥</FaButton>
      <FaButton variant="outline" :loading="model.syncingList" @click="model.syncServerList">同步皮肤站列表</FaButton>
      <FaButton variant="outline" :loading="model.syncingProfiles" @click="model.syncProfiles">全量同步角色</FaButton>
      <FaButton variant="outline" :loading="model.reconciling" @click="model.reconcileProfiles">立即对账角色</FaButton>
    </FaPageHeader>

    <FaPageMain>
      <div class="yggc-union-status-grid">
        <FaCard title="联邦状态" description="本地数据版本与上游连通性" content-class="yggc-card-content">
          <div class="yggc-status-line">
            <span>MUA API Root</span>
            <code class="yggc-code">{{ model.status?.apiRoot || '-' }}</code>
          </div>
          <div class="yggc-status-line">
            <span>Member Key</span>
            <FaTag :variant="model.status?.memberKeyConfigured ? 'default' : 'destructive'">
              {{ model.status?.memberKeyConfigured ? '已配置' : '未配置' }}
            </FaTag>
          </div>
          <div class="yggc-status-line">
            <span>上游连通性</span>
            <FaTag :variant="model.status?.upstream?.reachable ? 'default' : 'destructive'">
              {{ model.status?.upstream?.reachable ? `可访问（${model.status?.upstream?.latencyMs ?? '-'} ms）` : '不可访问' }}
            </FaTag>
          </div>
          <div class="yggc-status-line">
            <span>签名私钥来源</span>
            <FaTag :variant="model.status?.privateKeySynced ? 'default' : 'secondary'">
              {{ model.status?.privateKeySynced ? `MUA 主服务器（版本 ${model.status?.privateKey?.version ?? '-'}）` : '本地生成' }}
            </FaTag>
          </div>
          <div class="yggc-status-line">
            <span>私钥同步时间</span>
            <strong>{{ formatTime(model.status?.privateKey?.syncedAt) }}</strong>
          </div>
          <div class="yggc-status-line">
            <span>皮肤站列表</span>
            <strong>{{ model.status?.serverCount ?? 0 }} 个站点（版本 {{ model.status?.serverList?.version ?? '-' }}，{{ formatTime(model.status?.serverList?.syncedAt) }}）</strong>
          </div>
        </FaCard>

        <FaCard v-if="model.status?.upstream?.response" title="上游公告" description="GET {apiRoot}" content-class="yggc-card-content">
          <div class="yggc-status-line"><span>yggdrasilApiVersion</span><code>{{ upstreamText('yggdrasilApiVersion') }}</code></div>
          <div class="yggc-status-line"><span>serverListVersion</span><code>{{ upstreamText('serverListVersion') }}</code></div>
          <div class="yggc-status-line"><span>privateKeyVersion</span><code>{{ upstreamText('privateKeyVersion') }}</code></div>
          <div class="yggc-status-line"><span>enabledFeatures</span><code>{{ upstreamText('enabledFeatures') }}</code></div>
          <p class="yggc-help">
            用户信息签名密钥由 MUA 主服务器生成并通过 GET /privatekey 分发，本站不再本地生成材质签名私钥。
            主服务器会主动调用本站成员回调（/union/member/*）推送私钥轮换、列表更新与 UUID 重映射。
          </p>
        </FaCard>
      </div>

      <div class="yggc-union-section-grid">
        <FaResponsiveTable
          v-if="serverRows.length"
          row-key="key"
          table-root-class="rounded-lg overflow-hidden"
          table-class="min-w-[680px]"
          border
          stripe
          :columns="serverColumns"
          :data="pagedRows"
        >
          <template #toolbar>
            <div class="yggc-table-toolbar">
              <div>
                <strong>MUA 皮肤站列表</strong>
                <span>MUA 主服务器分发的高校联盟成员站点。</span>
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
                  <span class="text-base font-semibold break-all">{{ row.name }}</span>
                  <code class="text-xs">{{ row.code }}</code>
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
        <FaCard v-else title="MUA 皮肤站列表" description="MUA 主服务器分发的高校联盟成员站点。">
          <p class="yggc-help">尚未同步皮肤站列表，点击上方「同步皮肤站列表」从 MUA 主服务器拉取。</p>
        </FaCard>
        <FaPagination
          v-if="total > 0"
          v-model:page="page"
          v-model:size="pageSize"
          :total="total"
          :sizes="[10, 20, 30, 50]"
          layout="total, sizes, ->, pager"
          class="yggc-pagination"
        />
      </div>

      <div class="yggc-union-section-grid">
        <FaCard v-if="model.status?.profileSync" title="角色同步" description="本站角色 → MUA 主服务器" content-class="yggc-card-content">
          <div class="yggc-status-line">
            <span>已推送角色</span>
            <strong>{{ model.status.profileSync.pushedCount ?? 0 }} 个</strong>
          </div>
          <div class="yggc-status-line">
            <span>定时对账</span>
            <FaTag :variant="model.status.profileSync.enabled ? 'default' : 'secondary'">
              {{ model.status.profileSync.enabled ? `每 ${model.status.profileSync.intervalMinutes} 分钟` : '已关闭' }}
            </FaTag>
          </div>
          <div class="yggc-status-line">
            <span>登录时补推</span>
            <FaTag :variant="model.status.profileSync.onLoginPush ? 'default' : 'secondary'">
              {{ model.status.profileSync.onLoginPush ? '已开启' : '已关闭' }}
            </FaTag>
          </div>
          <div class="yggc-status-line">
            <span>上次同步</span>
            <strong>{{ formatTime(model.status.profileSync.syncedAt) }}</strong>
          </div>
          <div v-if="model.status.profileSync.lastResult" class="yggc-status-line">
            <span>上次结果</span>
            <strong>
              新增 {{ model.status.profileSync.lastResult.added ?? 0 }} ·
              改名 {{ model.status.profileSync.lastResult.renamed ?? 0 }} ·
              删除 {{ model.status.profileSync.lastResult.removed ?? 0 }} ·
              失败 {{ model.status.profileSync.lastResult.failed ?? 0 }}
            </strong>
          </div>
          <p class="yggc-help">
            角色在皮肤站创建，插件收不到「创建」事件：开启「玩家登录时补推」后，玩家首次用启动器登录或进入服务器时会立刻补推他的新角色；
            定时对账作为兜底，只推送与上次成功推送有差异的条目（缺失、改名、删除）。
            两者都需要在「插件配置」里填好 MUA API Root 与 Member Key。
          </p>
          <p v-if="!model.status.profileSync.ready" class="yggc-help">当前未配置 Member Key，自动同步不会执行。</p>
          <p v-if="model.status.profileSync.lastError" class="yggc-help yggc-help--danger">
            最近失败：{{ model.status.profileSync.lastError }}
          </p>
          <div v-if="model.lastReconcileResult" class="yggc-status-line">
            <span>本次对账</span>
            <strong>{{ model.lastReconcileResult.message || '-' }}</strong>
          </div>
        </FaCard>

        <FaCard v-if="model.lastSyncResult" title="最近一次全量同步" description="POST /sync" content-class="yggc-card-content">
          <div class="yggc-status-line"><span>推送角色数</span><strong>{{ model.lastSyncResult.profileCount ?? '-' }}</strong></div>
          <p class="yggc-help">{{ model.lastSyncResult.message }}</p>
          <pre class="yggc-diagnosis-body">{{ JSON.stringify(model.lastSyncResult, null, 2) }}</pre>
        </FaCard>
      </div>

      <div class="yggc-union-section-grid">
        <FaCard title="安全等级" description="MUA 主服务器对本站后端的评级">
          <p class="yggc-help">
            安全等级由主服务器综合评估：SL3 = 站点私钥安全存储且通信全链路校验；SL1 = 存在安全短板；SL0 = 不安全。
            用户可在「跨站角色」页查看本站当前评级。
          </p>
        </FaCard>
      </div>
    </FaPageMain>
  </section>
</template>
