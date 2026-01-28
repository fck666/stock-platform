<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import CandlestickChart from '../components/CandlestickChart.vue'
import { type BarDto, getIndexBars, syncPrices } from '../api/market'

const loading = ref(false)
const syncing = ref(false)
const bars = ref<BarDto[]>([])

const indices = [
  { symbol: '^SPX', name: 'S&P 500' },
  { symbol: '^HSI', name: '恒生指数' },
  { symbol: '^HSTECH', name: '恒生科技' },
]
const activeIndex = ref('^SPX')

const title = computed(() => {
  const idx = indices.find(i => i.symbol === activeIndex.value)
  return `${idx?.name || activeIndex.value} 日K`
})

const interval = ref<'1d'>('1d')
const start = ref<string>('')
const end = ref<string>('')

const chartHeight = ref(600)

function clamp(n: number, min: number, max: number) {
  return Math.min(Math.max(n, min), max)
}

function ymd(d: Date) {
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

function setDefaultRange() {
  const e = new Date()
  e.setDate(e.getDate() - 1)
  const s = new Date(e)
  s.setFullYear(s.getFullYear() - 2)
  end.value = ymd(e)
  start.value = ymd(s)
}

function updateChartHeight() {
  chartHeight.value = clamp(Math.floor(window.innerHeight - 320), 520, 900)
}

async function load() {
  loading.value = true
  try {
    bars.value = await getIndexBars(
      activeIndex.value,
      interval.value,
      start.value || undefined,
      end.value || undefined
    )
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? '加载失败')
  } finally {
    loading.value = false
  }
}

watch(activeIndex, load)

async function triggerSync() {
  syncing.value = true
  try {
    const job = await syncPrices(activeIndex.value)
    ElMessage.success(`价格同步任务已启动: ${job.jobId}`)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? '同步失败')
  } finally {
    syncing.value = false
  }
}

onMounted(() => {
  setDefaultRange()
  updateChartHeight()
  window.addEventListener('resize', updateChartHeight)
  load()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateChartHeight)
})
</script>

<template>
  <el-space direction="vertical" style="width: 100%" :size="16" fill>
    <el-tabs v-model="activeIndex" type="card" class="index-tabs">
      <el-tab-pane v-for="idx in indices" :key="idx.symbol" :label="idx.name" :name="idx.symbol" />
    </el-tabs>

    <el-card shadow="never" style="border-radius: 12px" :body-style="{ padding: '0' }">
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px">
          <div>
            <div style="font-weight: 700; font-size: 16px">{{ title }}</div>
            <div style="font-size: 13px; color: #667085; margin-top: 4px">上方为日K线，下方为成交量。默认展示最近两年的数据。</div>
          </div>
          <el-space>
            <el-button @click="load" :loading="loading">刷新</el-button>
            <el-button type="primary" @click="triggerSync" :loading="syncing">同步数据</el-button>
          </el-space>
        </div>
      </template>
      <div v-loading="loading" :style="{ height: `${chartHeight}px`, overflow: 'hidden' }">
        <CandlestickChart :bars="bars" :title="title" :height="chartHeight" />
      </div>
    </el-card>
  </el-space>
</template>
