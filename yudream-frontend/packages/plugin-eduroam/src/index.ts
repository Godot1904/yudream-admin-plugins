import { defineYuDreamPlugin } from '@yudream/plugin-sdk'
import 'virtual:uno.css'
import './styles.css'
import AdminAccountsPage from './pages/AdminAccountsPage.vue'
import AdminAttemptsPage from './pages/AdminAttemptsPage.vue'
import AdminSettingsPage from './pages/AdminSettingsPage.vue'
import LoginPage from './pages/LoginPage.vue'

/**
 * Eduroam 第三方登录插件的前端远程模块。
 *
 * <p>`Public` 是 /eduroam 凭据页：宿主登录页点击「Eduroam 认证」后整页跳到这里，认证成功后本页
 * 带票据回宿主 `/external-login/callback`，由宿主统一完成绑定与会话签发。
 */
export const Public = LoginPage
export const AdminAccounts = AdminAccountsPage
export const AdminAttempts = AdminAttemptsPage
export const AdminSettings = AdminSettingsPage

export const routes = {
  Public,
  AdminAccounts,
  AdminAttempts,
  AdminSettings,
  'eduroam/Public': Public,
  'eduroam/AdminAccounts': AdminAccounts,
  'eduroam/AdminAttempts': AdminAttempts,
  'eduroam/AdminSettings': AdminSettings,
}

export default defineYuDreamPlugin({
  routes,
  default: Public,
})
