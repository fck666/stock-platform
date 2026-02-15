<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import LineChart from '../../components/LineChart.vue'
import { getAnalyticsSummary, type AnalyticsSummaryDto } from '../../api/analytics'

const days = ref(14)
const loading = ref(false)
const summary = ref<AnalyticsSummaryDto | null>(null)

const dates = computed(() => {
  const list = summary.value?.pageViews ?? []
  return list.map(p => p.day)
})

const pageViewsSeries = computed(() => {
  const list = summary.value?.pageViews ?? []
  return list.map(p => p.count)
})

const apiCallsSeries = computed(() => {
  const list = summary.value?.apiCalls ?? []
  return list.map(p => p.count)
})

async function load() {
  loading.value = true
  try {
    summary.value = await getAnalyticsSummary(days.value)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <el-space direction="vertical" class="page" :size="16" fill>
    <PageHeader title="行为看板" subtitle="最近一段时间的页面浏览与接口调用概览">
      <el-select v-model="days" size="small" style="width: 140px" @change="load">
        <el-option label="近 7 天" :value="7" />
        <el-option label="近 14 天" :value="14" />
        <el-option label="近 30 天" :value="30" />
      </el-select>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </PageHeader>

    <el-row :gutter="16" style="width: 100%">
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <LineChart
            title="页面浏览（按天）"
            :dates="dates"
            :series="[{ name: 'PV', data: pageViewsSeries, color: 'var(--app-accent)' }]"
            :height="260"
          />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <LineChart
            title="接口调用（按天）"
            :dates="dates"
            :series="[{ name: 'API', data: apiCallsSeries, color: 'var(--app-success)' }]"
            :height="260"
          />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="width: 100%">
      <el-col :xs="24" :md="8">
        <el-card shadow="never">
          <div style="font-weight: 800; margin-bottom: 10px">Top 页面</div>
          <el-table :data="summary?.topPages ?? []" size="small" style="width: 100%" :show-header="false" v-loading="loading">
            <el-table-column prop="key" min-width="160" />
            <el-table-column prop="count" width="80" align="right" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="never">
          <div style="font-weight: 800; margin-bottom: 10px">Top 接口</div>
          <el-table :data="summary?.topApis ?? []" size="small" style="width: 100%" v-loading="loading">
            <el-table-column label="接口" min-width="170">
              <template #default="{ row }">
                <span class="font-mono">{{ row.method }}</span>
                <span class="text-muted" style="margin-left: 6px">{{ row.path }}</span>
              </template>
            </el-table-column>
            <el-table-column label="次数" prop="count" width="70" align="right" />
            <el-table-column label="错误" prop="errorCount" width="70" align="right" />
            <el-table-column label="P95(ms)" prop="p95LatencyMs" width="86" align="right" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="never">
          <div style="font-weight: 800; margin-bottom: 10px">Top 用户（按接口调用）</div>
          <el-table :data="summary?.topUsers ?? []" size="small" style="width: 100%" :show-header="false" v-loading="loading">
            <el-table-column prop="key" min-width="160" />
            <el-table-column prop="count" width="80" align="right" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </el-space>
</template>

