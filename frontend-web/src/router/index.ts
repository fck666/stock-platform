import { createRouter, createWebHistory } from 'vue-router'
import { auth } from '../auth/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: () => import('../views/HomeView.vue') },
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
    { path: '/market', name: 'market', component: () => import('../views/MarketView.vue') },
    { path: '/stocks', name: 'stocks', component: () => import('../views/StocksView.vue') },
    { path: '/indices', name: 'indices', component: () => import('../views/IndicesView.vue') },
    { path: '/plans', name: 'plans', component: () => import('../views/PlansView.vue') },
    { path: '/alerts', name: 'alerts', component: () => import('../views/AlertsView.vue') },
    { path: '/stocks/:symbol', name: 'stockDetail', component: () => import('../views/StockDetailView.vue') },
    { path: '/analysis', name: 'analysis', component: () => import('../views/AnalysisView.vue') },
    { path: '/sync', name: 'sync', component: () => import('../views/SyncView.vue'), meta: { requiresPermission: 'data.sync.execute' } },
  ],
})

router.beforeEach((to) => {
  if (to.path === '/login') return true
  const required = (to.meta as any)?.requiresPermission as string | undefined
  if (!required) return true
  if (!auth.isLoggedIn.value) {
    return { path: '/login', query: { next: to.fullPath } }
  }
  if (!auth.hasPermission(required)) {
    return { path: '/' }
  }
  return true
})

export default router
