<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import CandlestickChart from '../components/CandlestickChart.vue'
import { type BarDto, getIndexBars, getIndexIndicators, syncPrices, type IndicatorsResponseDto } from '../api/market'

const loading = ref(false)
const syncing = ref(false)
const bars = ref<BarDto[]>([])
const indicators = ref<IndicatorsResponseDto | null>(null)
const indicatorLoading = ref(false)

const indices = [
  { symbol: '^SPX', name: 'S&P 500' },
  { symbol: '^HSI', name: '恒生指数' },
  { symbol: '^HSTECH', name: '恒生科技' },
]
const activeIndex = ref('^SPX')

const title = computed(() => {
  const idx = indices.find(i => i.symbol === activeIndex.value)
  const label =
    interval.value === '1w' ? '周K' :
    interval.value === '1m' ? '月K' :
    interval.value === '1q' ? '季K' :
    interval.value === '1y' ? '年K' : '日K'
  return `${idx?.name || activeIndex.value} ${label}`
})

const interval = ref<'1d' | '1w' | '1m' | '1q' | '1y'>('1d')
const start = ref<string>('')
const end = ref<string>('')

const chartHeight = ref(600)
const selectedMas = ref<number[]>([])
const selectedSubIndicators = ref<Array<'macd' | 'kdj'>>([])

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
  const base = clamp(Math.floor(window.innerHeight - 360), 520, 900)
  const extra = selectedSubIndicators.value.length * 180
  chartHeight.value = clamp(base + extra, 520, 1600)
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
    await loadIndicators()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? '加载失败')
  } finally {
    loading.value = false
  }
}

function hasIndicatorsEnabled() {
  return selectedMas.value.length > 0 || selectedSubIndicators.value.length > 0
}

async function loadIndicators() {
  if (!hasIndicatorsEnabled()) {
    indicators.value = null
    return
  }
  if (!bars.value || bars.value.length === 0) {
    indicators.value = null
    return
  }
  indicatorLoading.value = true
  try {
    indicators.value = await getIndexIndicators(activeIndex.value, {
      interval: interval.value,
      start: start.value || undefined,
      end: end.value || undefined,
      ma: selectedMas.value.join(',') || undefined,
      include: selectedSubIndicators.value.join(',') || undefined,
    })
  } catch (e: any) {
    indicators.value = null
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? '指标计算失败')
  } finally {
    indicatorLoading.value = false
  }
}

function loadIndicatorConfig() {
  try {
    const raw = localStorage.getItem('stock_platform_indicator_config_index')
    if (!raw) return
    const parsed = JSON.parse(raw)
    const mas = Array.isArray(parsed?.mas) ? parsed.mas : []
    selectedMas.value = mas.map((x: any) => Number(x)).filter((x: any) => Number.isFinite(x))
    const subs = Array.isArray(parsed?.subs) ? parsed.subs : []
    selectedSubIndicators.value = subs
      .map((x: any) => String(x))
      .filter((x: any) => x === 'macd' || x === 'kdj')
  } catch {}
}

function saveIndicatorConfig() {
  try {
    localStorage.setItem(
      'stock_platform_indicator_config_index',
      JSON.stringify({ mas: selectedMas.value, subs: selectedSubIndicators.value }),
    )
  } catch {}
}

watch(activeIndex, load)
watch(interval, load)

watch(
  () => [selectedMas.value, selectedSubIndicators.value],
  () => {
    saveIndicatorConfig()
    updateChartHeight()
    loadIndicators()
  },
  { deep: true },
)

const indicatorByDate = computed(() => {
  const map = new Map<string, any>()
  const pts = indicators.value?.points || []
  for (const p of pts) {
    map.set(p.date, p)
  }
  return map
})

const maLines = computed(() => {
  const out: Record<string, Array<number | null>> = {}
  if (!hasIndicatorsEnabled()) return out
  for (const p of selectedMas.value) {
    const key = String(p)
    out[key] = bars.value.map((b) => {
      const pt = indicatorByDate.value.get(b.date)
      const ma = pt?.ma
      if (!ma) return null
      const v = ma[key]
      return v === null || v === undefined ? null : Number(v)
    })
  }
  return out
})

const macdSeries = computed(() => {
  if (!selectedSubIndicators.value.includes('macd')) return null
  return {
    dif: bars.value.map((b) => {
      const v = indicatorByDate.value.get(b.date)?.macd?.dif
      return v === null || v === undefined ? null : Number(v)
    }),
    dea: bars.value.map((b) => {
      const v = indicatorByDate.value.get(b.date)?.macd?.dea
      return v === null || v === undefined ? null : Number(v)
    }),
    hist: bars.value.map((b) => {
      const v = indicatorByDate.value.get(b.date)?.macd?.hist
      return v === null || v === undefined ? null : Number(v)
    }),
  }
})

const kdjSeries = computed(() => {
  if (!selectedSubIndicators.value.includes('kdj')) return null
  return {
    k: bars.value.map((b) => {
      const v = indicatorByDate.value.get(b.date)?.kdj?.k
      return v === null || v === undefined ? null : Number(v)
    }),
    d: bars.value.map((b) => {
      const v = indicatorByDate.value.get(b.date)?.kdj?.d
      return v === null || v === undefined ? null : Number(v)
    }),
    j: bars.value.map((b) => {
      const v = indicatorByDate.value.get(b.date)?.kdj?.j
      return v === null || v === undefined ? null : Number(v)
    }),
  }
})

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
  loadIndicatorConfig()
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
          <el-space wrap>
            <el-select v-model="interval" size="small" style="width: 110px">
              <el-option label="日线" value="1d" />
              <el-option label="周线" value="1w" />
              <el-option label="月线" value="1m" />
              <el-option label="季线" value="1q" />
              <el-option label="年线" value="1y" />
            </el-select>
            <el-button @click="load" :loading="loading">刷新</el-button>
            <el-button type="primary" @click="triggerSync" :loading="syncing">同步数据</el-button>
          </el-space>
        </div>
      </template>
      <div style="padding: 12px">
        <el-card shadow="never" style="border-radius: 12px" v-loading="indicatorLoading">
          <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap">
            <div style="font-weight: 700">指标</div>
            <el-space wrap>
              <el-checkbox-group v-model="selectedMas">
                <el-checkbox :label="20">MA20</el-checkbox>
                <el-checkbox :label="60">MA60</el-checkbox>
                <el-checkbox :label="180">MA180</el-checkbox>
                <el-checkbox :label="360">MA360</el-checkbox>
              </el-checkbox-group>
              <el-checkbox-group v-model="selectedSubIndicators">
                <el-checkbox label="macd">MACD</el-checkbox>
                <el-checkbox label="kdj">KDJ</el-checkbox>
              </el-checkbox-group>
            </el-space>
          </div>
        </el-card>
      </div>
      <div v-loading="loading" :style="{ height: `${chartHeight}px`, overflow: 'hidden' }">
        <CandlestickChart
          :bars="bars"
          :title="title"
          :height="chartHeight"
          :ma-lines="maLines"
          :sub-indicators="selectedSubIndicators"
          :macd="macdSeries"
          :kdj="kdjSeries"
        />
      </div>
    </el-card>
  </el-space>
</template>
