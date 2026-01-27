<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { ElTable } from 'element-plus'
import { listStocks, syncStocks, type StockListItemDto } from '../api/market'

const router = useRouter()

const loading = ref(false)
const syncing = ref(false)
const query = ref('')

const page = ref(1)
const size = ref(50)
const total = ref(0)

const rows = ref<StockListItemDto[]>([])
const selected = ref<StockListItemDto[]>([])
const tableRef = ref<InstanceType<typeof ElTable> | null>(null)

const selectedSymbols = computed(() => selected.value.map((r) => r.symbol))
const allSelectedOnPage = computed(() => rows.value.length > 0 && selected.value.length === rows.value.length)

async function load() {
  loading.value = true
  try {
    const res = await listStocks({
      query: query.value.trim() || undefined,
      page: page.value - 1,
      size: size.value,
    })
    rows.value = res.items
    total.value = res.total
    selected.value = []
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? '加载失败')
  } finally {
    loading.value = false
  }
}

function goDetail(symbol: string) {
  router.push(`/stocks/${encodeURIComponent(symbol)}`)
}

function toggleSelectAllCurrentPage() {
  if (!tableRef.value) return
  if (allSelectedOnPage.value) {
    tableRef.value.clearSelection()
  } else {
    tableRef.value.toggleAllSelection()
  }
}

function onSelectionChange(val: StockListItemDto[]) {
  selected.value = val
}

async function syncSelected() {
  if (selectedSymbols.value.length === 0) {
    ElMessage.warning('请先选择要同步的股票')
    return
  }
  syncing.value = true
  try {
    const job = await syncStocks(selectedSymbols.value)
    ElMessage.success(`已触发同步任务：${job.jobId}`)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? '同步失败')
  } finally {
    syncing.value = false
  }
}

onMounted(() => {
  load()
})
</script>

<template>
  <el-space direction="vertical" style="width: 100%" :size="16">
    <el-card shadow="never" style="border-radius: 12px">
      <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap">
        <div>
          <div style="font-size: 16px; font-weight: 700">标普500股票列表</div>
          <div style="color: #667085; margin-top: 4px">支持搜索、分页、选择当前页并批量同步日线数据</div>
        </div>
        <el-space>
          <el-input
            v-model="query"
            placeholder="搜索：股票代码 / 公司名 / 简称"
            clearable
            style="width: 280px"
            @keyup.enter="page = 1; load()"
          />
          <el-button :loading="loading" @click="page = 1; load()">搜索</el-button>
          <el-button :disabled="rows.length === 0" @click="toggleSelectAllCurrentPage">
            {{ allSelectedOnPage ? '取消全选' : '全选当前页' }}
          </el-button>
          <el-button type="primary" :loading="syncing" @click="syncSelected">同步数据</el-button>
        </el-space>
      </div>
    </el-card>

    <el-card shadow="never" style="border-radius: 12px">
      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="rows"
        row-key="symbol"
        style="width: 100%"
        height="640"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="44" />
        <el-table-column prop="symbol" label="代码" width="90" />
        <el-table-column prop="name" label="公司" min-width="220" show-overflow-tooltip />
        <el-table-column prop="gicsSector" label="行业" width="140" show-overflow-tooltip />
        <el-table-column prop="gicsSubIndustry" label="子行业" min-width="200" show-overflow-tooltip />
        <el-table-column prop="headquarters" label="总部" min-width="180" show-overflow-tooltip />
        <el-table-column label="简介" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <span style="color: #667085">{{ row.wikiDescription || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="goDetail(row.symbol)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 12px">
        <div style="color: #667085">已选择：{{ selectedSymbols.length }} 只</div>
        <el-pagination
          background
          layout="prev, pager, next, jumper"
          :current-page="page"
          :page-size="size"
          :total="total"
          @current-change="(p:number)=>{ page = p; load() }"
        />
      </div>
    </el-card>
  </el-space>
</template>
