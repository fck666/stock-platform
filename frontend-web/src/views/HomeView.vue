<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import CandlestickChart from '../components/CandlestickChart.vue'
import { type BarDto, getSp500Bars, syncSp500Index } from '../api/market'

const loading = ref(false)
const syncing = ref(false)
const bars = ref<BarDto[]>([])

const title = computed(() => 'S&P 500 (^SPX) 日K')

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

function setRangeYears(years: number) {
  const e = end.value ? new Date(end.value) : new Date()
  if (!end.value) e.setDate(e.getDate() - 1)
  const s = new Date(e)
  s.setFullYear(s.getFullYear() - years)
  end.value = ymd(e)
  start.value = ymd(s)
  load()
}

function setAll() {
  const e = new Date()
  e.setDate(e.getDate() - 1)
  end.value = ymd(e)
  start.value = '2016-01-01'
  load()
}

function updateChartHeight() {
  chartHeight.value = clamp(Math.floor(window.innerHeight - 320), 520, 900)
}

async function load() {
  loading.value = true
  try {
    bars.value = await getSp500Bars({
      interval: interval.value,
      start: start.value || undefined,
      end: end.value || undefined,
    })
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? '加载失败')
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
    <el-card shadow="never" style="border-radius: 12px">
      <div style="display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; flex-wrap: wrap">
        <div>
          <div style="font-size: 16px; font-weight: 700">{{ title }}</div>
          <div style="color: #667085; margin-top: 4px">上方为日K线，下方为成交量。支持区间调整与缩放。</div>
          <div style="display: flex; gap: 8px; align-items: center; margin-top: 10px; flex-wrap: wrap">
            <el-date-picker v-model="start" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" style="width: 150px" />
            <el-date-picker v-model="end" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" style="width: 150px" />
            <el-button :loading="loading" @click="load">应用</el-button>
            <el-button @click="setRangeYears(1)">1Y</el-button>
            <el-button @click="setRangeYears(2)">2Y</el-button>
            <el-button @click="setRangeYears(5)">5Y</el-button>
            <el-button @click="setAll">ALL</el-button>
          </div>
        </div>
        <el-space>
          <el-button type="primary" :loading="syncing" @click="triggerSync">同步数据</el-button>
        </el-space>
      </div>
    </el-card>

    <el-card shadow="never" style="border-radius: 12px" :body-style="{ padding: '0' }">
      <div v-loading="loading" :style="{ height: `${chartHeight}px`, overflow: 'hidden' }">
        <CandlestickChart :bars="bars" :title="title" :height="chartHeight" />
      </div>
    </el-card>
  </el-space>
</template>
