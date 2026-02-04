<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import {
  createAlertRule,
  deleteAlertRule,
  evaluateAlerts,
  listAlertEvents,
  listAlertRules,
  updateAlertRule,
  type AlertEventDto,
  type AlertRuleDto,
} from '../api/market'

const loading = ref(false)
const evaluating = ref(false)
const rules = ref<AlertRuleDto[]>([])
const events = ref<AlertEventDto[]>([])

const showCreate = ref(false)
const formSymbol = ref('')
const formType = ref<AlertRuleDto['ruleType']>('PRICE_BREAKOUT')
const formEnabled = ref(true)
const formPriceLevel = ref<number | null>(null)
const formPriceDirection = ref<'ABOVE' | 'BELOW'>('ABOVE')
const formMaPeriod = ref<20 | 50 | 200>(50)
const formMaDirection = ref<'ABOVE' | 'BELOW'>('ABOVE')
const formVolumeMultiple = ref<number | null>(2)

const sortedRules = computed(() => rules.value.slice().sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)))

function resetForm() {
  formSymbol.value = ''
  formType.value = 'PRICE_BREAKOUT'
  formEnabled.value = true
  formPriceLevel.value = null
  formPriceDirection.value = 'ABOVE'
  formMaPeriod.value = 50
  formMaDirection.value = 'ABOVE'
  formVolumeMultiple.value = 2
}

function ruleSummary(r: AlertRuleDto) {
  if (r.ruleType === 'PRICE_BREAKOUT') {
    return `${r.priceDirection || '-'} ${r.priceLevel ?? '-'}`
  }
  if (r.ruleType === 'MA_CROSS') {
    return `${r.maDirection || '-'} MA${r.maPeriod ?? 50}`
  }
  return `≥ ${(r.volumeMultiple ?? 2).toFixed(2)}x 50日均量`
}

async function loadAll() {
  loading.value = true
  try {
    const [r, e] = await Promise.all([listAlertRules(), listAlertEvents({ limit: 50 })])
    rules.value = r
    events.value = e
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? err?.message ?? '加载失败')
  } finally {
    loading.value = false
  }
}

async function runEvaluate() {
  evaluating.value = true
  try {
    const res = await evaluateAlerts({ latestLimit: 50 })
    ElMessage.success(`触发 ${res.triggered} 条告警`)
    events.value = res.latestEvents
    rules.value = await listAlertRules()
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? err?.message ?? '执行失败')
  } finally {
    evaluating.value = false
  }
}

async function submitCreate() {
  const symbol = formSymbol.value.trim().toUpperCase()
  if (!symbol) {
    ElMessage.warning('请输入股票代码')
    return
  }
  try {
    const body: any = {
      symbol,
      ruleType: formType.value,
      enabled: formEnabled.value,
    }
    if (formType.value === 'PRICE_BREAKOUT') {
      body.priceLevel = formPriceLevel.value ?? undefined
      body.priceDirection = formPriceDirection.value
    }
    if (formType.value === 'MA_CROSS') {
      body.maPeriod = formMaPeriod.value
      body.maDirection = formMaDirection.value
    }
    if (formType.value === 'VOLUME_SURGE') {
      body.volumeMultiple = formVolumeMultiple.value ?? undefined
    }
    await createAlertRule(body)
    ElMessage.success('已创建')
    showCreate.value = false
    resetForm()
    await loadAll()
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? err?.message ?? '创建失败')
  }
}

async function toggleEnabled(r: AlertRuleDto) {
  try {
    await updateAlertRule(r.id, { enabled: r.enabled })
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? err?.message ?? '更新失败')
    await loadAll()
  }
}

async function removeRule(id: number) {
  try {
    await deleteAlertRule(id)
    ElMessage.success('已删除')
    await loadAll()
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? err?.message ?? '删除失败')
  }
}

onMounted(() => {
  loadAll()
})
</script>

<template>
  <el-space direction="vertical" class="page" :size="16" fill>
    <PageHeader title="告警" subtitle="价格突破 / 均线事件 / 放量">
      <el-button :loading="loading" @click="loadAll">刷新</el-button>
      <el-button type="primary" :loading="evaluating" @click="runEvaluate">检查告警</el-button>
      <el-button @click="showCreate = true">新增告警</el-button>
    </PageHeader>

    <el-card shadow="never" style="border-radius: 12px">
      <div style="font-weight: 700; margin-bottom: 12px">规则</div>
      <el-table v-loading="loading" :data="sortedRules" row-key="id" style="width: 100%">
        <el-table-column prop="symbol" label="代码" width="110" />
        <el-table-column prop="name" label="名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="ruleType" label="类型" width="130" />
        <el-table-column label="条件" min-width="200">
          <template #default="{ row }">
            <span style="color: var(--app-text)">{{ ruleSummary(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="90">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="() => toggleEnabled(row)" />
          </template>
        </el-table-column>
        <el-table-column prop="lastTriggeredDate" label="上次触发" width="120" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="removeRule(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" style="border-radius: 12px">
      <div style="display:flex; align-items:center; justify-content: space-between; gap: 12px; flex-wrap: wrap">
        <div style="font-weight: 700">最近触发</div>
        <div class="text-muted" style="font-size: 12px">点击“检查告警”会基于最新日线计算并生成事件</div>
      </div>
      <el-table :data="events" size="small" style="width: 100%; margin-top: 12px" height="360" empty-text="暂无告警事件">
        <el-table-column prop="createdAt" label="时间" width="170" show-overflow-tooltip />
        <el-table-column prop="symbol" label="代码" width="110" />
        <el-table-column prop="name" label="名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="barDate" label="交易日" width="110" />
        <el-table-column prop="message" label="内容" min-width="240" show-overflow-tooltip />
      </el-table>
    </el-card>
  </el-space>

  <el-dialog v-model="showCreate" title="新增告警" width="560px" @closed="resetForm">
    <el-form label-width="92px">
      <el-form-item label="股票代码">
        <el-input v-model="formSymbol" placeholder="例如：AAPL / 00005" />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="formType" style="width: 100%">
          <el-option label="价格突破" value="PRICE_BREAKOUT" />
          <el-option label="均线事件" value="MA_CROSS" />
          <el-option label="放量" value="VOLUME_SURGE" />
        </el-select>
      </el-form-item>
      <el-form-item label="启用">
        <el-switch v-model="formEnabled" />
      </el-form-item>

      <template v-if="formType === 'PRICE_BREAKOUT'">
        <el-form-item label="方向">
          <el-select v-model="formPriceDirection" style="width: 100%">
            <el-option label="上破" value="ABOVE" />
            <el-option label="下破" value="BELOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="价位">
          <el-input-number v-model="formPriceLevel" :precision="4" :step="0.1" style="width: 100%" />
        </el-form-item>
      </template>

      <template v-else-if="formType === 'MA_CROSS'">
        <el-form-item label="方向">
          <el-select v-model="formMaDirection" style="width: 100%">
            <el-option label="上穿" value="ABOVE" />
            <el-option label="下穿" value="BELOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="均线">
          <el-select v-model="formMaPeriod" style="width: 100%">
            <el-option label="MA20" :value="20" />
            <el-option label="MA50" :value="50" />
            <el-option label="MA200" :value="200" />
          </el-select>
        </el-form-item>
      </template>

      <template v-else>
        <el-form-item label="倍数">
          <el-input-number v-model="formVolumeMultiple" :precision="2" :step="0.1" style="width: 100%" />
        </el-form-item>
        <div class="text-muted" style="font-size: 12px; margin-left: 92px; margin-top: -8px">
          以 50 日均量为基准
        </div>
      </template>
    </el-form>
    <template #footer>
      <el-space>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">创建</el-button>
      </el-space>
    </template>
  </el-dialog>
</template>
