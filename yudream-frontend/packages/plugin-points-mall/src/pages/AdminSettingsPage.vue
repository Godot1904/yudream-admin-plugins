<script setup lang="ts">
import type { PointsMallModel } from '../composables/usePointsMall'
import { FaButton, FaCard, FaIcon, FaPageHeader, FaPageMain, FaSwitch, FaTextarea } from '@yudream/components'

const props = defineProps<{ model: PointsMallModel }>()
</script>

<template>
  <FaPageHeader title="商城设置" class="mb-0">
    <FaButton variant="outline" :loading="model.loading" @click="model.loadSettings()">
      <FaIcon name="i-ri:refresh-line" />
      重新加载
    </FaButton>
  </FaPageHeader>

  <FaPageMain>
    <div class="pm-settings">
      <FaCard>
        <div class="pm-form">
          <label class="pm-switch"><span>开放积分商城</span><FaSwitch v-model="model.settingsForm.enabled" /></label>
          <p class="pm-hint">关闭后用户端不能再提交兑换；已兑换记录的发放与确认收货不受影响。</p>

          <label>
            <span>兑换须知</span>
            <FaTextarea v-model="model.settingsForm.notice" class="w-full" placeholder="例如：兑换后请联系管理员领取，发放后请在「我的兑换」确认收到" />
          </label>

          <div class="pm-actions pm-actions-end">
            <FaButton variant="outline" @click="model.loadSettings()">放弃修改</FaButton>
            <FaButton :loading="model.saving" @click="model.saveSettings()">保存设置</FaButton>
          </div>
        </div>
      </FaCard>

      <FaCard>
        <strong>结算资产在商品上配置</strong>
        <p class="pm-hint">
          不同商品可以用不同的钱包资产结算（例如积分、活动代币）。结算资产在「商品管理」的新增/编辑弹窗里选择，
          候选来自钱包插件的资产列表；用户端概览会按在架商品用到的资产分别显示余额。
        </p>
        <p class="pm-hint">要新增资产或调整资产名称，请到钱包插件的币种管理里维护。</p>
      </FaCard>
    </div>
  </FaPageMain>
</template>
