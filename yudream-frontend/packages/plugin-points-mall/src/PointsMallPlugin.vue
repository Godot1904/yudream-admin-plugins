<script setup lang="ts">
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { computed, onMounted, watch } from 'vue'
import { usePointsMall } from './composables/usePointsMall'
import type { PointsMallPage } from './composables/usePointsMall'
import AdminItemsPage from './pages/AdminItemsPage.vue'
import AdminRedemptionsPage from './pages/AdminRedemptionsPage.vue'
import AdminSettingsPage from './pages/AdminSettingsPage.vue'
import MallPage from './pages/MallPage.vue'
import MyRedemptionsPage from './pages/MyRedemptionsPage.vue'

const props = defineProps<{
  sdk: YuDreamPluginSdk
  route?: RouteLocationNormalizedLoaded
}>()

const model = usePointsMall(props.sdk)

const pageName = computed<PointsMallPage>(() => {
  const component = (props.route?.meta?.plugin as { component?: string } | undefined)?.component || ''
  if (component.endsWith('/MyRedemptions')) {
    return 'my-redemptions'
  }
  if (component.endsWith('/AdminItems')) {
    return 'admin-items'
  }
  if (component.endsWith('/AdminRedemptions')) {
    return 'admin-redemptions'
  }
  if (component.endsWith('/AdminSettings')) {
    return 'admin-settings'
  }
  return 'mall'
})

const page = computed(() => {
  if (pageName.value === 'my-redemptions') {
    return MyRedemptionsPage
  }
  if (pageName.value === 'admin-items') {
    return AdminItemsPage
  }
  if (pageName.value === 'admin-redemptions') {
    return AdminRedemptionsPage
  }
  if (pageName.value === 'admin-settings') {
    return AdminSettingsPage
  }
  return MallPage
})

onMounted(() => model.loadPage(pageName.value))
watch(pageName, value => model.loadPage(value))
</script>

<template>
  <div class="points-mall-plugin">
    <component :is="page" :model="model" />
  </div>
</template>
