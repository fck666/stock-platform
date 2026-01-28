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
const sortBy = ref<'symbol' | 'name'>('symbol')
const sortDir = ref<'asc' | 'desc'>('asc')

const rows = ref<StockListItemDto[]>([])
const selected = ref<StockListItemDto[]>([])
const tableRef = ref<InstanceType<typeof ElTable> | null>(null)

const selectedSymbols = computed(() => selected.value.map((r) => r.symbol))
const allSelectedOnPage = computed(() => rows.value.length > 0 && selected.value.length === rows.value.length)

const indices = [
  { symbol: 'ALL', name: '全部' },
  { symbol: '^SPX', name: 'S&P 500' },
  { symbol: '^HSI', name: '恒生指数' },
  { symbol: '^HSTECH', name: '恒生科技' },
]
const activeIndex = ref<'ALL' | '^SPX' | '^HSI' | '^HSTECH'>('^SPX')
const activeIndexName = computed(() => indices.find(i => i.symbol === activeIndex.value)?.name || activeIndex.value)
const syncIndex = ref<'^SPX' | '^HSI' | '^HSTECH'>('^SPX')

async function load() {
  loading.value = true
  try {
    const res = await listStocks({
      index: activeIndex.value,
      query: query.value.trim() || undefined,
      page: page.value - 1,
      size: size.value,
      sortBy: sortBy.value,
      sortDir: sortDir.value,
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
    const job = await syncStocks(selectedSymbols.value, syncIndex.value)
    ElMessage.success(`已触发同步任务：${job.jobId}`)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? '同步失败')
  } finally {
    syncing.value = false
  }
}

function onSortChange(e: any) {
  const prop = String(e?.prop || '')
  const order = e?.order as string | null | undefined
  if (!order) {
    sortBy.value = 'symbol'
    sortDir.value = 'asc'
  } else {
    if (prop === 'symbol') sortBy.value = 'symbol'
    if (prop === 'name') sortBy.value = 'name'
    sortDir.value = order === 'descending' ? 'desc' : 'asc'
  }
  page.value = 1
  load()
}

function changeIndex(sym: 'ALL' | '^SPX' | '^HSI' | '^HSTECH') {
  if (activeIndex.value === sym) return
  activeIndex.value = sym
  if (sym !== 'ALL') {
    syncIndex.value = sym
  }
  page.value = 1
  load()
}

onMounted(() => {
  load()
})
</script>

<template>
  <el-space direction="vertical" style="width: 100%" :size="16" fill>
    <el-card shadow="never" style="border-radius: 12px">
      <el-space wrap>
        <el-button
          v-for="idx in indices"
          :key="idx.symbol"
          :type="activeIndex === idx.symbol ? 'primary' : 'default'"
          @click="changeIndex(idx.symbol as any)"
        >
          {{ idx.name }}
        </el-button>
      </el-space>
    </el-card>

    <el-card shadow="never" style="border-radius: 12px">
      <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap">
        <div>
          <div style="font-size: 16px; font-weight: 700">股票列表 ({{ activeIndexName }})</div>
          <div style="color: #667085; margin-top: 4px">
            支持搜索、分页、选择当前页并批量同步日线数据
            <span v-if="activeIndex === 'ALL'">（同步时请选择指数范围）</span>
          </div>
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
          <el-select v-model="syncIndex" size="small" style="width: 140px" :disabled="activeIndex !== 'ALL'">
            <el-option label="同步：S&P 500" value="^SPX" />
            <el-option label="同步：恒生指数" value="^HSI" />
            <el-option label="同步：恒生科技" value="^HSTECH" />
          </el-select>
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
        max-height="calc(100vh - 290px)"
        @selection-change="onSelectionChange"
        @sort-change="onSortChange"
      >
        <el-table-column type="selection" width="44" />
        <el-table-column prop="symbol" label="代码" width="110" sortable="custom" />
        <el-table-column prop="name" label="公司" min-width="220" sortable="custom" show-overflow-tooltip />
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
          layout="sizes, prev, pager, next, jumper"
          :current-page="page"
          :page-size="size"
          :page-sizes="[10, 20, 50, 100, 200]"
          :total="total"
          @current-change="(p:number)=>{ page = p; load() }"
          @size-change="(s:number)=>{ size = s; page = 1; load() }"
        />
      </div>
    </el-card>
  </el-space>
</template>
