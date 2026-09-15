<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { EduroamAccount } from '../types'
import {
  FaAlert,
  FaButton,
  FaIcon,
  FaInput,
  FaModal,
  FaPageHeader,
  FaPageMain,
  FaPagination,
  FaResponsiveTable,
  FaSearchBar,
  FaSelect,
  FaTag,
  FaTextarea,
} from '@yudream/components'
import { onMounted, ref } from 'vue'
import { useEduroamAccounts } from '../composables/useEduroamAccounts'
import { formatTime, statusVariant } from '../types'

const props = defineProps<{ sdk: YuDreamPluginSdk }>()

const model = useEduroamAccounts(props.sdk)

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '正常', value: 'ACTIVE' },
  { label: '已禁止', value: 'BLOCKED' },
]

const columns: TableColumn<EduroamAccount>[] = [
  { accessorKey: 'email', header: '本站邮箱', width: 240 },
  { accessorKey: 'identity', header: 'Eduroam 账号', width: 220 },
  { accessorKey: 'status', header: '状态', width: 110, align: 'center' },
  { accessorKey: 'localUserId', header: '站内账号', width: 200 },
  { accessorKey: 'loginCount', header: '登录次数', width: 100, align: 'center' },
  { accessorKey: 'lastLoginAt', header: '最近登录', width: 170 },
  { accessorKey: 'lastLoginIp', header: '来源 IP', width: 140 },
  { id: 'operation', header: '操作', width: 220, align: 'center', fixed: 'right' },
]

const blockVisible = ref(false)
const blockTarget = ref<EduroamAccount | null>(null)
const blockReason = ref('')

function openBlock(row: EduroamAccount) {
  blockTarget.value = row
  blockReason.value = ''
  blockVisible.value = true
}

async function submitBlock() {
  const target = blockTarget.value
  if (!target) {
    return
  }
  const done = await model.block(target, blockReason.value.trim() || '管理员禁止登录')
  if (done) {
    blockVisible.value = false
  }
}

onMounted(() => model.load(true))
</script>

<template>
  <FaPageHeader
    title="Eduroam 登录账号"
    description="所有用校园网 / Eduroam 账号登录过的账号；站内账号列显示该邮箱在站内是否已有账号，被禁止的账号即使密码正确也无法登录。"
    class="mb-0"
  >
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
      table-class="min-w-[1440px]"
      border
      stripe
      column-visibility
      :columns="columns"
      :data="model.rows"
      empty-text="还没有账号用 Eduroam 登录过"
    >
      <template #toolbar>
        <FaSearchBar class="w-full">
          <div class="eduroam-filters">
            <FaInput
              v-model="model.filters.keyword"
              class="eduroam-search"
              placeholder="搜索邮箱 / Eduroam 账号"
              clearable
              @keydown.enter="model.load(true)"
              @clear="model.load(true)"
            />
            <FaSelect
              v-model="model.filters.status"
              :options="statusOptions"
              class="eduroam-filter-select"
              @change="model.load(true)"
            />
            <div class="eduroam-actions">
              <FaButton variant="outline" @click="model.filters.keyword = ''; model.filters.status = ''; model.load(true)">重置</FaButton>
              <FaButton @click="model.load(true)">
                <FaIcon name="i-ri:search-line" />
                查询
              </FaButton>
            </div>
          </div>
        </FaSearchBar>
      </template>

      <template #cell-email="{ row }">
        <span class="break-all">{{ row.original.email }}</span>
      </template>
      <template #cell-identity="{ row }">
        <span class="break-all">{{ row.original.identity || '-' }}</span>
      </template>
      <template #cell-status="{ row }">
        <FaTag :variant="statusVariant(row.original.status)">{{ row.original.statusLabel }}</FaTag>
        <div v-if="row.original.status === 'BLOCKED'" class="eduroam-sub eduroam-sub-center">
          禁止原因：{{ row.original.blockReason || '未填写原因' }}
        </div>
      </template>
      <template #cell-localUserId="{ row }">
        <div v-if="row.original.localUserId" class="break-all">
          <div>{{ row.original.localNickname || row.original.localUsername || '-' }}</div>
          <div class="eduroam-sub">用户名：{{ row.original.localUsername || '-' }}</div>
        </div>
        <span v-else class="eduroam-sub">未注册</span>
      </template>
      <template #cell-lastLoginAt="{ row }">{{ formatTime(row.original.lastLoginAt) }}</template>
      <template #cell-lastLoginIp="{ row }">{{ row.original.lastLoginIp || '-' }}</template>
      <template #cell-operation="{ row }">
        <div class="flex-center gap-2">
          <FaButton v-if="row.original.status === 'BLOCKED'" size="sm" variant="outline" @click="model.unblock(row.original)">
            解除禁止
          </FaButton>
          <FaButton v-else size="sm" variant="outline" @click="openBlock(row.original)">
            禁止登录
          </FaButton>
          <FaButton size="sm" variant="destructive" @click="model.remove(row.original)">删除</FaButton>
        </div>
      </template>

      <template #card="{ row }">
        <div class="flex flex-col gap-2 text-sm">
          <div class="flex items-center justify-between gap-2">
            <span class="text-base font-semibold break-all">{{ row.email }}</span>
            <FaTag :variant="statusVariant(row.status)">{{ row.statusLabel }}</FaTag>
          </div>
          <div class="eduroam-sub break-all">Eduroam 账号：{{ row.identity || '-' }}</div>
          <div class="eduroam-sub break-all">
            站内账号：<template v-if="row.localUserId">{{ row.localNickname || row.localUsername || '-' }}（{{ row.localUsername || '-' }}）</template>
            <template v-else>未注册</template>
          </div>
          <div class="eduroam-sub">登录 {{ row.loginCount }} 次 · 最近 {{ formatTime(row.lastLoginAt) }}</div>
          <div class="eduroam-sub">来源 IP：{{ row.lastLoginIp || '-' }}</div>
          <div v-if="row.status === 'BLOCKED'" class="eduroam-sub">禁止原因：{{ row.blockReason || '未填写原因' }}</div>
          <div class="eduroam-actions">
            <FaButton v-if="row.status === 'BLOCKED'" size="sm" variant="outline" @click="model.unblock(row)">解除禁止</FaButton>
            <FaButton v-else size="sm" variant="outline" @click="openBlock(row)">禁止登录</FaButton>
            <FaButton size="sm" variant="destructive" @click="model.remove(row)">删除</FaButton>
          </div>
        </div>
      </template>
    </FaResponsiveTable>

    <FaPagination
      v-model:page="model.pager.page"
      v-model:size="model.pager.size"
      :total="model.pager.total"
      class="mt-3"
      @page-change="model.load()"
      @size-change="model.load(true)"
    />

    <FaModal
      v-model="blockVisible"
      title="禁止 Eduroam 登录"
      class="max-w-[520px]"
      :show-confirm-button="false"
      :show-cancel-button="false"
    >
      <div class="eduroam-form">
        <FaAlert
          v-if="blockTarget"
          title="禁止后该账号无法登录"
          :description="`${blockTarget.email} 即使密码正确也会被拒绝，且不会再去探测上游认证服务；需要时可随时解除。`"
        />
        <label class="eduroam-field">
          <span class="eduroam-label">禁止原因（会写入记录备查）</span>
          <FaTextarea v-model="blockReason" class="w-full" placeholder="例如：账号盗用 / 非本校成员" />
        </label>
        <div class="eduroam-actions eduroam-actions-end">
          <FaButton variant="outline" @click="blockVisible = false">取消</FaButton>
          <FaButton variant="destructive" :loading="model.saving" @click="submitBlock">确认禁止</FaButton>
        </div>
      </div>
    </FaModal>
  </FaPageMain>
</template>
