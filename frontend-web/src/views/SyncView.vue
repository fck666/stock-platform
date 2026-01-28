<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { syncWiki, syncFundamentals, syncPrices, syncStocks, getSyncJob, type SyncJobDto } from '../api/market'

const activeJobs = ref<SyncJobDto[]>([])
const finishedJobs = ref<SyncJobDto[]>([])
const bulkSymbols = ref('')
const loading = ref(false)

let timer: any = null

async function startJob(action: () => Promise<SyncJobDto>) {
  loading.value = true
  try {
    if (import.meta.env.DEV) console.info('[sync] start')
    const job = await action()
    activeJobs.value.unshift(job)
    ElMessage.success(`任务已提交: ${job.jobId}`)
    startPolling()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? '启动任务失败')
  } finally {
    loading.value = false
  }
}

const indices = [
  { symbol: '^SPX', name: 'S&P 500' },
  { symbol: '^HSI', name: '恒生指数' },
  { symbol: '^HSTECH', name: '恒生科技' },
]
const activeIndex = ref('^SPX')

async function handleSyncWiki() {
  await startJob(() => syncWiki(activeIndex.value))
}

async function handleSyncPrices() {
  await startJob(() => syncPrices(activeIndex.value))
}

async function handleSyncFundamentals() {
  await startJob(() => syncFundamentals(activeIndex.value))
}

async function handleSyncBulk() {
  const symbols = bulkSymbols.value
    .split(/[\n,;]/)
    .map((s) => s.trim())
    .filter((s) => s.length > 0)
  
  if (symbols.length === 0) {
    ElMessage.warning('请输入股票代码')
    return
  }
  
  await startJob(() => syncStocks(symbols, activeIndex.value))
  bulkSymbols.value = ''
}

async function poll() {
  if (activeJobs.value.length === 0) {
    stopPolling()
    return
  }

  const nextActive: SyncJobDto[] = []
  for (const job of activeJobs.value) {
    try {
      const updated = await getSyncJob(job.jobId)
      if (import.meta.env.DEV && updated.status !== job.status) {
        console.info('[sync] job', updated.jobId, { from: job.status, to: updated.status, exitCode: updated.exitCode })
      }
      if (updated.status === 'SUCCEEDED' || updated.status === 'FAILED') {
        finishedJobs.value.unshift(updated)
      } else {
        nextActive.push(updated)
      }
    } catch (e) {
      console.error('Polling error', e)
      nextActive.push(job)
    }
  }
  activeJobs.value = nextActive
}

function startPolling() {
  if (timer) return
  timer = setInterval(poll, 2000)
}

function stopPolling() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

onMounted(() => {
  if (activeJobs.value.length > 0) startPolling()
})

onBeforeUnmount(() => {
  stopPolling()
})

function getStatusType(status: string) {
  switch (status) {
    case 'SUCCEEDED': return 'success'
    case 'FAILED': return 'danger'
    case 'RUNNING': return 'primary'
    default: return 'info'
  }
}
</script>

<template>
  <el-space direction="vertical" style="width: 100%" :size="16" fill>
    <el-tabs v-model="activeIndex" type="card">
      <el-tab-pane v-for="idx in indices" :key="idx.symbol" :label="idx.name" :name="idx.symbol" />
    </el-tabs>

    <el-card shadow="never" style="border-radius: 12px">
      <template #header>
        <div style="font-weight: 700">数据同步管理 ({{ indices.find(i => i.symbol === activeIndex)?.name }})</div>
      </template>
      <div style="display: flex; gap: 24px; flex-wrap: wrap">
        <div style="flex: 1; min-width: 300px">
          <div style="font-weight: 600; margin-bottom: 12px">数据同步 ({{ indices.find(i => i.symbol === activeIndex)?.name }})</div>
          <el-space wrap>
            <el-button type="primary" @click="handleSyncWiki" :loading="loading">
              同步成分股Wiki
            </el-button>
            <el-button type="warning" @click="handleSyncFundamentals" :loading="loading">
              同步基本面/分红
            </el-button>
            <el-button type="success" @click="handleSyncPrices" :loading="loading">
              全量价格同步 (2016-)
            </el-button>
          </el-space>
          <div style="color: #667085; font-size: 13px; margin-top: 12px">
            Wiki同步：抓取成分股列表及简介；基本面/分红：抓取市值/股本/分红拆股；价格同步：抓取该指数下所有股票的历史日线。
          </div>
        </div>
        
        <div style="flex: 1; min-width: 300px">
          <div style="font-weight: 600; margin-bottom: 12px">批量股票同步 ({{ activeIndex }})</div>
          <el-input
            v-model="bulkSymbols"
            type="textarea"
            :rows="3"
            placeholder="输入代码（HK股填 00700 格式），用逗号、换行分隔"
          />
          <el-button
            type="primary"
            style="margin-top: 12px"
            @click="handleSyncBulk"
            :loading="loading"
          >
            启动批量同步
          </el-button>
        </div>
      </div>
    </el-card>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never" style="border-radius: 12px">
          <template #header>
            <div style="font-weight: 700">正在运行 ({{ activeJobs.length }})</div>
          </template>
          <el-table :data="activeJobs" style="width: 100%" empty-text="暂无运行中的任务">
            <el-table-column prop="jobId" label="ID" width="100" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="startedAt" label="启动时间" width="160">
              <template #default="{ row }">
                {{ row.startedAt ? new Date(row.startedAt).toLocaleString() : '-' }}
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      
      <el-col :span="12">
        <el-card shadow="never" style="border-radius: 12px">
          <template #header>
            <div style="font-weight: 700">已完成任务</div>
          </template>
          <el-table :data="finishedJobs" style="width: 100%" height="400">
            <el-table-column prop="jobId" label="ID" width="100" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="exitCode" label="结果" width="70">
              <template #default="{ row }">
                <span :style="{ color: row.exitCode === 0 ? '#26a69a' : '#ef5350' }">
                  {{ row.exitCode === 0 ? '成功' : '失败' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="操作">
              <template #default="{ row }">
                <el-popover placement="left" :width="600" trigger="click">
                  <template #reference>
                    <el-button link type="primary">查看日志</el-button>
                  </template>
                  <pre style="background: #1e1e1e; color: #d4d4d4; padding: 12px; border-radius: 4px; font-size: 12px; max-height: 400px; overflow: auto; margin: 0">
                    {{ row.outputTail || '无日志输出' }}
                  </pre>
                </el-popover>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </el-space>
</template>
