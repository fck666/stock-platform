<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { ElTable } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import { createStock, listIndices, listStocks, syncStocks, type IndexListItemDto, type StockListItemDto } from '../api/market'
import { auth } from '../auth/auth'

const router = useRouter()

// --- State ---
const loading = ref(false)
const syncing = ref(false)
const query = ref('')

// Pagination & Sorting
const page = ref(1)
const size = ref(50)
const total = ref(0)
const sortBy = ref<'symbol' | 'name'>('symbol')
const sortDir = ref<'asc' | 'desc'>('asc')

// Table Data
const rows = ref<StockListItemDto[]>([])
const selected = ref<StockListItemDto[]>([])
const tableRef = ref<InstanceType<typeof ElTable> | null>(null)

// --- Computed ---
const selectedSymbols = computed(() => selected.value.map((r) => r.symbol))
const allSelectedOnPage = computed(() => rows.value.length > 0 && selected.value.length === rows.value.length)
const canSync = computed(() => auth.hasPermission('data.sync.execute'))
const canWriteStock = computed(() => auth.hasPermission('admin.stock.write'))

// --- Index Filter State ---
const indices = ref<IndexListItemDto[]>([])
const activeIndex = ref<string>('^SPX')
const activeIndexName = computed(() => {
  if (activeIndex.value === 'ALL') return '全部'
  const found = indices.value.find(i => i.symbol === activeIndex.value)
  return found?.name || found?.symbol || activeIndex.value
})
const syncIndex = ref<string>('^SPX')

// --- Create Dialog State ---
const showCreate = ref(false)
const createSymbol = ref('')
const createName = ref('')
const createWikiUrl = ref('')
const createIndexSymbols = ref<string[]>([])

const selectableIndices = computed(() => indices.value.map(i => i.symbol))
const hasIndicesLoaded = computed(() => indices.value.length > 0)

/**
 * Load stocks list from API based on current filters and pagination.
 */
async function load() {
  loading.value = true
  try {
    const res = await listStocks({
      index: activeIndex.value,
      query: query.value.trim() || undefined,
      page: page.value - 1, // API is 0-based
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

/**
 * Trigger sync for selected stocks.
 */
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

/**
 * Switch the active index filter and reload data.
 */
function changeIndex(sym: string) {
  if (activeIndex.value === sym) return
  activeIndex.value = sym
  if (sym !== 'ALL') {
    syncIndex.value = sym
  }
  page.value = 1
  load()
}

async function loadIndices() {
  try {
    const list = await listIndices()
    indices.value = list
    if (activeIndex.value !== 'ALL' && !list.find(i => i.symbol === activeIndex.value)) {
      activeIndex.value = 'ALL'
    }
    if (!list.find(i => i.symbol === syncIndex.value)) {
      syncIndex.value = list[0]?.symbol || '^SPX'
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? '加载指数失败')
  }
}

async function submitCreateStock() {
  const symbol = createSymbol.value.trim()
  if (!symbol) {
    ElMessage.warning('请填写股票代码')
    return
  }
  try {
    await createStock({
      symbol,
      name: createName.value.trim() || undefined,
      wikiUrl: createWikiUrl.value.trim() || undefined,
      indexSymbols: createIndexSymbols.value.length > 0 ? createIndexSymbols.value : undefined,
    })
    ElMessage.success('已添加股票')
    showCreate.value = false
    createSymbol.value = ''
    createName.value = ''
    createWikiUrl.value = ''
    createIndexSymbols.value = []
    page.value = 1
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? '添加失败')
  }
}

onMounted(() => {
  loadIndices().then(load)
})
</script>

<template>
  <el-space direction="vertical" class="page" :size="16" fill>
    <el-card shadow="never" style="border-radius: 12px">
      <el-space wrap>
        <el-button
          :type="activeIndex === 'ALL' ? 'primary' : 'default'"
          @click="changeIndex('ALL')"
        >
          全部
        </el-button>
        <el-button
          v-for="idx in indices"
          :key="idx.symbol"
          :type="activeIndex === idx.symbol ? 'primary' : 'default'"
          @click="changeIndex(idx.symbol as any)"
          class="font-mono"
        >
          {{ idx.name || idx.symbol }}
        </el-button>
      </el-space>
    </el-card>

    <PageHeader
      :title="`股票列表 (${activeIndexName})`"
      subtitle="支持搜索、分页、选择当前页并批量同步日线数据"
    >
      <el-button v-if="canWriteStock" :disabled="!hasIndicesLoaded" @click="showCreate = true">增加股票</el-button>
      <el-input
        v-model="query"
        placeholder="搜索：股票代码 / 公司名 / 简称"
        clearable
        style="width: 280px"
        @keyup.enter="page = 1; load()"
      />
      <el-button :loading="loading" @click="page = 1; load()">搜索</el-button>
      <template v-if="canSync">
        <el-button :disabled="rows.length === 0" @click="toggleSelectAllCurrentPage">
          {{ allSelectedOnPage ? '取消全选' : '全选当前页' }}
        </el-button>
        <el-select v-model="syncIndex" size="small" style="width: 160px" :disabled="activeIndex !== 'ALL'">
          <el-option v-for="idx in indices" :key="idx.symbol" :label="`同步：${idx.name || idx.symbol}`" :value="idx.symbol" />
        </el-select>
        <el-button type="primary" :loading="syncing" @click="syncSelected">同步数据</el-button>
        <div v-if="activeIndex === 'ALL'" class="text-muted" style="width: 100%; font-size: 12px">
          同步时请选择指数范围
        </div>
      </template>
    </PageHeader>

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
        <el-table-column prop="symbol" label="代码" width="110" sortable="custom">
          <template #default="{ row }">
            <span class="font-mono" style="font-weight: 600">{{ row.symbol }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="公司" min-width="220" sortable="custom" show-overflow-tooltip />
        <el-table-column prop="gicsSector" label="行业" width="140" show-overflow-tooltip />
        <el-table-column prop="gicsSubIndustry" label="子行业" min-width="200" show-overflow-tooltip />
        <el-table-column prop="headquarters" label="总部" min-width="180" show-overflow-tooltip />
        <el-table-column label="简介" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="text-muted">{{ row.wikiDescription || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="goDetail(row.symbol)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 12px">
        <div class="text-muted">已选择：{{ selectedSymbols.length }} 只</div>
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

  <el-dialog v-model="showCreate" title="增加股票" width="520px">
    <el-form label-width="92px">
      <el-form-item label="股票代码">
        <el-input v-model="createSymbol" placeholder="例如：AAPL / 00005" />
      </el-form-item>
      <el-form-item label="名称">
        <el-input v-model="createName" placeholder="可不填" />
      </el-form-item>
      <el-form-item label="Wiki URL">
        <el-input v-model="createWikiUrl" placeholder="可不填" />
      </el-form-item>
      <el-form-item label="所属指数">
        <el-select v-model="createIndexSymbols" multiple filterable style="width: 100%" placeholder="可不选">
          <el-option v-for="sym in selectableIndices" :key="sym" :label="sym" :value="sym" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-space>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="submitCreateStock">确定</el-button>
      </el-space>
    </template>
  </el-dialog>
</template>
