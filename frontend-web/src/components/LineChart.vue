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
let onThemeChange: (() => void) | null = null

function cssVar(name: string, fallback: string) {
  if (typeof window === 'undefined') return fallback
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return v || fallback
}

function resolveColor(input: string | undefined, fallback: string) {
  if (!input) return fallback
  const s = input.trim()
  if (s.startsWith('var(')) {
    const m = s.match(/var\((--[^)]+)\)/)
    if (m?.[1]) return cssVar(m[1], fallback)
  }
  return s
}

function buildOption(): echarts.EChartsOption {
  const bg = cssVar('--el-bg-color', '#ffffff')
  const muted = cssVar('--app-muted', '#667085')
  const border = cssVar('--app-border', '#cfd5e2')
  const gridLine = cssVar('--el-border-color-lighter', '#eef1f6')
  const tooltipBg = cssVar('--el-bg-color-overlay', 'rgba(17,17,17,0.9)')
  const tooltipText = cssVar('--el-text-color-primary', '#fff')
  const accent = cssVar('--app-accent', '#5b8ff9')

  const hasData = props.dates.length > 0 && props.series.some(s => s.data.some(v => v !== null && v !== undefined))
  if (!hasData) {
    return {
      title: props.title ? { text: props.title, left: 10, top: 6, textStyle: { fontSize: 14 } } : undefined,
      backgroundColor: bg,
      graphic: {
        type: 'text',
        left: 'center',
        top: 'middle',
        style: { text: '暂无数据', fill: muted, fontSize: 14 },
      },
    }
  }

  return {
    title: props.title ? { text: props.title, left: 10, top: 6, textStyle: { fontSize: 14 } } : undefined,
    backgroundColor: bg,
    animation: false,
    grid: { left: 56, right: 20, top: 44, bottom: 36 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line' },
      backgroundColor: tooltipBg,
      borderWidth: 0,
      textStyle: { color: tooltipText },
    },
    xAxis: {
      type: 'category',
      data: props.dates,
      boundaryGap: false,
      axisLine: { lineStyle: { color: border } },
      axisLabel: { color: muted },
      axisTick: { show: false },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      scale: true,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: muted, margin: 10 },
      splitLine: { lineStyle: { color: gridLine } },
      name: props.yLabel,
      nameTextStyle: { color: muted, padding: [0, 0, 0, 0] },
    },
    series: props.series.map((s) => ({
      name: s.name,
      type: 'line',
      data: s.data,
      showSymbol: false,
      smooth: true,
      lineStyle: { width: 2, color: resolveColor(s.color, accent) },
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

  onThemeChange = () => render()
  window.addEventListener('themechange', onThemeChange)
  onBeforeUnmount(() => {
    if (onThemeChange) window.removeEventListener('themechange', onThemeChange)
  })
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
