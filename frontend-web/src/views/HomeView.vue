<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import CandlestickChart from '../components/CandlestickChart.vue'
import { type BarDto, getSp500Bars, syncSp500Index } from '../api/market'

const loading = ref(false)
const syncing = ref(false)
const bars = ref<BarDto[]>([])

const title = computed(() => 'S&P 500 (^SPX) 日K')

async function load() {
  loading.value = true
  try {
    bars.value = await getSp500Bars({ interval: '1d' })
  } finally {
    loading.value = false
  }
}

async function triggerSync() {
  syncing.value = true
  try {
    const job = await syncSp500Index()
    ElMessage.success(`已触发同步任务：${job.jobId}`)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? '同步失败')
  } finally {
    syncing.value = false
  }
}

onMounted(() => {
  load()
})
</script>

<template>
  <el-space direction="vertical" style="width: 100%" :size="16">
    <el-card shadow="never" style="border-radius: 12px">
      <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
        <div>
          <div style="font-size: 16px; font-weight: 700">{{ title }}</div>
          <div style="color: #667085; margin-top: 4px">
            上方为日K线，下方为成交量。默认展示最近两年的数据。
          </div>
        </div>
        <el-space>
          <el-button :loading="loading" @click="load">刷新</el-button>
          <el-button type="primary" :loading="syncing" @click="triggerSync">同步数据</el-button>
        </el-space>
      </div>
    </el-card>

    <el-card shadow="never" style="border-radius: 12px; padding: 0">
      <div v-loading="loading" style="min-height: 520px">
        <CandlestickChart :bars="bars" :title="title" :height="520" />
      </div>
    </el-card>
  </el-space>
</template>
