<script setup lang="ts">
import { FaAlert, FaButton, FaInput } from '@yudream/components'

defineProps<{ email: string, busy: boolean }>()
const emit = defineEmits<{ submit: [], bindExisting: [] }>()
const password = defineModel<string>('password', { required: true })
const confirmation = defineModel<string>('confirmation', { required: true })
</script>

<template>
  <form class="eduroam-login" @submit.prevent="emit('submit')">
    <FaAlert title="校园认证通过，请设置本站密码" description="本站账号将自动创建，无需重新填写学号或邮箱。" />
    <label class="eduroam-field">
      <span class="eduroam-label">本站登录邮箱</span>
      <FaInput :model-value="email" readonly class="w-full" />
      <small class="eduroam-help">已按本站邮箱后缀自动生成，也将作为本站用户名。</small>
    </label>
    <label class="eduroam-field">
      <span class="eduroam-label">新的本站密码</span>
      <FaInput v-model="password" type="password" autocomplete="new-password" class="w-full"
        placeholder="至少 8 个字符" :disabled="busy" />
      <small class="eduroam-help">请为本站单独设置密码。校园网密码不会自动成为本站密码。</small>
    </label>
    <label class="eduroam-field">
      <span class="eduroam-label">确认本站密码</span>
      <FaInput v-model="confirmation" type="password" autocomplete="new-password" class="w-full"
        placeholder="再次输入新的本站密码" :disabled="busy" />
    </label>
    <div class="eduroam-actions">
      <FaButton type="button" variant="outline" :disabled="busy" @click="emit('bindExisting')">
        已有本站账号，继续绑定
      </FaButton>
      <FaButton type="submit" :loading="busy" :disabled="busy">创建本站账号</FaButton>
    </div>
    <small class="eduroam-help">创建后需用新密码登录一次完成首次绑定，以后即可直接使用 Eduroam 登录。</small>
  </form>
</template>
