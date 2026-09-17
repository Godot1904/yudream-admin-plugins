<script setup lang="ts">
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import {
  FaAlert,
  FaButton,
  FaCard,
  FaIcon,
  FaInput,
  FaNumberField,
  FaPageHeader,
  FaPageMain,
  FaSwitch,
  FaTextarea,
} from '@yudream/components'
import { onMounted } from 'vue'
import { useEduroamSettings } from '../composables/useEduroamSettings'

const props = defineProps<{ sdk: YuDreamPluginSdk }>()

const model = useEduroamSettings(props.sdk)

onMounted(model.load)
</script>

<template>
  <FaPageHeader title="Eduroam 登录设置" description="配置登录开关、账号域名与上游认证服务；保存后立即生效，登录页入口随之显隐。" class="mb-0">
    <div class="eduroam-actions">
      <FaButton variant="outline" :loading="model.loading" @click="model.load">
        <FaIcon name="i-ri:refresh-line" />
        重新加载
      </FaButton>
      <FaButton :loading="model.saving" @click="model.save">
        <FaIcon name="i-ri:save-3-line" />
        保存设置
      </FaButton>
    </div>
  </FaPageHeader>

  <FaPageMain>
    <FaCard title="登录开关" description="关闭后登录页不再显示「Eduroam 认证」入口，已有账号记录与封禁状态保留。">
      <label class="eduroam-switch">
        <span>开放 Eduroam 登录</span>
        <FaSwitch v-model="model.form.enabled" />
      </label>
    </FaCard>

    <FaCard
      class="mt-4"
      title="学校账号配置"
      description="统一配置学校的 Eduroam 账号后缀，用户登录时只需填写学号或工号。"
    >
      <div class="eduroam-grid">
        <label class="eduroam-field">
          <span class="eduroam-label">学校账号后缀（认证域）</span>
          <FaInput v-model="model.form.eduDomain" class="w-full" placeholder="例如 @xx.edu.cn" />
          <small class="eduroam-help">
            支持填写 @xx.edu.cn 或 xx.edu.cn。保存后登录框自动显示该后缀，用户无需再次输入；仅允许该学校域名的账号登录。
          </small>
          <small class="eduroam-help">
            例如配置 @xx.edu.cn 后，用户填写 20260001，实际认证账号为 20260001@xx.edu.cn。
          </small>
        </label>
        <label class="eduroam-field">
          <span class="eduroam-label">本站邮箱域（可选）</span>
          <FaInput v-model="model.form.storeDomain" class="w-full" placeholder="例如 mail.example.edu.cn" />
          <small class="eduroam-help">
            校园认证通过后，按「学号 + 此邮箱后缀」生成本站账号；不存在时要求用户设置新的本站密码并自动创建，已有账号不会覆盖密码。留空使用认证域。首次创建后需用新密码登录一次完成绑定。
          </small>
        </label>
      </div>
      <FaAlert
        v-if="!model.form.eduDomain"
        class="mt-3"
        title="尚未配置学校账号后缀"
        description="留空时不限成员院校，用户需要输入完整的 学号@学校域名；填写上方后缀即可省去这一步。"
      />
    </FaCard>

    <FaCard class="mt-4" title="上游认证服务" description="登录时向该地址提交账号密码并解析返回结果。">
      <label class="eduroam-field">
        <span class="eduroam-label">认证服务地址</span>
        <FaInput v-model="model.form.verifyEndpoint" class="w-full" placeholder="https://analysis.eduroam.edu.cn/checkc/pkudetection" />
        <small class="eduroam-help">
          默认使用北京大学 Eduroam 探测点（MSCHAPv2）；留空或填写旧默认地址会自动使用新站点。其他自定义地址须兼容原有认证服务。
        </small>
      </label>
      <div class="eduroam-grid three mt-3">
        <label class="eduroam-field">
          <span class="eduroam-label">连接超时（秒）</span>
          <FaNumberField v-model="model.form.connectTimeoutSeconds" :min="2" :max="60" class="w-full" />
        </label>
        <label class="eduroam-field">
          <span class="eduroam-label">请求超时（秒）</span>
          <FaNumberField v-model="model.form.requestTimeoutSeconds" :min="5" :max="120" class="w-full" />
        </label>
        <label class="eduroam-field">
          <span class="eduroam-label">每 IP 每小时失败上限</span>
          <FaNumberField v-model="model.form.maxAttemptsPerHour" :min="1" :max="200" class="w-full" />
        </label>
      </div>
      <small class="eduroam-help">
        失败次数超限后该 IP 会被暂时拒绝；登录成功会立即清空计数，正常用户输错一两次不会被挡。
      </small>
    </FaCard>

    <FaCard class="mt-4" title="用户教程" description="凭据页「查看教程」展示的内容，支持纯文本换行。">
      <FaTextarea v-model="model.form.tutorialMarkdown" class="w-full" :rows="8" placeholder="填写校园网连接方式与账号说明" />
    </FaCard>

    <FaAlert
      class="mt-4"
      title="安全说明"
      description="校园网密码只用于上游认证；新设置的本站密码交由宿主加密保存，插件不保存两种密码。认证票据一次性且 5 分钟内有效，开户前重新检查账号封禁与邮箱配置。"
    />
  </FaPageMain>
</template>
