<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import CandlestickChart from '../components/CandlestickChart.vue'
import { getStockBars, getStockDetail, syncStock, type BarDto, type StockDetailDto } from '../api/market'

const route = useRoute()

const symbol = computed(() => String(route.params.symbol || '').toUpperCase())

const loading = ref(false)
const syncing = ref(false)
const detail = ref<StockDetailDto | null>(null)
const bars = ref<BarDto[]>([])

const title = computed(() => `${symbol.value} 日K`)

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
  loadAll()
}

function setAll() {
  const e = new Date()
  e.setDate(e.getDate() - 1)
  end.value = ymd(e)
  start.value = '2016-01-01'
  loadAll()
}

function updateChartHeight() {
  chartHeight.value = clamp(Math.floor(window.innerHeight - 360), 520, 900)
}

function hasValue(val: any) {
  if (val === null || val === undefined) return false
  const s = String(val).trim()
  return s !== '' && s !== '-' && s !== 'NaN'
}

const isSp500 = computed(() => {
  return detail.value?.identifiers?.some(i => i.provider === 'stooq' && !i.identifier.endsWith('.HK'))
})

async function loadAll() {
  if (!symbol.value) return
  loading.value = true
  try {
    detail.value = await getStockDetail(symbol.value)
    bars.value = await getStockBars(symbol.value, {
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
    const job = await syncStock(symbol.value)
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
  loadAll()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateChartHeight)
})

watch(symbol, () => loadAll())
</script>

<template>
  <el-space direction="vertical" style="width: 100%" :size="16" fill>
    <el-card shadow="never" style="border-radius: 12px" v-loading="loading">
      <div style="display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; flex-wrap: wrap">
        <div>
          <div style="font-size: 18px; font-weight: 700">{{ symbol }} - {{ detail?.name || '-' }}</div>
          <div v-if="hasValue(detail?.wikiDescription)" style="color: #667085; margin-top: 6px; font-size: 14px; line-height: 1.5">
            {{ detail?.wikiDescription }}
          </div>
          <div style="display: flex; gap: 8px; align-items: center; margin-top: 12px; flex-wrap: wrap">
            <el-date-picker v-model="start" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" style="width: 150px" />
            <el-date-picker v-model="end" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" style="width: 150px" />
            <el-button :loading="loading" @click="loadAll">应用</el-button>
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

    <el-row :gutter="16" style="width: 100%">
      <el-col :xs="24" :md="14">
        <el-card shadow="never" style="border-radius: 12px" :body-style="{ padding: '0' }">
          <div v-loading="loading" :style="{ height: `${chartHeight}px`, overflow: 'hidden' }">
            <CandlestickChart :bars="bars" :title="title" :height="chartHeight" />
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="10">
        <el-card shadow="never" style="border-radius: 12px" v-loading="loading">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="代码">{{ detail?.symbol || symbol }}</el-descriptions-item>
            <el-descriptions-item label="公司" v-if="hasValue(detail?.name)">{{ detail?.name }}</el-descriptions-item>
            <el-descriptions-item label="行业" v-if="hasValue(detail?.gicsSector)">{{ detail?.gicsSector }}</el-descriptions-item>
            <el-descriptions-item label="子行业" v-if="hasValue(detail?.gicsSubIndustry)">{{ detail?.gicsSubIndustry }}</el-descriptions-item>
            <el-descriptions-item label="总部" v-if="hasValue(detail?.headquarters)">{{ detail?.headquarters }}</el-descriptions-item>
            <el-descriptions-item :label="isSp500 ? '加入标普500' : '纳入指数日期'" v-if="hasValue(detail?.dateFirstAdded)">
              {{ detail?.dateFirstAdded }}
            </el-descriptions-item>
            <el-descriptions-item label="CIK" v-if="hasValue(detail?.cik)">{{ detail?.cik }}</el-descriptions-item>
            <el-descriptions-item label="Founded" v-if="hasValue(detail?.founded)">{{ detail?.founded }}</el-descriptions-item>
            <el-descriptions-item label="Wikipedia" v-if="hasValue(detail?.wikiUrl)">
              <a :href="detail!.wikiUrl!" target="_blank" rel="noreferrer">{{ detail?.wikiTitle || detail?.wikiUrl }}</a>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card v-if="detail?.identifiers && detail.identifiers.length > 0" shadow="never" style="border-radius: 12px; margin-top: 16px" v-loading="loading">
          <div style="font-weight: 700; margin-bottom: 8px">标识符</div>
          <el-table :data="detail?.identifiers || []" size="small" style="width: 100%">
            <el-table-column prop="provider" label="provider" width="120" />
            <el-table-column prop="identifier" label="identifier" show-overflow-tooltip />
          </el-table>
        </el-card>

        <el-card v-if="hasValue(detail?.wikiExtract)" shadow="never" style="border-radius: 12px; margin-top: 16px" v-loading="loading">
          <div style="font-weight: 700; margin-bottom: 8px">摘要</div>
          <div style="color: #667085; white-space: pre-wrap; line-height: 1.6">
            {{ detail?.wikiExtract }}
          </div>
        </el-card>
      </el-col>
    </el-row>
  </el-space>
</template>
