import { createRouter, createWebHistory } from 'vue-router'
import { auth } from '../auth/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: () => import('../views/HomeView.vue'), meta: { title: '首页' } },
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { title: '登录' } },
    { path: '/market', name: 'market', component: () => import('../views/MarketView.vue'), meta: { title: '市场概览' } },
    { path: '/stocks', name: 'stocks', component: () => import('../views/StocksView.vue'), meta: { title: '股票列表' } },
    { path: '/indices', name: 'indices', component: () => import('../views/IndicesView.vue'), meta: { requiresPermission: 'admin.index.write', title: '指数管理' } },
    { path: '/plans', name: 'plans', component: () => import('../views/PlansView.vue'), meta: { title: '交易计划' } },
    { path: '/alerts', name: 'alerts', component: () => import('../views/AlertsView.vue'), meta: { title: '告警' } },
    { path: '/stocks/:symbol', name: 'stockDetail', component: () => import('../views/StockDetailView.vue'), meta: { title: '股票详情' } },
    { path: '/analysis', name: 'analysis', component: () => import('../views/AnalysisView.vue'), meta: { title: '股票分析' } },
    { path: '/sync', name: 'sync', component: () => import('../views/SyncView.vue'), meta: { requiresPermission: 'data.sync.execute', title: '数据同步' } },
    { path: '/admin/users', name: 'adminUsers', component: () => import('../views/admin/UserManagementView.vue'), meta: { requiresPermission: 'iam.manage', title: '用户管理' } },
    { path: '/profile', name: 'profile', component: () => import('../views/profile/UserProfileView.vue'), meta: { title: '个人设置' } },
    { path: '/403', name: 'forbidden', component: () => import('../views/ForbiddenView.vue'), meta: { title: '权限不足' } },
  ],
})

router.afterEach((to) => {
  let title = (to.meta.title as string) || ''
  if (to.name === 'stockDetail' && to.params.symbol) {
    title = String(to.params.symbol).toUpperCase()
  }
  document.title = title ? `${title} - Stock Platform` : 'Stock Platform'
})

router.beforeEach((to) => {
  if (to.path === '/login') return true
  const required = (to.meta as any)?.requiresPermission as string | undefined
  if (!required) return true
  if (!auth.ready.value) return true
  if (!auth.isLoggedIn.value) {
    return { path: '/login', query: { next: to.fullPath } }
  }
  if (!auth.hasPermission(required)) {
    return { path: '/403' }
  }
  return true
})

export default router
