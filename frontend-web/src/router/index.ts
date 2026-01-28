import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: () => import('../views/HomeView.vue') },
    { path: '/stocks', name: 'stocks', component: () => import('../views/StocksView.vue') },
    { path: '/stocks/:symbol', name: 'stockDetail', component: () => import('../views/StockDetailView.vue') },
    { path: '/sync', name: 'sync', component: () => import('../views/SyncView.vue') },
  ],
})

export default router

