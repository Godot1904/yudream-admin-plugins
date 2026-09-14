import { defineYuDreamPlugin } from '@yudream/plugin-sdk'
import 'virtual:uno.css'
import './styles.css'
import PointsMallPlugin from './PointsMallPlugin.vue'

export const Mall = PointsMallPlugin
export const MyRedemptions = PointsMallPlugin
export const AdminItems = PointsMallPlugin
export const AdminRedemptions = PointsMallPlugin
export const AdminSettings = PointsMallPlugin

export const routes = {
  Mall,
  MyRedemptions,
  AdminItems,
  AdminRedemptions,
  AdminSettings,
  'points-mall/Mall': Mall,
  'points-mall/MyRedemptions': MyRedemptions,
  'points-mall/AdminItems': AdminItems,
  'points-mall/AdminRedemptions': AdminRedemptions,
  'points-mall/AdminSettings': AdminSettings,
}

export default defineYuDreamPlugin({
  routes,
  default: Mall,
})
