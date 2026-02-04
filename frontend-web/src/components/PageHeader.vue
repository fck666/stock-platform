<script setup lang="ts">
defineProps<{
  title: string
  subtitle?: string
}>()
</script>

<template>
  <el-card shadow="never" class="page-header">
    <div class="page-header__row">
      <div>
        <div class="page-header__title">{{ title }}</div>
        <div v-if="subtitle" class="page-header__subtitle">{{ subtitle }}</div>
      </div>
      <div class="page-header__actions">
        <slot />
      </div>
    </div>
  </el-card>
</template>

<style scoped>
.page-header {
  width: 100%;
}

.page-header__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.page-header__title {
  font-size: 16px;
  font-weight: 800;
}

.page-header__subtitle {
  margin-top: 4px;
  font-size: 13px;
  color: var(--app-muted);
}

.page-header__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
</style>

