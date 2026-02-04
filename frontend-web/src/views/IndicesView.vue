<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import {
  createIndex,
  getIndexConstituents,
  listIndices,
  replaceIndexConstituents,
  type IndexListItemDto,
} from '../api/market'

const loading = ref(false)
const rows = ref<IndexListItemDto[]>([])

const showCreate = ref(false)
const createSymbol = ref('')
const createName = ref('')
const createWikiUrl = ref('')
const createInitialStocksText = ref('')

const showEdit = ref(false)
const editIndexSymbol = ref<string | null>(null)
const editStocksText = ref('')
const editLoading = ref(false)
const quickSymbol = ref('')

const sortedIndices = computed(() => rows.value.slice().sort((a, b) => a.symbol.localeCompare(b.symbol)))

function parseSymbols(text: string): string[] {
  const parts = text
    .split(/[\n,，\s]+/)
    .map((x) => x.trim())
    .filter(Boolean)
  return Array.from(new Set(parts))
}

function setSymbols(symbols: string[]) {
  editStocksText.value = symbols.join('\n')
}

function addOne() {
  if (editLoading.value) return
  const sym = quickSymbol.value.trim()
  if (!sym) {
    ElMessage.warning('请输入股票代码')
    return
  }
  const symbols = parseSymbols(editStocksText.value)
  if (symbols.includes(sym)) {
    ElMessage.info('已存在该股票')
    return
  }
  symbols.push(sym)
  symbols.sort((a, b) => a.localeCompare(b))
  setSymbols(symbols)
  quickSymbol.value = ''
}

function removeOne() {
  if (editLoading.value) return
  const sym = quickSymbol.value.trim()
  if (!sym) {
    ElMessage.warning('请输入股票代码')
    return
  }
  const symbols = parseSymbols(editStocksText.value).filter((s) => s !== sym)
  setSymbols(symbols)
  quickSymbol.value = ''
}

async function load() {
  loading.value = true
  try {
    rows.value = await listIndices()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? '加载失败')
  } finally {
    loading.value = false
  }
}

async function submitCreate() {
  const symbol = createSymbol.value.trim()
  if (!symbol) {
    ElMessage.warning('请填写指数代码')
    return
  }
  try {
    await createIndex({
      symbol,
      name: createName.value.trim() || undefined,
      wikiUrl: createWikiUrl.value.trim() || undefined,
      initialStockSymbols: parseSymbols(createInitialStocksText.value),
    })
    ElMessage.success('已添加指数')
    showCreate.value = false
    createSymbol.value = ''
    createName.value = ''
    createWikiUrl.value = ''
    createInitialStocksText.value = ''
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? '添加失败')
  }
}

async function openEdit(symbol: string) {
  showEdit.value = true
  editIndexSymbol.value = symbol
  editStocksText.value = ''
  quickSymbol.value = ''
  editLoading.value = true
  try {
    const stocks = await getIndexConstituents(symbol)
    editStocksText.value = stocks.join('\n')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? '加载成分失败')
  } finally {
    editLoading.value = false
  }
}

async function submitEdit() {
  if (!editIndexSymbol.value) return
  editLoading.value = true
  try {
    const symbols = parseSymbols(editStocksText.value)
    await replaceIndexConstituents(editIndexSymbol.value, symbols)
    ElMessage.success('已更新成分股')
    showEdit.value = false
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? '更新失败')
  } finally {
    editLoading.value = false
  }
}

onMounted(() => {
  load()
})
</script>

<template>
  <el-space direction="vertical" class="page" :size="16" fill>
    <PageHeader title="指数管理" subtitle="新增指数、设置初始成分股、后续维护成分股">
      <el-button :loading="loading" @click="load">刷新</el-button>
      <el-button type="primary" @click="showCreate = true">增加指数</el-button>
    </PageHeader>

    <el-card shadow="never" style="border-radius: 12px">
      <el-table v-loading="loading" :data="sortedIndices" row-key="symbol" style="width: 100%">
        <el-table-column prop="symbol" label="指数代码" width="160" />
        <el-table-column prop="name" label="名称" min-width="240" show-overflow-tooltip />
        <el-table-column prop="wikiUrl" label="Wiki URL" min-width="280" show-overflow-tooltip />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row.symbol)">成分股</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </el-space>

  <el-dialog v-model="showCreate" title="增加指数" width="560px">
    <el-form label-width="104px">
      <el-form-item label="指数代码">
        <el-input v-model="createSymbol" placeholder="例如：^SPX / ^HSI / ^HK_MYINDEX" />
      </el-form-item>
      <el-form-item label="名称">
        <el-input v-model="createName" placeholder="可不填" />
      </el-form-item>
      <el-form-item label="Wiki URL">
        <el-input v-model="createWikiUrl" placeholder="可不填" />
      </el-form-item>
      <el-form-item label="初始成分股">
        <el-input
          v-model="createInitialStocksText"
          type="textarea"
          :rows="6"
          placeholder="可不填；每行一个股票代码，或用逗号/空格分隔"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-space>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">确定</el-button>
      </el-space>
    </template>
  </el-dialog>

  <el-dialog v-model="showEdit" title="维护成分股" width="560px">
    <div class="text-muted" style="margin-bottom: 8px">
      指数：<span style="font-weight: 600">{{ editIndexSymbol }}</span>
    </div>
    <el-space wrap style="margin-bottom: 12px">
      <el-input v-model="quickSymbol" placeholder="输入股票代码，例如：AAPL / 00005" style="width: 260px" />
      <el-button @click="addOne" :disabled="editLoading">加入</el-button>
      <el-button @click="removeOne" :disabled="editLoading">移除</el-button>
    </el-space>
    <el-input v-model="editStocksText" type="textarea" :rows="10" :disabled="editLoading" />
    <template #footer>
      <el-space>
        <el-button @click="showEdit = false" :disabled="editLoading">取消</el-button>
        <el-button type="primary" @click="submitEdit" :loading="editLoading">保存</el-button>
      </el-space>
    </template>
  </el-dialog>
</template>
