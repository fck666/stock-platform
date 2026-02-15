<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'

const router = useRouter()
</script>

<template>
  <div class="forbidden-page">
    <div class="content">
      <h1 class="code">403</h1>
      <h2 class="title">权限不足</h2>
      <p class="desc">您没有权限访问此页面。</p>
      <el-button type="primary" :icon="ArrowLeft" @click="router.push('/')">返回首页</el-button>
    </div>
  </div>
</template>

<style scoped>
.forbidden-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--el-bg-color);
}

.content {
  text-align: center;
}

.code {
  font-size: 72px;
  font-weight: 800;
  color: var(--el-color-danger);
  margin: 0;
  line-height: 1;
}

.title {
  font-size: 24px;
  margin: 16px 0 8px;
  color: var(--el-text-color-primary);
}

.desc {
  color: var(--el-text-color-secondary);
  margin-bottom: 24px;
}
</style>
