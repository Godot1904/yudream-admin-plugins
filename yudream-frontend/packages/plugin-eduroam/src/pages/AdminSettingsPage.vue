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
      title="域名配置"
      description="对齐原 auth-eduroam 的 EDUROAM_HOST 与 EDUROAM_STORE_HOST：认证域用于登录上游；本站邮箱域只决定本插件的记录口径，不改动站点账号规则。"
    >
      <div class="eduroam-grid">
        <label class="eduroam-field">
          <span class="eduroam-label">Eduroam 认证域</span>
          <FaInput v-model="model.form.eduDomain" class="w-full" placeholder="例如 example.edu.cn" />
          <small class="eduroam-help">
            填写后学生只需输入学号，系统自动补 @域名，并拒绝其他域名的账号；留空则要求填写完整账号。
          </small>
        </label>
        <label class="eduroam-field">
          <span class="eduroam-label">本站邮箱域（可选）</span>
          <FaInput v-model="model.form.storeDomain" class="w-full" placeholder="例如 mail.example.edu.cn" />
          <small class="eduroam-help">
            学校无线域与邮箱域不同时填写，用来把认证账号映射成本站邮箱。它只影响本插件：登录账号按该域名登记与展示，并据此在账号列表里标注该邮箱是否已有站内账号；留空则与认证域一致。本站不会因此自动注册或自动绑定账号。
          </small>
        </label>
      </div>
      <FaAlert
        v-if="!model.form.eduDomain"
        class="mt-3"
        title="当前不限成员院校"
        description="未填写认证域时，任何能通过 Eduroam 认证的账号都可以登录。只想允许本校成员时请填写认证域。"
      />
    </FaCard>

    <FaCard class="mt-4" title="上游认证服务" description="登录时向该地址提交账号密码并解析返回结果。">
      <label class="eduroam-field">
        <span class="eduroam-label">认证服务地址</span>
        <FaInput v-model="model.form.verifyEndpoint" class="w-full" placeholder="https://.../cgi-bin/eduroam-test.cgi" />
        <small class="eduroam-help">必须是 http(s) 绝对地址；留空或非法时回落到内置默认地址。</small>
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
      description="密码只在认证请求期间使用，不写入数据库与审计记录；登录交接票据一次性且 5 分钟内有效，并必须与登录会话匹配，重放无效。"
    />
  </FaPageMain>
</template>
