<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps<{
  title?: string
  height?: number
  dates: string[]
  series: Array<{ name: string; data: Array<number | null>; color?: string }>
  yLabel?: string
}>()

const elRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

function buildOption(): echarts.EChartsOption {
  const hasData = props.dates.length > 0 && props.series.some(s => s.data.some(v => v !== null && v !== undefined))
  if (!hasData) {
    return {
      title: props.title ? { text: props.title, left: 10, top: 6, textStyle: { fontSize: 14 } } : undefined,
      backgroundColor: '#ffffff',
      graphic: {
        type: 'text',
        left: 'center',
        top: 'middle',
        style: { text: '暂无数据', fill: '#667085', fontSize: 14 },
      },
    }
  }

  return {
    title: props.title ? { text: props.title, left: 10, top: 6, textStyle: { fontSize: 14 } } : undefined,
    backgroundColor: '#ffffff',
    animation: false,
    grid: { left: 56, right: 20, top: 44, bottom: 36 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line' },
      backgroundColor: 'rgba(17,17,17,0.9)',
      borderWidth: 0,
      textStyle: { color: '#fff' },
    },
    xAxis: {
      type: 'category',
      data: props.dates,
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#cfd5e2' } },
      axisLabel: { color: '#667085' },
      axisTick: { show: false },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      scale: true,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#667085', margin: 10 },
      splitLine: { lineStyle: { color: '#eef1f6' } },
      name: props.yLabel,
      nameTextStyle: { color: '#667085', padding: [0, 0, 0, 0] },
    },
    series: props.series.map((s) => ({
      name: s.name,
      type: 'line',
      data: s.data,
      showSymbol: false,
      smooth: true,
      lineStyle: { width: 2, color: s.color || '#5b8ff9' },
      emphasis: { focus: 'series' },
    })),
  }
}

function render() {
  if (!chart || !elRef.value) return
  chart.setOption(buildOption(), true)
}

onMounted(() => {
  if (!elRef.value) return
  chart = echarts.init(elRef.value)
  render()
  const onResize = () => chart?.resize()
  window.addEventListener('resize', onResize)
  onBeforeUnmount(() => window.removeEventListener('resize', onResize))
})

watch(
  () => [props.dates, props.series, props.title, props.yLabel, props.height],
  () => render(),
  { deep: true },
)

onBeforeUnmount(() => {
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div
    ref="elRef"
    :style="{
      width: '100%',
      height: `${props.height ?? 260}px`,
      borderRadius: '12px',
    }"
  />
</template>

