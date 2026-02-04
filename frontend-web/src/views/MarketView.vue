<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import {
  getBreadth,
  getRelativeStrengthRank,
  listIndices,
  rankFactors,
  rankStreaks,
  runScreener,
  type BreadthSnapshotDto,
  type FactorRankItemDto,
  type IndexListItemDto,
  type RsRankItemDto,
  type ScreenerItemDto,
  type StreakRankItemDto,
} from '../api/market'

const loadingIndices = ref(false)
const indices = ref<IndexListItemDto[]>([])
const activeIndex = ref('^SPX')
const activeIndexName = computed(() => indices.value.find(i => i.symbol === activeIndex.value)?.name || activeIndex.value)

const activeTab = ref<'screener' | 'breadth'>('screener')

const screenerLoading = ref(false)
const breadthLoading = ref(false)

const preset = ref<
  | 'trend'
  | 'breakout'
  | 'rs'
  | 'streak'
  | 'drawdown_worst'
  | 'drawdown_best'
  | 'runup'
  | 'rundown'
  | 'new_high'
  | 'new_low'
>('trend')
const lookbackDays = ref(126)
const limit = ref(20)
const screenerRows = ref<ScreenerItemDto[]>([])
const rsRankRows = ref<RsRankItemDto[]>([])
const streakRows = ref<StreakRankItemDto[]>([])
const factorRows = ref<FactorRankItemDto[]>([])
const rsRequireAboveMa50 = ref(true)

const breadth = ref<BreadthSnapshotDto | null>(null)

const lookbackOptions = [
  { label: '3个月 (63)', value: 63 },
  { label: '6个月 (126)', value: 126 },
  { label: '12个月 (252)', value: 252 },
]

function toYmd(d: Date) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function pct(n: number, d: number) {
  if (!d || d <= 0) return '0%'
  return `${((n / d) * 100).toFixed(1)}%`
}

const streakInterval = ref<'1d' | '1w' | '1m'>('1d')
const streakDirection = ref<'up' | 'down'>('up')
const streakDateRange = ref<[Date, Date] | null>(null)
const streakVolumeMultiple = ref<number>(0)
const streakFlatThresholdPct = ref<number>(0)

const factorInterval = ref<'1d' | '1w' | '1m'>('1d')
const factorDateRange = ref<[Date, Date] | null>(null)
const factorLookback = ref<number>(252)

async function loadIndices() {
  loadingIndices.value = true
  try {
    const list = await listIndices()
    indices.value = [{ symbol: 'ALL', name: '全市场', wikiUrl: null }, ...list]
    if (indices.value.length > 0 && !indices.value.find(i => i.symbol === activeIndex.value)) {
      activeIndex.value = indices.value[0]?.symbol || '^SPX'
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? '加载指数失败')
  } finally {
    loadingIndices.value = false
  }
}

async function refreshScreener() {
  screenerLoading.value = true
  try {
    if (preset.value === 'trend' || preset.value === 'breakout') {
      screenerRows.value = await runScreener({
        index: activeIndex.value,
        preset: preset.value,
        lookbackDays: lookbackDays.value,
        limit: limit.value,
      })
      rsRankRows.value = []
      streakRows.value = []
      factorRows.value = []
    } else {
      if (preset.value === 'rs') {
        screenerRows.value = []
        rsRankRows.value = await getRelativeStrengthRank({
          index: activeIndex.value,
          lookbackDays: lookbackDays.value,
          limit: limit.value,
          requireAboveMa50: rsRequireAboveMa50.value,
        })
        streakRows.value = []
        factorRows.value = []
      } else {
        screenerRows.value = []
        rsRankRows.value = []
        const today = new Date()
        const defaultEnd = new Date(today)
        defaultEnd.setDate(defaultEnd.getDate() - 1)
        const defaultStart = new Date(defaultEnd)
        defaultStart.setDate(defaultStart.getDate() - lookbackDays.value)

        if (preset.value === 'streak') {
          factorRows.value = []
          const start = streakDateRange.value?.[0] ? toYmd(streakDateRange.value[0]) : toYmd(defaultStart)
          const end = streakDateRange.value?.[1] ? toYmd(streakDateRange.value[1]) : toYmd(defaultEnd)
          streakRows.value = await rankStreaks({
            index: activeIndex.value,
            interval: streakInterval.value,
            direction: streakDirection.value,
            start,
            end,
            limit: limit.value,
            volumeMultiple: streakVolumeMultiple.value > 1 ? streakVolumeMultiple.value : undefined,
            flatThresholdPct: streakFlatThresholdPct.value > 0 ? streakFlatThresholdPct.value : undefined,
          })
        } else {
          streakRows.value = []
          const start = factorDateRange.value?.[0] ? toYmd(factorDateRange.value[0]) : toYmd(defaultStart)
          const end = factorDateRange.value?.[1] ? toYmd(factorDateRange.value[1]) : toYmd(defaultEnd)

          let metric = 'max_drawdown'
          let mode: string | undefined = undefined
          let lookback: number | undefined = undefined
          if (preset.value === 'drawdown_worst') {
            metric = 'max_drawdown'
            mode = 'worst'
          } else if (preset.value === 'drawdown_best') {
            metric = 'max_drawdown'
            mode = 'best'
          } else if (preset.value === 'runup') {
            metric = 'max_runup'
          } else if (preset.value === 'rundown') {
            metric = 'max_rundown'
          } else if (preset.value === 'new_high') {
            metric = 'new_high_count'
            lookback = factorLookback.value
          } else if (preset.value === 'new_low') {
            metric = 'new_low_count'
            lookback = factorLookback.value
          }

          factorRows.value = await rankFactors({
            index: activeIndex.value,
            interval: factorInterval.value,
            metric,
            mode,
            lookback,
            start,
            end,
            limit: limit.value,
          })
        }
      }
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? '选股失败')
  } finally {
    screenerLoading.value = false
  }
}

async function refreshBreadth() {
  breadthLoading.value = true
  try {
    breadth.value = await getBreadth(activeIndex.value)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? '加载失败')
  } finally {
    breadthLoading.value = false
  }
}

async function refreshAll() {
  if (activeTab.value === 'screener') {
    await refreshScreener()
  } else {
    await refreshBreadth()
  }
}

onMounted(async () => {
  await loadIndices()
  await refreshScreener()
})
</script>

<template>
  <el-space direction="vertical" class="page" :size="16" fill>
    <PageHeader title="市场" subtitle="选股器 + 市场宽度（广度）">
      <el-select v-model="activeIndex" :loading="loadingIndices" style="width: 220px" filterable @change="refreshAll">
        <el-option v-for="idx in indices" :key="idx.symbol" :label="`${idx.name || idx.symbol} (${idx.symbol})`" :value="idx.symbol" />
      </el-select>
      <el-button :loading="screenerLoading || breadthLoading" @click="refreshAll">刷新</el-button>
    </PageHeader>

    <el-tabs v-model="activeTab" type="card" @tab-change="refreshAll">
      <el-tab-pane label="选股器" name="screener" />
      <el-tab-pane label="市场宽度" name="breadth" />
    </el-tabs>

    <div v-if="activeTab === 'screener'">
      <el-row :gutter="16">
        <el-col :span="7">
          <el-card shadow="never" style="border-radius: 12px">
            <div style="font-weight: 700; margin-bottom: 12px">筛选条件 ({{ activeIndexName }})</div>
            <el-form label-width="88px">
              <el-form-item label="模板">
                <el-select v-model="preset" style="width: 100%">
                  <el-option label="趋势最强（强于 MA50）" value="trend" />
                  <el-option label="52周新高" value="breakout" />
                  <el-option label="相对强弱 Top（对标指数）" value="rs" />
                  <el-option label="连涨/连跌最长" value="streak" />
                  <el-option label="最大回撤（跌得最惨）" value="drawdown_worst" />
                  <el-option label="最大回撤（最抗跌）" value="drawdown_best" />
                  <el-option label="最大单段上涨（Max Run-up）" value="runup" />
                  <el-option label="最大单段下跌（Max Run-down）" value="rundown" />
                  <el-option label="区间新高次数" value="new_high" />
                  <el-option label="区间新低次数" value="new_low" />
                </el-select>
              </el-form-item>
              <el-form-item label="周期" v-if="preset === 'trend' || preset === 'breakout' || preset === 'rs'">
                <el-select v-model="lookbackDays" style="width: 100%">
                  <el-option v-for="o in lookbackOptions" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>

              <el-form-item label="K线" v-if="preset === 'streak'">
                <el-select v-model="streakInterval" style="width: 100%">
                  <el-option label="日K" value="1d" />
                  <el-option label="周K" value="1w" />
                  <el-option label="月K" value="1m" />
                </el-select>
              </el-form-item>

              <el-form-item label="方向" v-if="preset === 'streak'">
                <el-select v-model="streakDirection" style="width: 100%">
                  <el-option label="连涨" value="up" />
                  <el-option label="连跌" value="down" />
                </el-select>
              </el-form-item>

              <el-form-item label="范围" v-if="preset === 'streak'">
                <el-date-picker
                  v-model="streakDateRange"
                  type="daterange"
                  unlink-panels
                  start-placeholder="开始"
                  end-placeholder="结束"
                  style="width: 100%"
                />
              </el-form-item>

              <el-form-item label="回溯" v-if="preset === 'streak' && !streakDateRange">
                <el-select v-model="lookbackDays" style="width: 100%">
                  <el-option v-for="o in lookbackOptions" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>

              <el-form-item label="放量" v-if="preset === 'streak'">
                <el-input-number v-model="streakVolumeMultiple" :min="0" :max="20" :step="0.1" style="width: 100%" />
              </el-form-item>

              <el-form-item label="震荡%" v-if="preset === 'streak'">
                <el-input-number v-model="streakFlatThresholdPct" :min="0" :max="5" :step="0.05" style="width: 100%" />
              </el-form-item>

              <el-form-item
                label="K线"
                v-if="
                  preset === 'drawdown_worst' ||
                  preset === 'drawdown_best' ||
                  preset === 'runup' ||
                  preset === 'rundown' ||
                  preset === 'new_high' ||
                  preset === 'new_low'
                "
              >
                <el-select v-model="factorInterval" style="width: 100%">
                  <el-option label="日K" value="1d" />
                  <el-option label="周K" value="1w" />
                  <el-option label="月K" value="1m" />
                </el-select>
              </el-form-item>

              <el-form-item
                label="范围"
                v-if="
                  preset === 'drawdown_worst' ||
                  preset === 'drawdown_best' ||
                  preset === 'runup' ||
                  preset === 'rundown' ||
                  preset === 'new_high' ||
                  preset === 'new_low'
                "
              >
                <el-date-picker
                  v-model="factorDateRange"
                  type="daterange"
                  unlink-panels
                  start-placeholder="开始"
                  end-placeholder="结束"
                  style="width: 100%"
                />
              </el-form-item>

              <el-form-item
                label="回溯"
                v-if="
                  (preset === 'drawdown_worst' ||
                    preset === 'drawdown_best' ||
                    preset === 'runup' ||
                    preset === 'rundown' ||
                    preset === 'new_high' ||
                    preset === 'new_low') &&
                  !factorDateRange
                "
              >
                <el-select v-model="lookbackDays" style="width: 100%">
                  <el-option v-for="o in lookbackOptions" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>

              <el-form-item label="窗口" v-if="preset === 'new_high' || preset === 'new_low'">
                <el-input-number v-model="factorLookback" :min="2" :max="2000" :step="1" style="width: 100%" />
              </el-form-item>

              <el-form-item label="数量">
                <el-input-number v-model="limit" :min="5" :max="200" style="width: 100%" />
              </el-form-item>
              <el-form-item v-if="preset === 'rs'" label="过滤">
                <el-switch v-model="rsRequireAboveMa50" active-text="站上MA50" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="screenerLoading" @click="refreshScreener" style="width: 100%">
                  运行选股
                </el-button>
              </el-form-item>
              <div class="text-muted" style="font-size: 12px; line-height: 18px">
                趋势：以近周期涨幅排序，并要求股价高于 MA50。<br />
                52周新高：筛出创 52 周新高的股票，再按周期涨幅排序。<br />
                相对强弱：按（股票收益 / 指数收益）排序，衡量跑赢程度。
                <template v-if="preset === 'streak'">
                  <br />
                  连涨/连跌：在指定范围内，按“连续上涨/下跌的周期数”排序（相邻两根K线收盘价比较）。
                  <br />
                  放量：周期成交量 ≥ 20周期均量 × 倍数（≤1 视为不启用）。
                  <br />
                  震荡过滤：涨跌幅绝对值小于阈值视为平盘（会打断连续性）。
                </template>
                <template v-if="preset === 'drawdown_worst' || preset === 'drawdown_best'">
                  <br />
                  最大回撤：区间内（收盘/历史最高收盘 - 1）的最小值，越负越惨。
                </template>
                <template v-if="preset === 'runup'">
                  <br />
                  最大单段上涨：区间内（收盘/历史最低收盘 - 1）的最大值。
                </template>
                <template v-if="preset === 'rundown'">
                  <br />
                  最大单段下跌：等同最大回撤（但按“最大跌幅”视角展示）。
                </template>
                <template v-if="preset === 'new_high' || preset === 'new_low'">
                  <br />
                  新高/新低：收盘价突破最近 N 根K线（不含当根）的最高/最低次数，并给出频率。
                </template>
              </div>
            </el-form>
          </el-card>
        </el-col>

        <el-col :span="17">
          <el-card shadow="never" style="border-radius: 12px">
            <div style="display: flex; justify-content: space-between; align-items: baseline; gap: 12px; flex-wrap: wrap">
              <div>
                <div style="font-weight: 700">结果</div>
                <div class="text-muted" style="font-size: 12px; margin-top: 4px">
                  以 {{ activeIndexName }} 为范围
                </div>
              </div>
            </div>

            <el-table
              v-if="preset === 'trend' || preset === 'breakout'"
              v-loading="screenerLoading"
              :data="screenerRows"
              row-key="symbol"
              style="width: 100%; margin-top: 12px"
            >
              <el-table-column prop="symbol" label="代码" width="110" />
              <el-table-column prop="name" label="名称" min-width="220" show-overflow-tooltip />
              <el-table-column prop="asOfDate" label="日期" width="110" />
              <el-table-column prop="close" label="收盘" width="110" align="right">
                <template #default="{ row }">{{ row.close == null ? '-' : row.close.toFixed(2) }}</template>
              </el-table-column>
              <el-table-column prop="returnPct" label="涨幅" width="110" align="right">
                <template #default="{ row }">
                  <span :style="{ color: row.returnPct != null && row.returnPct > 0 ? 'var(--app-success)' : 'var(--app-danger)' }">
                    {{ row.returnPct == null ? '-' : (row.returnPct * 100).toFixed(2) + '%' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="ma50" label="MA50" width="110" align="right">
                <template #default="{ row }">{{ row.ma50 == null ? '-' : row.ma50.toFixed(2) }}</template>
              </el-table-column>
              <el-table-column prop="ma200" label="MA200" width="110" align="right">
                <template #default="{ row }">{{ row.ma200 == null ? '-' : row.ma200.toFixed(2) }}</template>
              </el-table-column>
              <el-table-column prop="volume" label="成交量" width="140" align="right">
                <template #default="{ row }">{{ row.volume == null ? '-' : row.volume.toLocaleString() }}</template>
              </el-table-column>
            </el-table>

            <el-table
              v-else-if="preset === 'rs'"
              v-loading="screenerLoading"
              :data="rsRankRows"
              row-key="symbol"
              style="width: 100%; margin-top: 12px"
            >
              <el-table-column prop="symbol" label="代码" width="110" />
              <el-table-column prop="name" label="名称" min-width="220" show-overflow-tooltip />
              <el-table-column prop="asOfDate" label="日期" width="110" />
              <el-table-column prop="rsReturnPct" label="相对收益" width="120" align="right">
                <template #default="{ row }">
                  <span :style="{ color: row.rsReturnPct != null && row.rsReturnPct > 0 ? 'var(--app-success)' : 'var(--app-danger)' }">
                    {{ row.rsReturnPct == null ? '-' : (row.rsReturnPct * 100).toFixed(2) + '%' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="stockReturnPct" label="股票收益" width="120" align="right">
                <template #default="{ row }">{{ row.stockReturnPct == null ? '-' : (row.stockReturnPct * 100).toFixed(2) + '%' }}</template>
              </el-table-column>
              <el-table-column prop="indexReturnPct" label="指数收益" width="120" align="right">
                <template #default="{ row }">{{ row.indexReturnPct == null ? '-' : (row.indexReturnPct * 100).toFixed(2) + '%' }}</template>
              </el-table-column>
            </el-table>

            <el-table
              v-else-if="preset === 'streak'"
              v-loading="screenerLoading"
              :data="streakRows"
              row-key="symbol"
              style="width: 100%; margin-top: 12px"
            >
              <el-table-column prop="symbol" label="代码" width="110" />
              <el-table-column prop="name" label="名称" min-width="220" show-overflow-tooltip />
              <el-table-column prop="interval" label="K线" width="80" />
              <el-table-column prop="direction" label="方向" width="80">
                <template #default="{ row }">
                  <span :style="{ color: row.direction === 'up' ? 'var(--app-success)' : 'var(--app-danger)' }">
                    {{ row.direction === 'up' ? '连涨' : '连跌' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="streak" label="最长" width="90" align="right" />
              <el-table-column prop="startDate" label="开始" width="110" />
              <el-table-column prop="endDate" label="结束" width="110" />
            </el-table>

            <el-table
              v-else
              v-loading="screenerLoading"
              :data="factorRows"
              row-key="symbol"
              style="width: 100%; margin-top: 12px"
            >
              <el-table-column prop="symbol" label="代码" width="110" />
              <el-table-column prop="name" label="名称" min-width="220" show-overflow-tooltip />
              <el-table-column label="值/次数" width="140" align="right">
                <template #default="{ row }">
                  <span v-if="row.value != null">
                    {{ (row.value * 100).toFixed(2) + '%' }}
                  </span>
                  <span v-else>
                    {{ row.count ?? '-' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="频率" width="110" align="right">
                <template #default="{ row }">
                  {{ row.rate == null ? '-' : (row.rate * 100).toFixed(2) + '%' }}
                </template>
              </el-table-column>
              <el-table-column prop="endDate" label="日期" width="110" />
            </el-table>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <div v-else>
      <el-card shadow="never" style="border-radius: 12px">
        <div style="display: flex; justify-content: space-between; align-items: baseline; gap: 12px; flex-wrap: wrap">
          <div>
            <div style="font-weight: 700">市场宽度 ({{ activeIndexName }})</div>
            <div class="text-muted" style="font-size: 12px; margin-top: 4px">
              截止日期：{{ breadth?.asOfDate || '-' }}；覆盖：{{ breadth?.membersWithData || 0 }}/{{ breadth?.totalMembers || 0 }}
            </div>
          </div>
          <el-button :loading="breadthLoading" @click="refreshBreadth">刷新</el-button>
        </div>

        <el-skeleton v-if="breadthLoading" :rows="6" animated style="margin-top: 12px" />

        <div v-else style="margin-top: 12px">
          <el-row :gutter="12">
            <el-col :span="6">
              <el-card shadow="never" style="border-radius: 12px; background: var(--el-fill-color-lighter)">
                <div class="text-muted" style="font-size: 12px">收涨比例</div>
                <div style="font-size: 22px; font-weight: 800; margin-top: 6px">
                  {{ pct(breadth?.up || 0, breadth?.membersWithData || 0) }}
                </div>
                <div class="text-muted" style="font-size: 12px; margin-top: 6px">
                  上涨 {{ breadth?.up || 0 }} / 下跌 {{ breadth?.down || 0 }}
                </div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="never" style="border-radius: 12px; background: var(--el-fill-color-lighter)">
                <div class="text-muted" style="font-size: 12px">站上 MA50</div>
                <div style="font-size: 22px; font-weight: 800; margin-top: 6px">
                  {{ pct(breadth?.aboveMa50 || 0, breadth?.membersWithData || 0) }}
                </div>
                <div class="text-muted" style="font-size: 12px; margin-top: 6px">数量：{{ breadth?.aboveMa50 || 0 }}</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="never" style="border-radius: 12px; background: var(--el-fill-color-lighter)">
                <div class="text-muted" style="font-size: 12px">站上 MA200</div>
                <div style="font-size: 22px; font-weight: 800; margin-top: 6px">
                  {{ pct(breadth?.aboveMa200 || 0, breadth?.membersWithData || 0) }}
                </div>
                <div class="text-muted" style="font-size: 12px; margin-top: 6px">数量：{{ breadth?.aboveMa200 || 0 }}</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="never" style="border-radius: 12px; background: var(--el-fill-color-lighter)">
                <div class="text-muted" style="font-size: 12px">放量（≥2x 50日均量）</div>
                <div style="font-size: 22px; font-weight: 800; margin-top: 6px">
                  {{ pct(breadth?.volumeSurge || 0, breadth?.membersWithData || 0) }}
                </div>
                <div class="text-muted" style="font-size: 12px; margin-top: 6px">数量：{{ breadth?.volumeSurge || 0 }}</div>
              </el-card>
            </el-col>
          </el-row>

          <el-divider />

          <el-row :gutter="12">
            <el-col :span="8">
              <div style="font-weight: 700; margin-bottom: 8px">涨跌分布</div>
              <el-progress :percentage="breadth?.membersWithData ? (breadth.up / breadth.membersWithData) * 100 : 0" :stroke-width="10" />
              <div class="text-muted" style="font-size: 12px; margin-top: 8px">
                上涨 {{ breadth?.up || 0 }} / 下跌 {{ breadth?.down || 0 }} / 平 {{ breadth?.flat || 0 }}
              </div>
            </el-col>
            <el-col :span="8">
              <div style="font-weight: 700; margin-bottom: 8px">52周新高/新低</div>
              <div style="display: flex; gap: 16px; align-items: center">
                <div style="flex: 1">
                  <div class="text-muted" style="font-size: 12px">新高</div>
                  <div style="font-size: 20px; font-weight: 800">{{ breadth?.newHigh52w || 0 }}</div>
                </div>
                <div style="flex: 1">
                  <div class="text-muted" style="font-size: 12px">新低</div>
                  <div style="font-size: 20px; font-weight: 800">{{ breadth?.newLow52w || 0 }}</div>
                </div>
              </div>
            </el-col>
            <el-col :span="8">
              <div style="font-weight: 700; margin-bottom: 8px">站上均线</div>
              <div class="text-muted" style="font-size: 12px">MA20：{{ breadth?.aboveMa20 || 0 }}</div>
              <div class="text-muted" style="font-size: 12px">MA50：{{ breadth?.aboveMa50 || 0 }}</div>
              <div class="text-muted" style="font-size: 12px">MA200：{{ breadth?.aboveMa200 || 0 }}</div>
            </el-col>
          </el-row>
        </div>
      </el-card>
    </div>
  </el-space>
</template>
