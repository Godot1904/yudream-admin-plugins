<script setup lang="ts">
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { StudentProfile } from '../types'
import { FaAlert, FaButton, FaCard, FaDrawer, FaIcon, FaInput, FaPageHeader, FaPageMain, FaPagination, FaTag, useFaToast } from '@yudream/components'
import { computed, onMounted, ref } from 'vue'
import { createCasApi } from '../api/cas-api'
import { errorMessage } from '../types'

const props = defineProps<{ sdk: YuDreamPluginSdk }>()
const api = createCasApi(props.sdk)
const toast = useFaToast()

const loading = ref(false)
const error = ref('')
const keyword = ref('')
const searchApplied = ref('')
const items = ref<StudentProfile[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const archiveAvailable = ref(true)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<StudentProfile | null>(null)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

const rawAttributesJson = computed(() => {
  if (!detail.value?.rawAttributes) {
    return ''
  }
  try {
    return JSON.stringify(JSON.parse(detail.value.rawAttributes), null, 2)
  }
  catch {
    return detail.value.rawAttributes
  }
})

/** 本页绑定统计：已绑定数量；宿主不支持查询时不展示统计，避免误导。 */
const boundCount = computed(() => items.value.filter(row => row.binding?.bound).length)
const bindingQueryable = computed(() => items.value.some(row => row.binding?.available))

function bindingLabel(row: StudentProfile) {
  const binding = row.binding
  if (binding?.bound) {
    return binding.username || binding.nickname || binding.userId || '已绑定'
  }
  return ''
}

/** 学院 / 班级来自学生档案插件；对方不可用或该学号未填写时显示 —。 */
function archiveValue(row: StudentProfile, key: 'college' | 'className') {
  return row.archive?.filled ? (row.archive[key] || '—') : '—'
}

function formatTime(value: number | string) {
  if (value === null || value === undefined || value === '') {
    return '—'
  }
  // 宿主会把 Long 序列化成字符串，必须先转数字，否则 Invalid Date
  const timestamp = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(timestamp) || timestamp <= 0) {
    return '—'
  }
  return new Date(timestamp).toLocaleString('zh-CN', { hour12: false })
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await api.students(page.value, size.value, searchApplied.value.trim() || undefined)
    items.value = result.items || []
    total.value = result.total || 0
    archiveAvailable.value = result.archiveAvailable !== false
    if (page.value > 1 && items.value.length === 0) {
      page.value = 1
      return load()
    }
  }
  catch (caught) {
    error.value = errorMessage(caught, '加载学生信息失败')
  }
  finally {
    loading.value = false
  }
}

function search() {
  searchApplied.value = keyword.value
  page.value = 1
  load()
}

function reset() {
  keyword.value = ''
  searchApplied.value = ''
  page.value = 1
  load()
}

function onPageChange(next: number) {
  page.value = next
  load()
}

function onSizeChange(next: number) {
  size.value = next
  page.value = 1
  load()
}

async function openDetail(socialUid: string) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await api.studentDetail(socialUid)
  }
  catch (caught) {
    toast.error(errorMessage(caught, '加载学生详情失败'))
    detailVisible.value = false
  }
  finally {
    detailLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="tsu-page">
    <FaPageHeader title="学生信息" description="统一身份认证登录后自动归档的认证身份（学号 / 姓名），并显示该学号在本站的账号绑定与学生档案里的学院、班级。">
      <FaButton variant="outline" :loading="loading" @click="load">
        <FaIcon name="i-ri:refresh-line" />
        刷新
      </FaButton>
    </FaPageHeader>

    <FaPageMain>
      <FaAlert
        v-if="error"
        variant="destructive"
        title="操作未完成"
        :description="error"
      />

      <FaCard content-class="tsu-card-content">
        <form class="tsu-actions" @submit.prevent="search">
          <div class="tsu-search">
            <FaInput
              v-model="keyword"
              placeholder="搜索学工号 / 姓名 / 邮箱 / 电话"
              maxlength="60"
              @keydown.enter.prevent="search"
            />
          </div>
          <FaButton type="submit" variant="outline" :loading="loading">
            <FaIcon name="i-ri:search-line" />
            搜索
          </FaButton>
          <FaButton v-if="searchApplied" type="button" variant="ghost" @click="reset">
            清除
          </FaButton>
          <span class="tsu-field-hint">共 {{ total }} 条{{ searchApplied ? `，关键词「${searchApplied}」` : '' }}</span>
          <span v-if="bindingQueryable" class="tsu-field-hint">本页已绑定 {{ boundCount }} / {{ items.length }}</span>
        </form>

        <p v-if="!archiveAvailable" class="tsu-field-hint">
          未安装或未启用学生档案插件（yudream-student-info），学院 / 班级列暂时为空；安装后自动读取。
        </p>

        <div class="tsu-table-wrap">
          <table class="tsu-table">
            <thead>
              <tr>
                <th>学工号</th>
                <th>绑定账号</th>
                <th>姓名</th>
                <th>学院</th>
                <th>班级</th>
                <th>登录次数</th>
                <th>最近登录</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading && items.length === 0">
                <td colspan="8" class="tsu-table-empty">
                  加载中…
                </td>
              </tr>
              <tr v-else-if="items.length === 0">
                <td colspan="8" class="tsu-table-empty">
                  暂无数据：成员通过 CAS 登录一次后，认证身份会自动出现在这里。
                </td>
              </tr>
              <tr v-for="row in items" v-else :key="row.socialUid">
                <td class="tsu-table-mono">{{ row.socialUid }}</td>
                <td>
                  <FaTag v-if="row.binding?.bound">{{ bindingLabel(row) }}</FaTag>
                  <span v-else-if="row.binding && !row.binding.available" class="tsu-table-time" :title="row.binding.message || ''">无法查询</span>
                  <span v-else class="tsu-table-time">未绑定</span>
                </td>
                <td>{{ row.name || '—' }}</td>
                <td>{{ archiveValue(row, 'college') }}</td>
                <td>{{ archiveValue(row, 'className') }}</td>
                <td>{{ row.loginCount }}</td>
                <td class="tsu-table-time">{{ formatTime(row.lastSeenAt) }}</td>
                <td>
                  <FaButton type="button" variant="ghost" size="sm" @click="openDetail(row.socialUid)">
                    详情
                  </FaButton>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <FaPagination
          v-if="total > size"
          v-model:page="page"
          v-model:size="size"
          :total="total"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </FaCard>
    </FaPageMain>

    <FaDrawer v-model="detailVisible" title="学生详情" side="right" :footer="false" content-class="tsu-detail">
      <div v-if="detailLoading" class="tsu-table-empty">
        加载中…
      </div>
      <div v-else-if="detail" class="tsu-detail">
        <dl class="tsu-detail-grid">
          <dt>学工号</dt>
          <dd class="tsu-table-mono">{{ detail.socialUid }}</dd>
          <dt>姓名</dt>
          <dd>{{ detail.name || '—' }}</dd>
          <dt>邮箱</dt>
          <dd>{{ detail.email || '—' }}</dd>
          <dt>电话</dt>
          <dd>{{ detail.phone || '—' }}</dd>
          <dt>协议</dt>
          <dd><FaTag>{{ detail.protocol || '—' }}</FaTag></dd>
          <dt>登录次数</dt>
          <dd>{{ detail.loginCount }}</dd>
          <dt>首次记录</dt>
          <dd class="tsu-table-time">{{ formatTime(detail.firstSeenAt) }}</dd>
          <dt>最近登录</dt>
          <dd class="tsu-table-time">{{ formatTime(detail.lastSeenAt) }}</dd>
        </dl>

        <div class="tsu-raw">
          <p class="tsu-field-hint">
            学生档案（来自 yudream-student-info 插件，本插件只读、不写入）：学院与班级以那里的记录为准。
          </p>
          <dl v-if="detail.archive?.filled" class="tsu-detail-grid">
            <dt>姓名</dt>
            <dd>{{ detail.archive.studentName || '—' }}</dd>
            <dt>学院</dt>
            <dd>{{ detail.archive.college || '—' }}</dd>
            <dt>班级</dt>
            <dd>{{ detail.archive.className || '—' }}</dd>
          </dl>
          <p v-else-if="detail.archive && !detail.archive.available" class="tsu-field-hint">
            {{ detail.archive.message || '未安装学生档案插件' }}
          </p>
          <p v-else class="tsu-table-empty">
            该学号尚未在学生档案插件里填写学院 / 班级。
          </p>
        </div>

        <div class="tsu-raw">
          <p class="tsu-field-hint">
            本站账号绑定：宿主 external account 表里 (登录通道, 协议, 学工号) 命中的那条绑定记录。
          </p>
          <dl v-if="detail.binding?.bound" class="tsu-detail-grid">
            <dt>本站账号</dt>
            <dd class="tsu-table-mono">{{ detail.binding.username || '—' }}</dd>
            <dt>用户 ID</dt>
            <dd class="tsu-table-mono">{{ detail.binding.userId || '—' }}</dd>
            <dt>昵称</dt>
            <dd>{{ detail.binding.nickname || '—' }}</dd>
            <dt>邮箱</dt>
            <dd>{{ detail.binding.email || '—' }}</dd>
            <dt>手机</dt>
            <dd>{{ detail.binding.phone || '—' }}</dd>
            <dt>账号状态</dt>
            <dd>{{ detail.binding.status || '—' }}</dd>
          </dl>
          <p v-else-if="detail.binding && !detail.binding.available" class="tsu-field-hint tsu-field-hint--danger">
            {{ detail.binding.message || '宿主不支持查询绑定信息' }}
          </p>
          <p v-else class="tsu-table-empty">
            该学工号尚未绑定本站账号。
          </p>
        </div>

        <div class="tsu-raw">
          <p class="tsu-field-hint">
            认证中心返回的原始属性，用于核对学校到底返回了哪些字段。
          </p>
          <pre v-if="rawAttributesJson" class="tsu-raw-json">{{ rawAttributesJson }}</pre>
          <p v-else class="tsu-table-empty">
            本次登录未返回属性（CAS attributes 为空或 OIDC userinfo 无文本字段）。
          </p>
        </div>
      </div>
    </FaDrawer>
  </section>
</template>

<style scoped>
.tsu-search {
  width: min(320px, 100%);
}

.tsu-table-wrap {
  overflow-x: auto;
  border: 1px solid var(--color-border, #e5e6eb);
  border-radius: 8px;
}

.tsu-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.tsu-table th,
.tsu-table td {
  padding: 10px 12px;
  text-align: left;
  white-space: nowrap;
  border-bottom: 1px solid var(--color-border, #e5e6eb);
}

.tsu-table th {
  font-weight: 600;
  color: var(--color-text-2, #4e5969);
  background: var(--color-fill-1, #f7f8fa);
}

.tsu-table tbody tr:last-child td {
  border-bottom: none;
}

.tsu-table-empty {
  text-align: center;
  color: var(--color-text-3, #86909c);
}

.tsu-table-mono {
  font-family: var(--font-family-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
}

.tsu-table-time {
  color: var(--color-text-3, #86909c);
  font-size: 12px;
}

.tsu-detail {
  display: grid;
  gap: 16px;
}

.tsu-detail-grid {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  gap: 8px 12px;
  margin: 0;
  font-size: 13px;
}

.tsu-detail-grid dt {
  color: var(--color-text-3, #86909c);
}

.tsu-detail-grid dd {
  margin: 0;
  word-break: break-all;
}

.tsu-raw {
  display: grid;
  gap: 8px;
}

.tsu-raw-json {
  max-height: 320px;
  overflow: auto;
  margin: 0;
  padding: 12px;
  font-size: 12px;
  line-height: 1.6;
  border-radius: 8px;
  background: var(--color-fill-2, #f2f3f5);
  border: 1px solid var(--color-border, #e5e6eb);
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
