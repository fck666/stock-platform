<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
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

async function loadAll() {
  if (!symbol.value) return
  loading.value = true
  try {
    detail.value = await getStockDetail(symbol.value)
    bars.value = await getStockBars(symbol.value, { interval: '1d' })
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

onMounted(loadAll)
watch(symbol, () => loadAll())
</script>

<template>
  <el-space direction="vertical" style="width: 100%" :size="16">
    <el-card shadow="never" style="border-radius: 12px" v-loading="loading">
      <div style="display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; flex-wrap: wrap">
        <div>
          <div style="font-size: 16px; font-weight: 700">{{ symbol }} - {{ detail?.name || '-' }}</div>
          <div style="color: #667085; margin-top: 4px">{{ detail?.wikiDescription || '' }}</div>
        </div>
        <el-space>
          <el-button :loading="loading" @click="loadAll">刷新</el-button>
          <el-button type="primary" :loading="syncing" @click="triggerSync">同步数据</el-button>
        </el-space>
      </div>
    </el-card>

    <el-row :gutter="16">
      <el-col :span="14">
        <el-card shadow="never" style="border-radius: 12px; padding: 0">
          <div v-loading="loading" style="min-height: 520px">
            <CandlestickChart :bars="bars" :title="title" :height="520" />
          </div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never" style="border-radius: 12px" v-loading="loading">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="代码">{{ detail?.symbol || symbol }}</el-descriptions-item>
            <el-descriptions-item label="公司">{{ detail?.name || '-' }}</el-descriptions-item>
            <el-descriptions-item label="行业">{{ detail?.gicsSector || '-' }}</el-descriptions-item>
            <el-descriptions-item label="子行业">{{ detail?.gicsSubIndustry || '-' }}</el-descriptions-item>
            <el-descriptions-item label="总部">{{ detail?.headquarters || '-' }}</el-descriptions-item>
            <el-descriptions-item label="加入标普500">{{ detail?.dateFirstAdded || '-' }}</el-descriptions-item>
            <el-descriptions-item label="CIK">{{ detail?.cik || '-' }}</el-descriptions-item>
            <el-descriptions-item label="Founded">{{ detail?.founded || '-' }}</el-descriptions-item>
            <el-descriptions-item label="Wikipedia">
              <a v-if="detail?.wikiUrl" :href="detail.wikiUrl" target="_blank" rel="noreferrer">{{ detail.wikiTitle || detail.wikiUrl }}</a>
              <span v-else>-</span>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card shadow="never" style="border-radius: 12px; margin-top: 16px" v-loading="loading">
          <div style="font-weight: 700; margin-bottom: 8px">标识符</div>
          <el-table :data="detail?.identifiers || []" size="small" style="width: 100%">
            <el-table-column prop="provider" label="provider" width="120" />
            <el-table-column prop="identifier" label="identifier" show-overflow-tooltip />
          </el-table>
        </el-card>

        <el-card shadow="never" style="border-radius: 12px; margin-top: 16px" v-loading="loading">
          <div style="font-weight: 700; margin-bottom: 8px">摘要</div>
          <div style="color: #667085; white-space: pre-wrap; line-height: 1.6">
            {{ detail?.wikiExtract || '-' }}
          </div>
        </el-card>
      </el-col>
    </el-row>
  </el-space>
</template>

