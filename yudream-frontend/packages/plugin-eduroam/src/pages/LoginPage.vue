<script setup lang="ts">
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { FaAlert, FaButton, FaCard, FaIcon, FaInput, FaPageHeader, FaPageMain, useFaToast } from '@yudream/components'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useEduroamLogin } from '../composables/useEduroamLogin'

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

function backToLogin() {
  router.push({ path: '/login' })
}

async function submit() {
  const payload = await model.submit(state.value)
  if (!payload?.success) {
    return
  }
  toast.success('Eduroam 认证通过，正在完成登录…')
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
      description="用校园网 / Eduroam 账号密码登录。认证通过后由本站完成账号绑定与会话签发。"
    >
      <FaButton variant="outline" @click="backToLogin">
        <FaIcon name="i-ri:arrow-go-back-line" />
        返回登录
      </FaButton>
    </FaPageHeader>

    <FaPageMain>
      <FaCard class="eduroam-card">
        <p v-if="model.loading" class="eduroam-help">正在加载登录配置…</p>

        <FaAlert
          v-else-if="!model.enabled"
          variant="destructive"
          title="Eduroam 登录未开放"
          description="管理员尚未开启 Eduroam 登录，请改用其它方式登录或联系管理员。"
        />

        <div v-else class="eduroam-login">
          <FaAlert
            v-if="!fromLoginPage"
            title="请从登录页进入"
            description="本页需要携带登录会话参数。请回到登录页点击「Eduroam 认证」入口，或直接访问登录页重新发起。"
          />

          <div class="eduroam-field">
            <span class="eduroam-label">Eduroam 账号</span>
            <FaInput
              v-model="model.account"
              class="w-full"
              :placeholder="model.config?.accountHint || '学号 / 工号'"
              :disabled="model.submitting"
            />
            <small class="eduroam-help">{{ model.config?.accountHint }}</small>
          </div>

          <div class="eduroam-field">
            <span class="eduroam-label">Eduroam 密码</span>
            <FaInput
              v-model="model.password"
              type="password"
              class="w-full"
              placeholder="校园网 / Eduroam 密码"
              :disabled="model.submitting"
              @keydown.enter="submit"
            />
            <small class="eduroam-help">密码只用于本次认证，不会保存到任何地方。</small>
          </div>

          <FaAlert v-if="model.error" variant="destructive" title="登录未通过" :description="model.error" />

          <div class="eduroam-actions">
            <FaButton variant="outline" @click="tutorialOpen = !tutorialOpen">
              <FaIcon name="i-ri:question-line" />
              {{ tutorialOpen ? '收起教程' : '查看教程' }}
            </FaButton>
            <FaButton :loading="model.submitting" :disabled="!fromLoginPage" @click="submit">
              <FaIcon name="i-ri:login-box-line" />
              登录
            </FaButton>
          </div>

          <pre v-if="tutorialOpen" class="eduroam-tutorial">{{ model.config?.tutorialMarkdown }}</pre>
        </div>
      </FaCard>
    </FaPageMain>
  </section>
</template>
