<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import {
  createTradePlan,
  deleteTradePlan,
  listTradePlans,
  updateTradePlan,
  type TradePlanDto,
} from '../api/market'

const loading = ref(false)
const rows = ref<TradePlanDto[]>([])
const statusFilter = ref<'ALL' | TradePlanDto['status']>('ALL')
const statusOptions = ['ALL', 'PLANNED', 'OPEN', 'CLOSED', 'CANCELLED']

const showEdit = ref(false)
const editingId = ref<number | null>(null)
const formSymbol = ref('')
const formDirection = ref<'LONG' | 'SHORT'>('LONG')
const formStatus = ref<TradePlanDto['status']>('PLANNED')
const formStartDate = ref<string>('')
const formEntryPrice = ref<number | null>(null)
const formEntryLow = ref<number | null>(null)
const formEntryHigh = ref<number | null>(null)
const formStopPrice = ref<number | null>(null)
const formTargetPrice = ref<number | null>(null)
const formNote = ref('')

const filtered = computed(() => {
  if (statusFilter.value === 'ALL') return rows.value
  return rows.value.filter(r => r.status === statusFilter.value)
})

function fmtPct(v: number | null) {
  if (v == null) return '-'
  return `${(v * 100).toFixed(2)}%`
}

function openCreate() {
  editingId.value = null
  formSymbol.value = ''
  formDirection.value = 'LONG'
  formStatus.value = 'PLANNED'
  formStartDate.value = ''
  formEntryPrice.value = null
  formEntryLow.value = null
  formEntryHigh.value = null
  formStopPrice.value = null
  formTargetPrice.value = null
  formNote.value = ''
  showEdit.value = true
}

function openEdit(row: TradePlanDto) {
  editingId.value = row.id
  formSymbol.value = row.symbol
  formDirection.value = row.direction
  formStatus.value = row.status
  formStartDate.value = row.startDate || ''
  formEntryPrice.value = row.entryPrice
  formEntryLow.value = row.entryLow
  formEntryHigh.value = row.entryHigh
  formStopPrice.value = row.stopPrice
  formTargetPrice.value = row.targetPrice
  formNote.value = row.note || ''
  showEdit.value = true
}

async function load() {
  loading.value = true
  try {
    rows.value = await listTradePlans()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? '加载失败')
  } finally {
    loading.value = false
  }
}

async function submit() {
  const symbol = formSymbol.value.trim().toUpperCase()
  if (!symbol) {
    ElMessage.warning('请输入股票代码')
    return
  }
  try {
    if (editingId.value == null) {
      await createTradePlan({
        symbol,
        direction: formDirection.value,
        status: formStatus.value,
        startDate: formStartDate.value || undefined,
        entryPrice: formEntryPrice.value ?? undefined,
        entryLow: formEntryLow.value ?? undefined,
        entryHigh: formEntryHigh.value ?? undefined,
        stopPrice: formStopPrice.value ?? undefined,
        targetPrice: formTargetPrice.value ?? undefined,
        note: formNote.value.trim() || undefined,
      })
      ElMessage.success('已创建')
    } else {
      await updateTradePlan(editingId.value, {
        direction: formDirection.value,
        status: formStatus.value,
        startDate: formStartDate.value || undefined,
        entryPrice: formEntryPrice.value ?? undefined,
        entryLow: formEntryLow.value ?? undefined,
        entryHigh: formEntryHigh.value ?? undefined,
        stopPrice: formStopPrice.value ?? undefined,
        targetPrice: formTargetPrice.value ?? undefined,
        note: formNote.value.trim() || undefined,
      })
      ElMessage.success('已更新')
    }
    showEdit.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? '保存失败')
  }
}

async function remove(id: number) {
  try {
    await deleteTradePlan(id)
    ElMessage.success('已删除')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? '删除失败')
  }
}

onMounted(() => {
  load()
})
</script>

<template>
  <el-space direction="vertical" class="page" :size="16" fill>
    <PageHeader title="交易计划" subtitle="记录计划、跟踪盈亏、快速复盘">
      <el-segmented v-model="statusFilter" :options="statusOptions" />
      <el-button :loading="loading" @click="load">刷新</el-button>
      <el-button type="primary" @click="openCreate">新增计划</el-button>
    </PageHeader>

    <el-card shadow="never" style="border-radius: 12px">
      <el-table v-loading="loading" :data="filtered" row-key="id" style="width: 100%">
        <el-table-column prop="symbol" label="代码" width="110" />
        <el-table-column prop="name" label="名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="方向" width="80">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'LONG' ? 'success' : 'danger'">{{ row.direction }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'OPEN' ? 'primary' : row.status === 'PLANNED' ? 'info' : row.status === 'CLOSED' ? 'success' : 'warning'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startDate" label="开始" width="110" />
        <el-table-column label="入场" width="110" align="right">
          <template #default="{ row }">{{ row.entryPrice == null ? '-' : row.entryPrice.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="止损" width="110" align="right">
          <template #default="{ row }">{{ row.stopPrice == null ? '-' : row.stopPrice.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="目标" width="110" align="right">
          <template #default="{ row }">{{ row.targetPrice == null ? '-' : row.targetPrice.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="最新价" width="110" align="right">
          <template #default="{ row }">{{ row.lastClose == null ? '-' : row.lastClose.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="盈亏" width="110" align="right">
          <template #default="{ row }">
            <span :style="{ color: row.pnlPct != null && row.pnlPct >= 0 ? 'var(--app-success)' : 'var(--app-danger)' }">{{ fmtPct(row.pnlPct) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="触发" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.hitStop" type="danger" size="small">止损</el-tag>
            <el-tag v-else-if="row.hitTarget" type="success" size="small">目标</el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="note" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-space>
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="remove(row.id)">删除</el-button>
            </el-space>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </el-space>

  <el-dialog v-model="showEdit" :title="editingId == null ? '新增计划' : '编辑计划'" width="560px">
    <el-form label-width="92px">
      <el-form-item label="股票代码">
        <el-input v-model="formSymbol" :disabled="editingId != null" placeholder="例如：AAPL / 00005" />
      </el-form-item>
      <el-form-item label="方向">
        <el-select v-model="formDirection" style="width: 100%">
          <el-option label="做多 LONG" value="LONG" />
          <el-option label="做空 SHORT" value="SHORT" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="formStatus" style="width: 100%">
          <el-option label="计划 PLANNED" value="PLANNED" />
          <el-option label="持仓 OPEN" value="OPEN" />
          <el-option label="结束 CLOSED" value="CLOSED" />
          <el-option label="取消 CANCELLED" value="CANCELLED" />
        </el-select>
      </el-form-item>
      <el-form-item label="开始日期">
        <el-date-picker v-model="formStartDate" type="date" value-format="YYYY-MM-DD" placeholder="可不填" style="width: 100%" />
      </el-form-item>
      <el-form-item label="入场价">
        <el-input-number v-model="formEntryPrice" :precision="4" :step="0.1" style="width: 100%" />
      </el-form-item>
      <el-form-item label="入场区间">
        <el-space style="width: 100%">
          <el-input-number v-model="formEntryLow" :precision="4" :step="0.1" style="flex: 1" placeholder="低" />
          <span class="text-muted">~</span>
          <el-input-number v-model="formEntryHigh" :precision="4" :step="0.1" style="flex: 1" placeholder="高" />
        </el-space>
      </el-form-item>
      <el-form-item label="止损价">
        <el-input-number v-model="formStopPrice" :precision="4" :step="0.1" style="width: 100%" />
      </el-form-item>
      <el-form-item label="目标价">
        <el-input-number v-model="formTargetPrice" :precision="4" :step="0.1" style="width: 100%" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="formNote" type="textarea" :rows="4" placeholder="理由/形态/计划细节" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-space>
        <el-button @click="showEdit = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </el-space>
    </template>
  </el-dialog>
</template>
