<script setup lang="ts">
/**
 * StockDetailView
 * 
 * The main view for a single stock's detailed analysis.
 * Features:
 * - Interactive Candlestick Chart (Day/Week/Month/Year intervals)
 * - Technical Indicators (MA, MACD, KDJ) configurable via checkboxes
 * - Relative Strength (RS) analysis against a benchmark index
 * - Longest Streak analysis (Consecutive Up/Down days)
 * - Corporate Actions (Dividends, Splits) visualization
 * - Fundamental Data (Market Cap, Sector, Description)
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import CandlestickChart from '../components/CandlestickChart.vue'
import LineChart from '../components/LineChart.vue'
import {
  getLongestStreakForSymbol,
  getRelativeStrength,
  listIndices,
  getStockBars,
  getStockDetail,
  getStockIndicators,
  syncStock,
  type BarDto,
  type IndicatorsResponseDto,
  type StockDetailDto,
  type IndexListItemDto,
  type RsSeriesDto,
  type StreakRankItemDto,
} from '../api/market'

const route = useRoute()

// --- Route & State ---
const symbol = computed(() => String(route.params.symbol || '').toUpperCase())

const loading = ref(false)
const syncing = ref(false)
const detail = ref<StockDetailDto | null>(null)
const bars = ref<BarDto[]>([])
const indicators = ref<IndicatorsResponseDto | null>(null)
const indicatorLoading = ref(false)

// --- Relative Strength State ---
const rsLoading = ref(false)
const rsSeries = ref<RsSeriesDto | null>(null)
const rsIndex = ref('^SPX') // Default benchmark
const indices = ref<IndexListItemDto[]>([])

// --- Streak State ---
const streakLoading = ref(false)
const streakUp = ref<StreakRankItemDto | null>(null)
const streakDown = ref<StreakRankItemDto | null>(null)

// --- Chart Configuration ---
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

/**
 * Sets default date range (past 2 years).
 */
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

/**
 * Adjusts chart height dynamically based on window size and selected indicators.
 * Each sub-indicator (MACD, KDJ) adds extra height.
 */
function updateChartHeight() {
  const base = clamp(Math.floor(window.innerHeight - 420), 520, 900)
  const extra = selectedSubIndicators.value.length * 180
  chartHeight.value = clamp(base + extra, 520, 1600)
}

function hasValue(val: any) {
  if (val === null || val === undefined) return false
  const s = String(val).trim()
  return s !== '' && s !== '-' && s !== 'NaN'
}

const isSp500 = computed(() => {
  return detail.value?.identifiers?.some(i => i.provider === 'stooq' && !i.identifier.endsWith('.HK'))
})

// --- Corporate Actions Visibility ---
const showAllCorporateActions = ref(false)
const corporateActionsVisible = computed(() => {
  const list = detail.value?.corporateActions || []
  return showAllCorporateActions.value ? list : list.slice(0, 10)
})
const hasMoreCorporateActions = computed(() => {
  const n = detail.value?.corporateActions?.length || 0
  return n > 10
})

/**
 * Master load function.
 * Fetches basic details, price bars, indicators, RS, and streaks in parallel where possible.
 */
async function loadAll() {
  if (!symbol.value) return
  document.title = `${symbol.value} - Stock Platform`
  loading.value = true
  try {
    detail.value = await getStockDetail(symbol.value)
    showAllCorporateActions.value = false
    // Fetch bars first as indicators depend on range, though here we use same range params
    bars.value = await getStockBars(symbol.value, {
      interval: interval.value,
      start: start.value || undefined,
      end: end.value || undefined,
    })
    await loadIndicators()
    await loadRelativeStrength()
    await loadStreaks()
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
    const raw = localStorage.getItem('stock_platform_indicator_config')
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
      'stock_platform_indicator_config',
      JSON.stringify({ mas: selectedMas.value, subs: selectedSubIndicators.value }),
    )
  } catch {}
}

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
async function loadIndices() {
  try {
    indices.value = await listIndices()
    if (indices.value.length > 0 && !indices.value.find(i => i.symbol === rsIndex.value)) {
      rsIndex.value = indices.value[0]?.symbol || '^SPX'
    }
  } catch {}
}

async function loadRelativeStrength() {
  if (!symbol.value) return
  rsLoading.value = true
  try {
    rsSeries.value = await getRelativeStrength({
      symbol: symbol.value,
      index: rsIndex.value,
      start: start.value || undefined,
      end: end.value || undefined,
    })
  } catch (e: any) {
    rsSeries.value = null
  } finally {
    rsLoading.value = false
  }
}

async function loadStreaks() {
  if (!symbol.value) return
  const itv = interval.value === '1d' || interval.value === '1w' || interval.value === '1m' ? interval.value : '1d'
  streakLoading.value = true
  try {
    const [up, down] = await Promise.all([
      getLongestStreakForSymbol(symbol.value, {
        interval: itv,
        direction: 'up',
        start: start.value || undefined,
        end: end.value || undefined,
      }),
      getLongestStreakForSymbol(symbol.value, {
        interval: itv,
        direction: 'down',
        start: start.value || undefined,
        end: end.value || undefined,
      }),
    ])
    streakUp.value = up
    streakDown.value = down
  } catch {
    streakUp.value = null
    streakDown.value = null
  } finally {
    streakLoading.value = false
  }
}

const rsDates = computed(() => rsSeries.value?.points?.map(p => p.date) || [])
const rsNorm = computed(() => rsSeries.value?.points?.map(p => (p.rsNormalized == null ? null : Number(p.rsNormalized))) || [])

const chartMarkers = computed(() => {
  const actions = detail.value?.corporateActions || []
  const markers: Array<{ date: string; type: 'DIVIDEND' | 'SPLIT'; label: string }> = []
  for (const a of actions) {
    const t = String(a.actionType || '')
    if (t !== 'DIVIDEND' && t !== 'SPLIT') continue
    const d = String(a.exDate || '').trim()
    if (!d) continue
    let label = ''
    if (t === 'DIVIDEND') {
      label = a.cashAmount != null ? `${a.cashAmount}${a.currency ? ' ' + a.currency : ''}` : 'DIV'
    } else {
      label = a.splitNumerator && a.splitDenominator ? `${a.splitNumerator}:${a.splitDenominator}` : 'SPL'
    }
    markers.push({ date: d, type: t as any, label })
  }
  return markers
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
  loadIndices()
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
watch(rsIndex, () => loadRelativeStrength())
</script>

<template>
  <el-space direction="vertical" class="page" :size="16" fill>
    <el-card shadow="never" style="border-radius: 12px" v-loading="loading">
      <div style="display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; flex-wrap: wrap">
        <div>
          <div style="font-size: 18px; font-weight: 700">
            <span class="font-mono">{{ symbol }}</span>
            <span style="font-weight: 400; margin-left: 8px">{{ detail?.name || '-' }}</span>
          </div>
          <div v-if="hasValue(detail?.wikiDescription)" class="text-muted" style="margin-top: 6px; font-size: 14px; line-height: 1.5">
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

    <!-- Top Section: Chart & Market Analysis -->
    <el-row :gutter="16" style="width: 100%">
      <el-col :xs="24" :md="16">
        <el-card shadow="never" style="border-radius: 12px; margin-bottom: 16px" v-loading="indicatorLoading">
          <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap">
            <div style="font-weight: 700">指标</div>
            <el-space wrap>
              <el-checkbox-group v-model="selectedMas">
                <el-checkbox :value="20">MA20</el-checkbox>
                <el-checkbox :value="60">MA60</el-checkbox>
                <el-checkbox :value="180">MA180</el-checkbox>
                <el-checkbox :value="360">MA360</el-checkbox>
              </el-checkbox-group>
              <el-checkbox-group v-model="selectedSubIndicators">
                <el-checkbox value="macd">MACD</el-checkbox>
                <el-checkbox value="kdj">KDJ</el-checkbox>
              </el-checkbox-group>
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
              :markers="chartMarkers"
              :sub-indicators="selectedSubIndicators"
              :macd="macdSeries"
              :kdj="kdjSeries"
            />
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="never" style="border-radius: 12px; margin-bottom: 16px" v-loading="rsLoading">
          <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap">
            <div style="font-weight: 700">相对强弱 (RS)</div>
            <el-space wrap>
              <el-select v-model="rsIndex" size="small" style="width: 180px">
                <el-option v-for="idx in indices" :key="idx.symbol" :label="idx.name || idx.symbol" :value="idx.symbol" />
              </el-select>
              <el-button size="small" @click="loadRelativeStrength" :loading="rsLoading">刷新</el-button>
            </el-space>
          </div>
          <div class="text-muted" style="font-size: 12px; margin-top: 6px">
            RS 相对收益：{{ rsSeries?.rsReturnPct == null ? '-' : (rsSeries.rsReturnPct * 100).toFixed(2) + '%' }}
            （股票 {{ rsSeries?.stockReturnPct == null ? '-' : (rsSeries.stockReturnPct * 100).toFixed(2) + '%' }}，
            指数 {{ rsSeries?.indexReturnPct == null ? '-' : (rsSeries.indexReturnPct * 100).toFixed(2) + '%' }}）
          </div>
          <div style="margin-top: 12px">
            <LineChart
              :title="`RS（归一化） vs ${rsIndex}`"
              :dates="rsDates"
              :series="[{ name: 'RS', data: rsNorm, color: 'var(--app-accent)' }]"
              :height="220"
            />
          </div>
        </el-card>

        <el-card shadow="never" style="border-radius: 12px; margin-bottom: 16px" v-loading="streakLoading">
          <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap">
            <div style="font-weight: 700">连涨/连跌（范围内最长）</div>
            <el-button size="small" @click="loadStreaks" :loading="streakLoading">刷新</el-button>
          </div>
          <div class="text-muted" style="font-size: 12px; margin-top: 6px">
            统计口径：相邻两根K线收盘价比较，连续上涨/下跌的周期数（不含平盘）。
          </div>
          <el-row :gutter="12" style="margin-top: 12px">
            <el-col :span="12">
              <el-card shadow="never" style="border-radius: 12px; background: var(--el-fill-color-lighter)">
                <div class="text-muted" style="font-size: 12px">最长连涨</div>
                <div style="font-size: 22px; font-weight: 800; margin-top: 6px; color: var(--app-success)">
                  {{ streakUp?.streak ?? '-' }}
                </div>
                <div class="text-muted" style="font-size: 12px; margin-top: 6px">
                  {{ streakUp?.startDate || '-' }} → {{ streakUp?.endDate || '-' }}
                </div>
              </el-card>
            </el-col>
            <el-col :span="12">
              <el-card shadow="never" style="border-radius: 12px; background: var(--el-fill-color-lighter)">
                <div class="text-muted" style="font-size: 12px">最长连跌</div>
                <div style="font-size: 22px; font-weight: 800; margin-top: 6px; color: var(--app-danger)">
                  {{ streakDown?.streak ?? '-' }}
                </div>
                <div class="text-muted" style="font-size: 12px; margin-top: 6px">
                  {{ streakDown?.startDate || '-' }} → {{ streakDown?.endDate || '-' }}
                </div>
              </el-card>
            </el-col>
          </el-row>
        </el-card>
      </el-col>
    </el-row>

    <!-- Bottom Section: Info & Events -->
    <el-row :gutter="16" style="width: 100%">
      <el-col :xs="24" :md="12">
        <el-card shadow="never" style="border-radius: 12px" v-loading="loading">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="代码">
              <span class="font-mono">{{ detail?.symbol || symbol }}</span>
            </el-descriptions-item>
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

        <el-card v-if="hasValue(detail?.wikiExtract)" shadow="never" style="border-radius: 12px; margin-top: 16px" v-loading="loading">
          <div style="font-weight: 700; margin-bottom: 8px">摘要</div>
          <div class="text-muted" style="white-space: pre-wrap; line-height: 1.6">
            {{ detail?.wikiExtract }}
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card v-if="detail?.corporateActions && detail.corporateActions.length > 0" shadow="never" style="border-radius: 12px; margin-bottom: 16px" v-loading="loading">
          <div style="font-weight: 700; margin-bottom: 8px">分红与拆股</div>
          <div style="height: 320px; overflow: hidden">
            <el-table :data="corporateActionsVisible" size="small" style="width: 100%" max-height="280">
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
          </div>
          <div v-if="hasMoreCorporateActions" style="margin-top: 8px; display: flex; justify-content: flex-end">
            <el-button link type="primary" @click="showAllCorporateActions = !showAllCorporateActions">
              {{ showAllCorporateActions ? '收起' : `展开全部（${detail?.corporateActions?.length || 0}条）` }}
            </el-button>
          </div>
        </el-card>

        <el-card v-if="detail?.identifiers && detail.identifiers.length > 0" shadow="never" style="border-radius: 12px" v-loading="loading">
          <div style="font-weight: 700; margin-bottom: 8px">标识符</div>
          <el-table :data="detail?.identifiers || []" size="small" style="width: 100%">
            <el-table-column prop="provider" label="provider" width="120" />
            <el-table-column prop="identifier" label="identifier" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </el-space>
</template>
