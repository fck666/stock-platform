<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { 
  TrendCharts, 
  Histogram, 
  CaretBottom, 
  Lightning, 
  DataLine, 
  Search, 
  View 
} from '@element-plus/icons-vue'
import { executeAnalysis } from '../api/analysis'
import type { AnalysisResultDto } from '../api/analysis_types'
import dayjs from 'dayjs'

const router = useRouter()
const loading = ref(false)
const results = ref<AnalysisResultDto[]>([])

const form = reactive({
  index: '^SPX',
  type: 'TREND',
  dateRange: [
    dayjs().subtract(6, 'month').format('YYYY-MM-DD'),
    dayjs().subtract(1, 'day').format('YYYY-MM-DD')
  ] as [string, string],
  limit: 20,
  trendType: 'strong',
  threshold: 0.9,
  alphaBetaType: 'alpha'
})

const analysisTypes = [
  { value: 'TREND', label: '趋势强度', icon: TrendCharts },
  { value: 'WIN_RATE', label: '收涨比例', icon: Histogram },
  { value: 'MAX_DRAWDOWN', label: '最大回撤', icon: CaretBottom },
  { value: 'VOLUME_SPIKE', label: '成交量异动', icon: Lightning },
  { value: 'ALPHA_BETA', label: 'Alpha/Beta', icon: DataLine },
]

const indices = [
  { symbol: 'ALL', name: '全部股票' },
  { symbol: '^SPX', name: 'S&P 500' },
  { symbol: '^HSI', name: '恒生指数' },
  { symbol: '^HSTECH', name: '恒生科技' },
]

async function runAnalysis() {
  loading.value = true
  try {
    const request = {
      index: form.index,
      type: form.type as any,
      start: form.dateRange[0],
      end: form.dateRange[1],
      limit: form.limit,
      params: {} as any
    }

    if (form.type === 'TREND') request.params.trendType = form.trendType
    if (form.type === 'WIN_RATE') request.params.threshold = form.threshold
    if (form.type === 'ALPHA_BETA') request.params.sortType = form.alphaBetaType

    const res = await executeAnalysis(request)
    results.value = res.results
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? '分析失败')
  } finally {
    loading.value = false
  }
}

function goDetail(symbol: string) {
  router.push(`/stocks/${encodeURIComponent(symbol)}`)
}

function formatPercent(val: number) {
  return (val * 100).toFixed(2) + '%'
}

function formatScore(val: number) {
  return val.toFixed(4)
}

onMounted(() => {
  runAnalysis()
})
</script>

<template>
  <el-space direction="vertical" style="width: 100%" :size="16" fill>
    <el-card shadow="never" style="border-radius: 12px">
      <template #header>
        <div style="font-weight: 700">筛选条件</div>
      </template>
      <el-form :model="form" label-width="100px" inline>
        <el-form-item label="分析维度">
          <el-segmented v-model="form.type" :options="analysisTypes" @change="runAnalysis">
            <template #item="{ item }">
              <div style="display: flex; flex-direction: column; align-items: center; padding: 4px 8px">
                <el-icon><component :is="item.icon" /></el-icon>
                <div style="font-size: 12px; margin-top: 4px">{{ item.label }}</div>
              </div>
            </template>
          </el-segmented>
        </el-form-item>
        <div style="margin-top: 16px">
          <el-form-item label="指数范围">
            <el-select v-model="form.index" style="width: 140px">
              <el-option v-for="idx in indices" :key="idx.symbol" :label="idx.name" :value="idx.symbol" />
            </el-select>
          </el-form-item>
          <el-form-item label="日期范围">
            <el-date-picker
              v-model="form.dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始"
              end-placeholder="结束"
              value-format="YYYY-MM-DD"
              style="width: 220px"
            />
          </el-form-item>
          <el-form-item label="展示数量">
            <el-input-number v-model="form.limit" :min="1" :max="100" style="width: 120px" />
          </el-form-item>

          <!-- Dynamic Params -->
          <template v-if="form.type === 'TREND'">
            <el-form-item label="趋势目标">
              <el-radio-group v-model="form.trendType" size="small">
                <el-radio-button label="strong">走势强</el-radio-button>
                <el-radio-button label="weak">走势弱</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </template>
          <template v-if="form.type === 'WIN_RATE'">
            <el-form-item label="胜率阈值">
              <el-input-number v-model="form.threshold" :step="0.05" :min="0" :max="1" size="small" />
            </el-form-item>
          </template>
          <template v-if="form.type === 'ALPHA_BETA'">
            <el-form-item label="排序指标">
              <el-radio-group v-model="form.alphaBetaType" size="small">
                <el-radio-button label="alpha">Alpha (超额收益)</el-radio-button>
                <el-radio-button label="beta">Beta (波动相关)</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </template>

          <el-form-item>
            <el-button type="primary" :loading="loading" :icon="Search" @click="runAnalysis">执行分析</el-button>
          </el-form-item>
        </div>
      </el-form>
    </el-card>

    <el-card shadow="never" style="border-radius: 12px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span style="font-weight: 700">分析结果</span>
          <span style="font-size: 12px; color: #909399">
            {{ 
              form.type === 'TREND' ? '趋势得分 = 标准化斜率 * R²' : 
              form.type === 'WIN_RATE' ? '收涨比例 = 上涨天数 / 总天数' :
              form.type === 'MAX_DRAWDOWN' ? '最大回撤 = (当前低点 - 前期高点) / 前期高点' :
              form.type === 'VOLUME_SPIKE' ? '异动倍数 = 周期内最大成交量 / 基准平均成交量' :
              'Alpha = 超额收益率, Beta = 与大盘波动的相关性系数'
            }}
          </span>
        </div>
      </template>
      <el-table v-loading="loading" :data="results" style="width: 100%" stripe>
        <el-table-column type="index" label="排名" width="60" align="center" />
        <el-table-column prop="symbol" label="代码" width="100" sortable />
        <el-table-column prop="name" label="公司名称" min-width="180" show-overflow-tooltip />
        
        <!-- Score Column with Dynamic Label and Formatting -->
        <el-table-column prop="score" :label="
          form.type === 'TREND' ? '趋势得分' : 
          form.type === 'WIN_RATE' ? '收涨比例' :
          form.type === 'MAX_DRAWDOWN' ? '最大回撤' :
          form.type === 'VOLUME_SPIKE' ? '异动倍数' :
          (form.alphaBetaType === 'alpha' ? 'Alpha' : 'Beta')
        " width="130" align="right">
          <template #default="{ row }">
            <el-tag :type="
              form.type === 'MAX_DRAWDOWN' ? (row.score < -0.2 ? 'danger' : 'success') :
              form.type === 'VOLUME_SPIKE' ? (row.score > 3 ? 'warning' : 'info') :
              (row.score > 0 ? 'success' : 'danger')
            " effect="plain">
              <template v-if="form.type === 'TREND'">{{ formatScore(row.score) }}</template>
              <template v-else-if="form.type === 'WIN_RATE'">{{ formatPercent(row.score) }}</template>
              <template v-else-if="form.type === 'MAX_DRAWDOWN'">{{ formatPercent(row.score) }}</template>
              <template v-else-if="form.type === 'VOLUME_SPIKE'">{{ row.score.toFixed(2) }}x</template>
              <template v-else>{{ formatScore(row.score) }}</template>
            </el-tag>
          </template>
        </el-table-column>
        
        <!-- Details Column with Better Layout -->
        <el-table-column label="多维数据" min-width="320">
          <template #default="{ row }">
            <div style="display: flex; gap: 12px; flex-wrap: wrap">
              <template v-if="form.type === 'TREND'">
                <el-statistic title="斜率" :value="row.details.slope" :precision="4" />
                <el-statistic title="R² (稳定性)" :value="row.details.rSquared" :precision="4" />
              </template>
              <template v-else-if="form.type === 'WIN_RATE'">
                <el-statistic title="上涨天数" :value="row.details.upDays" />
                <el-statistic title="总天数" :value="row.details.totalDays" />
              </template>
              <template v-else-if="form.type === 'MAX_DRAWDOWN'">
                <el-statistic title="历史最高价" :value="row.details.peak" :precision="2" />
                <el-statistic title="观测天数" :value="row.details.totalDays" />
              </template>
              <template v-else-if="form.type === 'VOLUME_SPIKE'">
                <el-statistic title="基准均量" :value="row.details.avgBaselineVolume" />
                <el-statistic title="峰值成交" :value="row.details.maxVolume" />
              </template>
              <template v-else-if="form.type === 'ALPHA_BETA'">
                <el-statistic title="Alpha" :value="row.details.alpha" :precision="4" />
                <el-statistic title="Beta" :value="row.details.beta" :precision="4" />
                <el-statistic title="相关性 (R²)" :value="row.details.rSquare" :precision="4" />
              </template>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="goDetail(row.symbol)">K 线</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </el-space>
</template>

<style scoped>
:deep(.el-statistic__title) {
  font-size: 11px;
  color: #909399;
}
:deep(.el-statistic__content) {
  font-size: 13px;
  font-weight: 500;
}
</style>
