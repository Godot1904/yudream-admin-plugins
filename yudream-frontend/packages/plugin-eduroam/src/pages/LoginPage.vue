<script setup lang="ts">
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { FaAlert, FaButton, FaCard, FaIcon, FaInput, FaPageHeader, FaPageMain, useFaToast } from '@yudream/components'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useEduroamLogin } from '../composables/useEduroamLogin'
import EduroamRegistrationForm from '../components/EduroamRegistrationForm.vue'

/**
 * Eduroam 登录凭据页（`/eduroam`）。
 *
 * <p>本页就是 Eduroam 这个第三方登录项背后的「授权页」：宿主登录页点击「Eduroam 认证」后把浏览器
 * 带到这里（带一次性 state），认证成功后本页带着票据回宿主统一的外部登录回调端点，由宿主完成
 * 第三方账号绑定与会话签发——本页不签发任何登录态。
 */
const props = defineProps<{ sdk: YuDreamPluginSdk }>()

const route = useRoute()
const router = useRouter()
const toast = useFaToast()
const model = useEduroamLogin(props.sdk)

const state = computed(() => String(route.query.state || ''))
const provider = computed(() => String(route.query.provider || 'eduroam'))
const type = computed(() => String(route.query.type || 'eduroam'))
const fromLoginPage = computed(() => Boolean(state.value))
const tutorialOpen = ref(false)

onMounted(model.loadConfig)
onUnmounted(model.reset)
watch(state, model.reset)

function backToLogin() {
  router.push({ path: '/login' })
}

async function submit() {
  const payload = await model.submit(state.value)
  if (!payload?.success) {
    return
  }
  if (!model.needsRegistration) await continueLogin()
}

async function register() {
  const payload = await model.register(state.value)
  if (payload?.success && !payload.accountCreated) {
    toast.success('本站账号已存在，请用已有密码完成绑定')
    await continueLogin()
  }
}

async function continueLogin() {
  const payload = model.result
  if (!payload?.success || model.submitting) return
  model.sitePassword = ''
  model.confirmPassword = ''
  // 回宿主的第三方登录回调：宿主核销 state、调用插件的 exchange 换取身份，再签发会话或要求绑定已有账号。
  await router.replace({
    path: '/external-login/callback',
    query: {
      code: payload.ticket,
      state: state.value,
      provider: provider.value,
      type: type.value,
    },
  })
}
</script>

<template>
  <section class="eduroam-page">
    <FaPageHeader
      title="Eduroam 登录"
      description="使用校园网 / Eduroam 认证；首次使用时设置本站密码，自动创建本站账号。"
    >
      <FaButton variant="outline" @click="backToLogin">
        <FaIcon name="i-ri:arrow-go-back-line" />
        返回登录
      </FaButton>
    </FaPageHeader>

    <FaPageMain>
      <FaCard class="eduroam-card">
        <p v-if="model.loading" class="eduroam-help">正在加载登录配置…</p>

        <FaAlert v-else-if="!model.config && model.error" variant="destructive" title="加载失败" :description="model.error" />

        <FaAlert
          v-else-if="!model.enabled"
          variant="destructive"
          title="Eduroam 登录未开放"
          description="管理员尚未开启 Eduroam 登录，请改用其它方式登录或联系管理员。"
        />

        <div v-else class="eduroam-login">
          <EduroamRegistrationForm
            v-if="model.needsRegistration && model.result"
            v-model:password="model.sitePassword"
            v-model:confirmation="model.confirmPassword"
            :email="model.result.email"
            :busy="model.submitting"
            @submit="register"
            @bind-existing="continueLogin"
          />

          <template v-else-if="model.result?.accountCreated">
            <FaAlert title="本站账号已创建" :description="`登录邮箱：${model.result.email}`" />
            <p class="eduroam-help">
              请继续，并用此邮箱与刚设置的本站密码登录，完成首次绑定。以后即可直接使用 Eduroam 登录。
            </p>
            <FaButton @click="continueLogin">继续完成首次绑定</FaButton>
          </template>

          <template v-else>
            <FaAlert
              v-if="!fromLoginPage"
              title="请从登录页进入"
              description="本页需要携带登录会话参数。请回到登录页点击「Eduroam 认证」入口，或直接访问登录页重新发起。"
            />

            <label class="eduroam-field">
              <span class="eduroam-label">{{ model.accountSuffix ? '学号 / 工号' : 'Eduroam 账号' }}</span>
              <FaInput
                v-model="model.account"
                class="w-full"
                end-class="eduroam-account-addon"
                aria-describedby="eduroam-account-hint"
                :placeholder="model.accountPlaceholder"
                :disabled="model.submitting"
              >
                <template v-if="model.accountSuffix && !model.account.includes('@')" #end>
                  <span class="eduroam-account-suffix" :title="model.accountSuffix">{{ model.accountSuffix }}</span>
                </template>
              </FaInput>
              <small id="eduroam-account-hint" class="eduroam-help">{{ model.config?.accountHint }}</small>
            </label>

            <div class="eduroam-field">
              <span class="eduroam-label">Eduroam 密码</span>
              <FaInput
                v-model="model.password"
                type="password"
                autocomplete="current-password"
                class="w-full"
                placeholder="校园网 / Eduroam 密码"
                :disabled="model.submitting"
                @keydown.enter="submit"
              />
              <small class="eduroam-help">校园网密码只用于本次认证，本站不会保存。</small>
            </div>

            <div class="eduroam-actions">
              <FaButton variant="outline" @click="tutorialOpen = !tutorialOpen">
                <FaIcon name="i-ri:question-line" />
                {{ tutorialOpen ? '收起教程' : '查看教程' }}
              </FaButton>
              <FaButton :loading="model.submitting" :disabled="!fromLoginPage || model.submitting" @click="submit">
                <FaIcon name="i-ri:login-box-line" />
                验证校园账号
              </FaButton>
            </div>

            <pre v-if="tutorialOpen" class="eduroam-tutorial">{{ model.config?.tutorialMarkdown }}</pre>
          </template>
          <FaAlert v-if="model.error" variant="destructive" title="操作未完成" :description="model.error" />
        </div>
      </FaCard>
    </FaPageMain>
  </section>
</template>
