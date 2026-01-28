<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import CandlestickChart from '../components/CandlestickChart.vue'
import { getStockBars, getStockDetail, getStockIndicators, syncStock, type BarDto, type IndicatorsResponseDto, type StockDetailDto } from '../api/market'

const route = useRoute()

const symbol = computed(() => String(route.params.symbol || '').toUpperCase())

const loading = ref(false)
const syncing = ref(false)
const detail = ref<StockDetailDto | null>(null)
const bars = ref<BarDto[]>([])
const indicators = ref<IndicatorsResponseDto | null>(null)
const indicatorLoading = ref(false)

const intervalLabel = computed(() => {
  if (interval.value === '1w') return '周K'
  if (interval.value === '1m') return '月K'
  if (interval.value === '1q') return '季K'
  if (interval.value === '1y') return '年K'
  return '日K'
})
const title = computed(() => `${symbol.value} ${intervalLabel.value}`)

const interval = ref<'1d' | '1w' | '1m' | '1q' | '1y'>('1d')
const start = ref<string>('')
const end = ref<string>('')
const chartHeight = ref(600)
const selectedMas = ref<number[]>([])
const subIndicator = ref<'none' | 'macd' | 'kdj'>('none')

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
    await loadIndicators()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? '加载失败')
  } finally {
    loading.value = false
  }
}

function hasIndicatorsEnabled() {
  return selectedMas.value.length > 0 || subIndicator.value !== 'none'
}

async function loadIndicators() {
  if (!symbol.value) return
  if (!bars.value || bars.value.length === 0) {
    indicators.value = null
    return
  }
  if (!hasIndicatorsEnabled()) {
    indicators.value = null
    return
  }
  indicatorLoading.value = true
  try {
    indicators.value = await getStockIndicators(symbol.value, {
      interval: interval.value,
      start: start.value || undefined,
      end: end.value || undefined,
      ma: selectedMas.value.join(',') || undefined,
      include: subIndicator.value !== 'none' ? subIndicator.value : undefined,
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
    const raw = localStorage.getItem('stock_platform_indicator_config')
    if (!raw) return
    const parsed = JSON.parse(raw)
    const mas = Array.isArray(parsed?.mas) ? parsed.mas : []
    selectedMas.value = mas.map((x: any) => Number(x)).filter((x: any) => Number.isFinite(x))
    const sub = String(parsed?.sub || 'none') as any
    subIndicator.value = sub === 'macd' || sub === 'kdj' ? sub : 'none'
  } catch {}
}

function saveIndicatorConfig() {
  try {
    localStorage.setItem(
      'stock_platform_indicator_config',
      JSON.stringify({ mas: selectedMas.value, sub: subIndicator.value }),
    )
  } catch {}
}

watch(
  () => [selectedMas.value, subIndicator.value],
  () => {
    saveIndicatorConfig()
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
  if (subIndicator.value !== 'macd') return null
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
  if (subIndicator.value !== 'kdj') return null
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
    const job = await syncStock(symbol.value)
    ElMessage.success(`已触发同步任务：${job.jobId}`)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? '同步失败')
  } finally {
    syncing.value = false
  }
}

onMounted(() => {
  loadIndicatorConfig()
  setDefaultRange()
  updateChartHeight()
  window.addEventListener('resize', updateChartHeight)
  loadAll()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateChartHeight)
})

watch(symbol, () => loadAll())
watch(interval, () => loadAll())
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
            <el-select v-model="interval" size="small" style="width: 110px">
              <el-option label="日线" value="1d" />
              <el-option label="周线" value="1w" />
              <el-option label="月线" value="1m" />
              <el-option label="季线" value="1q" />
              <el-option label="年线" value="1y" />
            </el-select>
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
              <el-select v-model="subIndicator" size="small" style="width: 120px">
                <el-option label="无副图" value="none" />
                <el-option label="MACD" value="macd" />
                <el-option label="KDJ" value="kdj" />
              </el-select>
            </el-space>
          </div>
        </el-card>

        <el-card shadow="never" style="border-radius: 12px" :body-style="{ padding: '0' }">
          <div v-loading="loading" :style="{ height: `${chartHeight}px`, overflow: 'hidden' }">
            <CandlestickChart
              :bars="bars"
              :title="title"
              :height="chartHeight"
              :ma-lines="maLines"
              :sub-indicator="subIndicator"
              :macd="macdSeries"
              :kdj="kdjSeries"
            />
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
            <el-descriptions-item label="总股数" v-if="detail?.sharesOutstanding">{{ detail.sharesOutstanding.toLocaleString() }}</el-descriptions-item>
            <el-descriptions-item label="流通股数" v-if="detail?.floatShares">{{ detail.floatShares.toLocaleString() }}</el-descriptions-item>
            <el-descriptions-item label="市值" v-if="detail?.marketCap">
              {{ detail.currency ? `${detail.currency} ` : '' }}{{ detail.marketCap.toLocaleString() }}
            </el-descriptions-item>
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

        <el-card v-if="detail?.corporateActions && detail.corporateActions.length > 0" shadow="never" style="border-radius: 12px; margin-top: 16px" v-loading="loading">
          <div style="font-weight: 700; margin-bottom: 8px">分红与拆股</div>
          <el-table :data="detail?.corporateActions || []" size="small" style="width: 100%">
            <el-table-column prop="exDate" label="日期" width="100" />
            <el-table-column prop="actionType" label="类型" width="80" />
            <el-table-column label="金额/比例" width="120">
              <template #default="{ row }">
                <span v-if="row.actionType === 'DIVIDEND'">
                  {{ row.cashAmount ?? '-' }}
                </span>
                <span v-else>
                  {{ row.splitNumerator && row.splitDenominator ? `${row.splitNumerator}:${row.splitDenominator}` : '-' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="source" label="来源" width="80" />
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
