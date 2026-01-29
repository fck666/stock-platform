<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getBreadth,
  getRelativeStrengthRank,
  listIndices,
  runScreener,
  type BreadthSnapshotDto,
  type IndexListItemDto,
  type RsRankItemDto,
  type ScreenerItemDto,
} from '../api/market'

const loadingIndices = ref(false)
const indices = ref<IndexListItemDto[]>([])
const activeIndex = ref('^SPX')
const activeIndexName = computed(() => indices.value.find(i => i.symbol === activeIndex.value)?.name || activeIndex.value)

const activeTab = ref<'screener' | 'breadth'>('screener')

const screenerLoading = ref(false)
const breadthLoading = ref(false)

const preset = ref<'trend' | 'breakout' | 'rs'>('trend')
const lookbackDays = ref(126)
const limit = ref(20)
const screenerRows = ref<ScreenerItemDto[]>([])
const rsRankRows = ref<RsRankItemDto[]>([])
const rsRequireAboveMa50 = ref(true)

const breadth = ref<BreadthSnapshotDto | null>(null)

const lookbackOptions = [
  { label: '3个月 (63)', value: 63 },
  { label: '6个月 (126)', value: 126 },
  { label: '12个月 (252)', value: 252 },
]

function pct(n: number, d: number) {
  if (!d || d <= 0) return '0%'
  return `${((n / d) * 100).toFixed(1)}%`
}

async function loadIndices() {
  loadingIndices.value = true
  try {
    indices.value = await listIndices()
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
    } else {
      screenerRows.value = []
      rsRankRows.value = await getRelativeStrengthRank({
        index: activeIndex.value,
        lookbackDays: lookbackDays.value,
        limit: limit.value,
        requireAboveMa50: rsRequireAboveMa50.value,
      })
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
  <el-space direction="vertical" style="width: 100%" :size="16" fill>
    <el-card shadow="never" style="border-radius: 12px">
      <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap">
        <div>
          <div style="font-size: 16px; font-weight: 700">市场</div>
          <div style="color: #667085; margin-top: 4px">选股器 + 市场宽度（广度）</div>
        </div>
        <el-space wrap>
          <el-select v-model="activeIndex" :loading="loadingIndices" style="width: 220px" filterable @change="refreshAll">
            <el-option v-for="idx in indices" :key="idx.symbol" :label="`${idx.name || idx.symbol} (${idx.symbol})`" :value="idx.symbol" />
          </el-select>
          <el-button :loading="screenerLoading || breadthLoading" @click="refreshAll">刷新</el-button>
        </el-space>
      </div>
    </el-card>

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
                </el-select>
              </el-form-item>
              <el-form-item label="周期">
                <el-select v-model="lookbackDays" style="width: 100%">
                  <el-option v-for="o in lookbackOptions" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
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
              <div style="color: #667085; font-size: 12px; line-height: 18px">
                趋势：以近周期涨幅排序，并要求股价高于 MA50。<br />
                52周新高：筛出创 52 周新高的股票，再按周期涨幅排序。<br />
                相对强弱：按（股票收益 / 指数收益）排序，衡量跑赢程度。
              </div>
            </el-form>
          </el-card>
        </el-col>

        <el-col :span="17">
          <el-card shadow="never" style="border-radius: 12px">
            <div style="display: flex; justify-content: space-between; align-items: baseline; gap: 12px; flex-wrap: wrap">
              <div>
                <div style="font-weight: 700">结果</div>
                <div style="color: #667085; font-size: 12px; margin-top: 4px">
                  以 {{ activeIndexName }} 为范围
                </div>
              </div>
            </div>

            <el-table
              v-if="preset !== 'rs'"
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
                  <span :style="{ color: row.returnPct != null && row.returnPct > 0 ? '#26a69a' : '#ef5350' }">
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
              v-else
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
                  <span :style="{ color: row.rsReturnPct != null && row.rsReturnPct > 0 ? '#26a69a' : '#ef5350' }">
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
          </el-card>
        </el-col>
      </el-row>
    </div>

    <div v-else>
      <el-card shadow="never" style="border-radius: 12px">
        <div style="display: flex; justify-content: space-between; align-items: baseline; gap: 12px; flex-wrap: wrap">
          <div>
            <div style="font-weight: 700">市场宽度 ({{ activeIndexName }})</div>
            <div style="color: #667085; font-size: 12px; margin-top: 4px">
              截止日期：{{ breadth?.asOfDate || '-' }}；覆盖：{{ breadth?.membersWithData || 0 }}/{{ breadth?.totalMembers || 0 }}
            </div>
          </div>
          <el-button :loading="breadthLoading" @click="refreshBreadth">刷新</el-button>
        </div>

        <el-skeleton v-if="breadthLoading" :rows="6" animated style="margin-top: 12px" />

        <div v-else style="margin-top: 12px">
          <el-row :gutter="12">
            <el-col :span="6">
              <el-card shadow="never" style="border-radius: 12px; background: #f8fafc">
                <div style="color: #667085; font-size: 12px">收涨比例</div>
                <div style="font-size: 22px; font-weight: 800; margin-top: 6px">
                  {{ pct(breadth?.up || 0, breadth?.membersWithData || 0) }}
                </div>
                <div style="color: #667085; font-size: 12px; margin-top: 6px">
                  上涨 {{ breadth?.up || 0 }} / 下跌 {{ breadth?.down || 0 }}
                </div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="never" style="border-radius: 12px; background: #f8fafc">
                <div style="color: #667085; font-size: 12px">站上 MA50</div>
                <div style="font-size: 22px; font-weight: 800; margin-top: 6px">
                  {{ pct(breadth?.aboveMa50 || 0, breadth?.membersWithData || 0) }}
                </div>
                <div style="color: #667085; font-size: 12px; margin-top: 6px">数量：{{ breadth?.aboveMa50 || 0 }}</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="never" style="border-radius: 12px; background: #f8fafc">
                <div style="color: #667085; font-size: 12px">站上 MA200</div>
                <div style="font-size: 22px; font-weight: 800; margin-top: 6px">
                  {{ pct(breadth?.aboveMa200 || 0, breadth?.membersWithData || 0) }}
                </div>
                <div style="color: #667085; font-size: 12px; margin-top: 6px">数量：{{ breadth?.aboveMa200 || 0 }}</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="never" style="border-radius: 12px; background: #f8fafc">
                <div style="color: #667085; font-size: 12px">放量（≥2x 50日均量）</div>
                <div style="font-size: 22px; font-weight: 800; margin-top: 6px">
                  {{ pct(breadth?.volumeSurge || 0, breadth?.membersWithData || 0) }}
                </div>
                <div style="color: #667085; font-size: 12px; margin-top: 6px">数量：{{ breadth?.volumeSurge || 0 }}</div>
              </el-card>
            </el-col>
          </el-row>

          <el-divider />

          <el-row :gutter="12">
            <el-col :span="8">
              <div style="font-weight: 700; margin-bottom: 8px">涨跌分布</div>
              <el-progress :percentage="breadth?.membersWithData ? (breadth.up / breadth.membersWithData) * 100 : 0" :stroke-width="10" />
              <div style="color: #667085; font-size: 12px; margin-top: 8px">
                上涨 {{ breadth?.up || 0 }} / 下跌 {{ breadth?.down || 0 }} / 平 {{ breadth?.flat || 0 }}
              </div>
            </el-col>
            <el-col :span="8">
              <div style="font-weight: 700; margin-bottom: 8px">52周新高/新低</div>
              <div style="display: flex; gap: 16px; align-items: center">
                <div style="flex: 1">
                  <div style="color: #667085; font-size: 12px">新高</div>
                  <div style="font-size: 20px; font-weight: 800">{{ breadth?.newHigh52w || 0 }}</div>
                </div>
                <div style="flex: 1">
                  <div style="color: #667085; font-size: 12px">新低</div>
                  <div style="font-size: 20px; font-weight: 800">{{ breadth?.newLow52w || 0 }}</div>
                </div>
              </div>
            </el-col>
            <el-col :span="8">
              <div style="font-weight: 700; margin-bottom: 8px">站上均线</div>
              <div style="color: #667085; font-size: 12px">MA20：{{ breadth?.aboveMa20 || 0 }}</div>
              <div style="color: #667085; font-size: 12px">MA50：{{ breadth?.aboveMa50 || 0 }}</div>
              <div style="color: #667085; font-size: 12px">MA200：{{ breadth?.aboveMa200 || 0 }}</div>
            </el-col>
          </el-row>
        </div>
      </el-card>
    </div>
  </el-space>
</template>
