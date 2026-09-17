<script setup lang="ts">
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { SsoSettings, StudentMapping } from '../types'
import { FaAlert, FaButton, FaCard, FaIcon, FaInput, FaLabel, FaPageHeader, FaPageMain, FaSelect, FaSwitch, useFaToast } from '@yudream/components'
import { computed, onMounted, reactive, ref } from 'vue'
import { createCasApi } from '../api/cas-api'
import { errorMessage } from '../types'

const props = defineProps<{ sdk: YuDreamPluginSdk }>()
const api = createCasApi(props.sdk)
const toast = useFaToast()

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const registering = ref(false)
const mappingLoading = ref(false)
const mappingSaving = ref(false)
const error = ref('')
const testMessage = ref('')
const testOk = ref<boolean | null>(null)
const clientSecret = ref('')

const mapping = reactive<StudentMapping>({
  requireBinding: false,
  nameKey: '',
  deptKey: '',
  majorKey: '',
  gradeKey: '',
  classKey: '',
})

const form = reactive<SsoSettings>({
  enabled: false,
  protocol: 'CAS',
  displayName: '塔里木大学统一身份认证',
  icon: 'i-ri:graduation-cap-line',
  casBaseUrl: 'https://auth.taru.edu.cn',
  loginPath: '/authserver/login',
  validatePath: '/cas/p3/serviceValidate',
  oidcIssuer: 'https://auth.taru.edu.cn/authserver/oidc/',
  oidcAuthorizePath: '/authserver/oidc/authorize',
  oidcTokenPath: '/authserver/oidc/accessToken',
  oidcUserinfoPath: '/authserver/oidc/profile',
  oidcJwksPath: '/authserver/oidc/jwks',
  oidcRegisterPath: '/authserver/oidc/register',
  clientId: '',
  clientSecretConfigured: false,
  scopes: 'openid profile email',
  callbackUrl: '',
  casStateMode: 'query',
  ready: false,
})

const protocolOptions = [
  { label: 'CAS 3.0（推荐，无需客户端密钥）', value: 'CAS' },
  { label: 'OIDC 授权码', value: 'OIDC' },
]

const stateModeOptions = [
  { label: '标准：state 追加在 service 查询串上', value: 'query' },
  { label: '兜底：service 走插件固定中转地址', value: 'relay' },
]

const oidcMode = computed(() => form.protocol === 'OIDC')

/** 兜底中转地址预览：站点基址从回调地址里截掉 /api/external-login 得到。 */
const relayPathPreview = computed(() => {
  const index = form.callbackUrl.indexOf('/api/external-login')
  const base = index > 0 ? form.callbackUrl.slice(0, index) : ''
  return `${base}/api/plugins/cas/public/relay`
})

function apply(data: SsoSettings) {
  Object.assign(form, data)
  clientSecret.value = ''
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    apply(await api.settings())
  }
  catch (caught) {
    error.value = errorMessage(caught, '加载设置失败')
  }
  finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  error.value = ''
  try {
    apply(await api.saveSettings(form, clientSecret.value.trim() || undefined))
    toast.success('设置已保存')
  }
  catch (caught) {
    error.value = errorMessage(caught, '保存失败')
  }
  finally {
    saving.value = false
  }
}

async function test() {
  testing.value = true
  error.value = ''
  testMessage.value = ''
  testOk.value = null
  try {
    const result = await api.test()
    testOk.value = result.ok
    testMessage.value = result.message
    if (result.ok) {
      toast.success('连通性检查通过')
    }
    else {
      toast.error(result.message || '连通性检查未通过')
    }
  }
  catch (caught) {
    error.value = errorMessage(caught, '连通性检查失败')
  }
  finally {
    testing.value = false
  }
}

async function registerOidc() {
  registering.value = true
  error.value = ''
  try {
    const result = await api.registerOidc(form.displayName)
    if (result.clientId) {
      form.clientId = result.clientId
    }
    form.clientSecretConfigured = form.clientSecretConfigured || result.clientSecretIssued
    toast.success(result.clientSecretIssued ? '动态注册成功，客户端密钥已写入密钥库' : '动态注册完成')
    await load()
  }
  catch (caught) {
    error.value = errorMessage(caught, '动态注册失败')
  }
  finally {
    registering.value = false
  }
}

async function loadMapping() {
  mappingLoading.value = true
  try {
    Object.assign(mapping, await api.mapping())
  }
  catch (caught) {
    toast.error(errorMessage(caught, '加载学生信息映射失败'))
  }
  finally {
    mappingLoading.value = false
  }
}

async function saveMapping() {
  mappingSaving.value = true
  try {
    Object.assign(mapping, await api.saveMapping({ ...mapping }))
    toast.success(mapping.requireBinding ? '已开启：未绑定统一身份认证的成员将无法使用其他功能' : '已关闭绑定门禁')
  }
  catch (caught) {
    toast.error(errorMessage(caught, '保存映射配置失败'))
  }
  finally {
    mappingSaving.value = false
  }
}

onMounted(() => {
  load()
  loadMapping()
})
</script>

<template>
  <section class="tsu-page">
    <FaPageHeader title="CAS 统一身份认证" description="把学校 / 机构的 CAS / OIDC 接到站点登录页。首次登录走宿主绑定流程，不会自动开户。">
      <FaButton variant="outline" :loading="loading" @click="load">
        <FaIcon name="i-ri:refresh-line" />
        刷新
      </FaButton>
    </FaPageHeader>

    <FaPageMain>
      <form class="tsu-stack" @submit.prevent="save">
        <FaAlert v-if="error" variant="destructive" title="操作未完成" :description="error" />
        <FaAlert
          v-if="form.ready"
          title="登录入口已就绪"
          description="启用后会在登录页「登录方式」中出现本提供方。未绑定本站账号的用户会被引导登录或注册后再绑定。"
        />
        <FaAlert
          v-else
          title="尚未对登录页开放"
          :description="oidcMode ? 'OIDC 需要本站回调地址、client_id 与 client_secret。' : 'CAS 需要填写本站回调地址，并把该域名登记到学校网信中心的 service 白名单。'"
        />

        <FaCard title="开关与展示" content-class="tsu-card-content">
          <div class="tsu-switch-row">
            <FaSwitch v-model="form.enabled" />
            <div class="tsu-switch-copy">
              <strong>启用登录入口</strong>
              <small>配置不完整时即使打开也不会出现在登录页。</small>
            </div>
          </div>
          <div class="tsu-settings-grid">
            <FaLabel label="显示名称" class="tsu-field">
              <FaInput v-model="form.displayName" class="w-full" maxlength="40" />
            </FaLabel>
            <FaLabel label="图标" class="tsu-field">
              <FaInput v-model="form.icon" class="w-full" maxlength="80" />
              <span class="tsu-field-hint">Remix Icon 类名，例如 i-ri:graduation-cap-line。</span>
            </FaLabel>
          </div>
        </FaCard>

        <FaCard title="协议" description="学校认证中心同时暴露 CAS3 XML 与 OIDC。推荐先走 CAS，无需向网信中心申请 client_secret。" content-class="tsu-card-content">
          <FaLabel label="认证协议" class="tsu-field">
            <FaSelect v-model="form.protocol" :options="protocolOptions" />
          </FaLabel>
          <FaLabel label="认证服务根地址" class="tsu-field">
            <FaInput v-model="form.casBaseUrl" class="w-full" maxlength="200" placeholder="https://auth.taru.edu.cn" />
          </FaLabel>
          <FaLabel label="本站回调地址" class="tsu-field">
            <FaInput v-model="form.callbackUrl" class="w-full" maxlength="400" placeholder="https://你的站点/api/external-login/callback" />
            <span class="tsu-field-hint">必须与宿主统一回调端点一致。CAS 会把 ticket 与 state 带回该地址；OIDC 的 redirect_uri 也必须精确匹配已登记值。</span>
          </FaLabel>
        </FaCard>

        <FaCard v-if="!oidcMode" title="CAS 路径" content-class="tsu-card-content">
          <div class="tsu-settings-grid">
            <FaLabel label="登录路径" class="tsu-field">
              <FaInput v-model="form.loginPath" class="w-full" maxlength="120" />
            </FaLabel>
            <FaLabel label="票据校验路径" class="tsu-field">
              <FaInput v-model="form.validatePath" class="w-full" maxlength="120" />
            </FaLabel>
          </div>
          <p class="tsu-field-hint">
            CAS 没有独立 state 参数，插件会把宿主签发的 state 编码进 service URL。校验时必须用完全相同的 service 回放。
          </p>
        </FaCard>

        <FaCard v-if="!oidcMode" title="state 承载方式" description="个别 CAS 部署不接受 service 里带查询串，此时切到兜底模式。" content-class="tsu-card-content">
          <FaLabel label="承载方式" class="tsu-field">
            <FaSelect v-model="form.casStateMode" :options="stateModeOptions" />
          </FaLabel>
          <template v-if="form.casStateMode === 'relay'">
            <p class="tsu-field-hint">
              兜底模式：跳转时 service 指向插件的固定中转地址
              <code>{{ relayPathPreview }}</code>（不含任何查询串），CAS 追加 ticket 回跳后，
              插件把宿主 state 补回 <code>/api/external-login/callback</code>，票据校验仍用同一个 service 串。
            </p>
            <p class="tsu-field-hint">
              代价：中转地址不带一次性标记，只能按「最近一次未消费的登录尝试」匹配（记录 5 分钟内有效、用后即删）。
              因此同时开多个标签页登录、或别人诱导你打开一个带 ticket 的中转链接时，可能出现会话串号（登录 CSRF）。
              仅在标准模式确实不被学校 CAS 接受时开启。
            </p>
            <p v-if="!form.callbackUrl.includes('/api/external-login')" class="tsu-field-hint tsu-field-hint--danger">
              兜底模式要求回调地址包含 <code>/api/external-login</code>（插件据此反推站点基址与中转地址）。
            </p>
          </template>
          <p v-else class="tsu-field-hint">
            标准模式：service = 回调地址 + <code>?state=&lt;宿主 state&gt;</code>，CAS 回跳时原样带回，安全性最好。
          </p>
        </FaCard>

        <FaCard v-else title="OIDC 客户端" content-class="tsu-card-content">
          <div class="tsu-settings-grid">
            <FaLabel label="Issuer" class="tsu-field">
              <FaInput v-model="form.oidcIssuer" class="w-full" maxlength="240" />
            </FaLabel>
            <FaLabel label="client_id" class="tsu-field">
              <FaInput v-model="form.clientId" class="w-full" maxlength="120" />
            </FaLabel>
            <FaLabel label="client_secret" class="tsu-field">
              <FaInput v-model="clientSecret" type="password" class="w-full" maxlength="240" :placeholder="form.clientSecretConfigured ? '已保存，留空则不修改' : '写入插件密钥库，不会回显'" />
              <span class="tsu-field-hint">{{ form.clientSecretConfigured ? '密钥库中已有客户端密钥。' : '尚未配置客户端密钥。' }}</span>
            </FaLabel>
            <FaLabel label="scopes" class="tsu-field">
              <FaInput v-model="form.scopes" class="w-full" maxlength="120" />
            </FaLabel>
          </div>
          <div class="tsu-settings-grid">
            <FaLabel label="授权路径" class="tsu-field">
              <FaInput v-model="form.oidcAuthorizePath" class="w-full" maxlength="120" />
            </FaLabel>
            <FaLabel label="Token 路径" class="tsu-field">
              <FaInput v-model="form.oidcTokenPath" class="w-full" maxlength="120" />
            </FaLabel>
            <FaLabel label="UserInfo 路径" class="tsu-field">
              <FaInput v-model="form.oidcUserinfoPath" class="w-full" maxlength="120" />
            </FaLabel>
            <FaLabel label="JWKS 路径" class="tsu-field">
              <FaInput v-model="form.oidcJwksPath" class="w-full" maxlength="120" />
            </FaLabel>
            <FaLabel label="动态注册路径" class="tsu-field">
              <FaInput v-model="form.oidcRegisterPath" class="w-full" maxlength="120" />
            </FaLabel>
          </div>
          <div class="tsu-actions">
            <FaButton type="button" variant="outline" :loading="registering" :disabled="saving" @click="registerOidc">
              向认证中心动态注册客户端
            </FaButton>
            <span class="tsu-field-hint">注册成功后会回写 client_id，并把 client_secret 存入密钥库。是否允许动态注册由学校网信中心决定。</span>
          </div>
        </FaCard>

        <FaCard title="连通性" content-class="tsu-card-content">
          <div class="tsu-actions">
            <FaButton type="button" variant="outline" :loading="testing" @click="test">
              探测认证端点
            </FaButton>
            <span v-if="testMessage" class="tsu-status">
              {{ testOk ? '通过：' : '未通过：' }}{{ testMessage }}
            </span>
          </div>
          <p class="tsu-field-hint">
            探测不会真正登录。CAS 会访问登录页并用无效票据打校验端点；OIDC 会读取 JWKS 并访问授权端点。
          </p>
        </FaCard>

        <FaCard title="访问控制与学生信息映射" description="登录时把认证属性归档为学生信息（可在「学生信息」页查看）；绑定门禁开启后，未绑定统一身份认证的成员会被引导完成绑定。" content-class="tsu-card-content">
          <div class="tsu-switch-row">
            <FaSwitch v-model="mapping.requireBinding" />
            <div class="tsu-switch-copy">
              <strong>未绑定则限制使用其他功能</strong>
              <small>仅在前端界面强制；管理员（持有本插件管理权限）始终豁免，登录入口未就绪时自动失效。</small>
            </div>
          </div>
          <div class="tsu-settings-grid">
            <FaLabel label="姓名字段键" class="tsu-field">
              <FaInput v-model="mapping.nameKey" class="w-full" maxlength="60" placeholder="留空自动探测（name / cn / displayName）" :disabled="mappingLoading" />
            </FaLabel>
            <FaLabel label="学院字段键" class="tsu-field">
              <FaInput v-model="mapping.deptKey" class="w-full" maxlength="60" placeholder="留空自动探测（department / org_dn / ou …）" :disabled="mappingLoading" />
            </FaLabel>
            <FaLabel label="专业字段键" class="tsu-field">
              <FaInput v-model="mapping.majorKey" class="w-full" maxlength="60" placeholder="留空自动探测（major / subject …）" :disabled="mappingLoading" />
            </FaLabel>
            <FaLabel label="年级字段键" class="tsu-field">
              <FaInput v-model="mapping.gradeKey" class="w-full" maxlength="60" placeholder="留空自动探测（grade / entranceYear …）" :disabled="mappingLoading" />
            </FaLabel>
            <FaLabel label="班级字段键" class="tsu-field">
              <FaInput v-model="mapping.classKey" class="w-full" maxlength="60" placeholder="留空自动探测（className / class / clazz …）" :disabled="mappingLoading" />
            </FaLabel>
          </div>
          <div class="tsu-actions">
            <FaButton type="button" :loading="mappingSaving" :disabled="mappingLoading" @click="saveMapping">
              保存映射配置
            </FaButton>
            <span class="tsu-field-hint">学校实际返回的属性键名可在「学生信息 → 详情 → 原始属性」中核对。</span>
          </div>
        </FaCard>

        <div class="tsu-actions tsu-actions-end">
          <FaButton type="submit" :loading="saving">
            保存设置
          </FaButton>
        </div>
      </form>
    </FaPageMain>
  </section>
</template>
