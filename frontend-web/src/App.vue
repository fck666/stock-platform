<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const active = computed(() => {
  if (route.path.startsWith('/stocks')) return '/stocks'
  if (route.path.startsWith('/analysis')) return '/analysis'
  if (route.path.startsWith('/sync')) return '/sync'
  return '/'
})

function handleSelect(index: string) {
  router.push(index)
}
</script>

<template>
  <el-container style="min-height: 100vh">
    <el-header style="display: flex; align-items: center; gap: 16px">
      <div style="font-weight: 700; font-size: 16px">Stock Platform</div>
      <el-menu mode="horizontal" :default-active="active" @select="handleSelect" style="flex: 1">
        <el-menu-item index="/">首页</el-menu-item>
        <el-menu-item index="/stocks">股票列表</el-menu-item>
        <el-menu-item index="/analysis">股票分析</el-menu-item>
        <el-menu-item index="/sync">数据同步</el-menu-item>
      </el-menu>
    </el-header>
    <el-main style="padding: 16px; background: #f6f7fb">
      <router-view />
    </el-main>
  </el-container>
</template>
