<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { YggcClientView } from '../types'
import { FaButton, FaIcon, FaInput, FaModal, FaPagination, FaResponsiveTable, FaSearchBar, FaSwitch, FaTag, FaTextarea, useFaModal } from '@yudream/components'
import { computed, onMounted, reactive, ref } from 'vue'
import { createYggcApi } from '../api/yggc-api'
import { useYggcClients } from '../composables/useYggcClients'

const props = defineProps<{
  sdk: YuDreamPluginSdk
  route?: RouteLocationNormalizedLoaded
}>()

void props.route

const api = createYggcApi(props.sdk)
const model = useYggcClients(props.sdk)
const confirm = useFaModal()
/** 当前被声明为共享客户端（发现文档 shared_client_id）的应用 id。 */
const sharedClientId = ref('')
const pagination = reactive({ page: 1, size: 10 })
const modalTitle = computed(() => model.editing ? '编辑应用' : '新建应用')
const columns: TableColumn<YggcClientView>[] = [
  { id: 'name', header: '应用', width: 220, fixed: 'left' },
  { id: 'clientId', header: 'client_id', width: 200 },
  { id: 'redirectUris', header: '回调地址', width: 300 },
  { id: 'type', header: '类型', width: 110 },
  { id: 'enabled', header: '状态', width: 100 },
  { id: 'createdAt', header: '创建时间', width: 170 },
  { id: 'operation', header: '操作', width: 230, align: 'center', fixed: 'right' },
]

function formatTime(value: number) {
  return value ? new Date(value).toLocaleString() : '-'
}

async function search() {
  pagination.page = 1
  await model.load(pagination.page, pagination.size)
}

async function changePage(page: number) {
  pagination.page = page
  await model.load(pagination.page, pagination.size)
}

async function changeSize(size: number) {
  pagination.size = size
  pagination.page = 1
  await model.load(pagination.page, pagination.size)
}

function askDelete(client: YggcClientView) {
  confirm.confirm({
    title: '删除应用',
    content: `确认删除“${client.name}”吗？其名下全部令牌将被吊销。`,
    onConfirm: () => model.remove(client),
  })
}

onMounted(async () => {
  await Promise.all([
    model.load(pagination.page, pagination.size),
    loadSharedClientId(),
  ])
})

/** 标记哪条应用正被用作共享客户端；失败时静默（不影响列表本身）。 */
async function loadSharedClientId() {
  try {
    const settings = await api.config()
    sharedClientId.value = settings.sharedClientId ?? ''
  }
  catch {
    sharedClientId.value = ''
  }
}
</script>

<template>
  <div class="yggc-plugin">
    <section class="yggc-toolbar">
      <div>
        <span>OAuth 应用</span>
        <h2>应用管理</h2>
      </div>
      <div class="yggc-actions">
        <FaButton variant="outline" :loading="model.loading" @click="model.load(pagination.page, pagination.size)">刷新</FaButton>
        <FaButton @click="model.openCreate">新建应用</FaButton>
      </div>
    </section>

    <section class="yggc-panel">
      <FaSearchBar class="mb-3 w-full">
        <div class="yggc-filter-bar">
          <FaInput v-model="model.keyword" placeholder="搜索应用名称或 client_id" clearable @keydown.enter="search" @clear="search" />
          <FaButton variant="outline" @click="search"><FaIcon name="i-ri:search-line" />查询</FaButton>
        </div>
      </FaSearchBar>
      <FaResponsiveTable
        v-loading="model.loading"
        row-key="id"
        table-root-class="max-w-full overflow-x-auto rounded-lg"
        table-class="min-w-[1330px]"
        border
        stripe
        :columns="columns"
        :data="model.records"
        empty-text="暂无应用"
      >
        <template #cell-name="{ row }">
          <strong>{{ row.original.name }}</strong>
          <FaTag v-if="row.original.id === sharedClientId" class="ml-2" variant="secondary">共享登录</FaTag>
        </template>
        <template #cell-clientId="{ row }">
          <code class="yggc-code">{{ row.original.id }}</code>
        </template>
        <template #cell-redirectUris="{ row }">
          <div class="yggc-uri-list">
            <code v-for="uri in row.original.redirectUris" :key="uri">{{ uri }}</code>
            <span v-if="!row.original.redirectUris.length" class="yggc-muted">未配置</span>
          </div>
        </template>
        <template #cell-type="{ row }">
          <FaTag :variant="row.original.publicClient ? 'secondary' : 'default'">
            {{ row.original.publicClient ? '公共客户端（PKCE）' : '机密客户端' }}
          </FaTag>
        </template>
        <template #cell-enabled="{ row }">
          <FaTag :variant="row.original.enabled ? 'default' : 'secondary'">{{ row.original.enabled ? '启用中' : '已禁用' }}</FaTag>
        </template>
        <template #cell-createdAt="{ row }">{{ formatTime(row.original.createdAt) }}</template>
        <template #cell-operation="{ row }">
          <div class="yggc-actions">
            <FaButton size="sm" variant="outline" @click="model.openEdit(row.original)">编辑</FaButton>
            <FaButton v-if="!row.original.publicClient" size="sm" variant="outline" @click="model.resetSecret(row.original)">重置密钥</FaButton>
            <FaButton size="sm" variant="destructive" @click="askDelete(row.original)">删除</FaButton>
          </div>
        </template>
      </FaResponsiveTable>
      <FaPagination
        :page="pagination.page"
        :size="pagination.size"
        :total="model.total"
        class="mt-3"
        @update:page="changePage"
        @update:size="changeSize"
      />
    </section>

    <FaModal v-model="model.modalVisible" :title="modalTitle" class="max-w-[720px]" :show-confirm-button="false" :show-cancel-button="false">
      <div class="yggc-form">
        <label>
          <span>应用名称</span>
          <input v-model="model.form.name" class="yggc-input" placeholder="例如：某启动器 / 某客户端">
        </label>
        <label>
          <span>回调地址（redirect_uri）</span>
          <FaTextarea v-model="model.form.redirectUrisText" class="w-full" placeholder="每行一个完整 URL，须与客户端授权请求完全一致" />
          <p class="yggc-help">只用于设备码登录（如 PCL-CE 的 Yggdrasil Connect）的应用可以留空；留空也避免共享 client_id 被用于授权码流。</p>
        </label>
        <div class="yggc-form-grid two">
          <label>
            <span>客户端类型</span>
            <FaSwitch v-model="model.form.publicClient" />
            <p class="yggc-help">公共客户端（启动器等）使用 PKCE 校验，无需 client_secret；机密客户端使用密钥校验。</p>
          </label>
          <label>
            <span>启用状态</span>
            <FaSwitch v-model="model.form.enabled" />
          </label>
        </div>
      </div>
      <template #footer>
        <FaButton variant="outline" @click="model.modalVisible = false">取消</FaButton>
        <FaButton :loading="model.saving" @click="model.save">保存应用</FaButton>
      </template>
    </FaModal>
  </div>
</template>
