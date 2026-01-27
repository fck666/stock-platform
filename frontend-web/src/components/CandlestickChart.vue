<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { BarDto } from '../api/market'

const props = defineProps<{
  bars: BarDto[]
  height?: number
  title?: string
}>()

const elRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

function buildOption(bars: BarDto[], title?: string): echarts.EChartsOption {
  const categories = bars.map((b) => b.date)
  const candle = bars.map((b) => [b.open ?? 0, b.close ?? 0, b.low ?? 0, b.high ?? 0])
  const volumes = bars.map((b) => {
    const up = (b.close ?? 0) >= (b.open ?? 0)
    return {
      value: b.volume ?? 0,
      itemStyle: { color: up ? '#26a69a' : '#ef5350' },
    }
  })

  return {
    title: title ? { text: title, left: 10, top: 6, textStyle: { fontSize: 14 } } : undefined,
    backgroundColor: '#ffffff',
    animation: false,
    grid: [
      { left: 56, right: 24, top: 44, height: '62%' },
      { left: 56, right: 24, top: '76%', height: '16%' },
    ],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      backgroundColor: 'rgba(17,17,17,0.9)',
      borderWidth: 0,
      textStyle: { color: '#fff' },
    },
    axisPointer: { link: [{ xAxisIndex: 'all' }] },
    xAxis: [
      {
        type: 'category',
        data: categories,
        boundaryGap: true,
        axisLine: { lineStyle: { color: '#cfd5e2' } },
        axisLabel: { color: '#667085' },
        axisTick: { show: false },
        splitLine: { show: false },
        min: 'dataMin',
        max: 'dataMax',
      },
      {
        type: 'category',
        gridIndex: 1,
        data: categories,
        boundaryGap: true,
        axisLine: { lineStyle: { color: '#cfd5e2' } },
        axisLabel: { show: false },
        axisTick: { show: false },
        splitLine: { show: false },
        min: 'dataMin',
        max: 'dataMax',
      },
    ],
    yAxis: [
      {
        scale: true,
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#667085' },
        splitLine: { lineStyle: { color: '#eef1f6' } },
      },
      {
        gridIndex: 1,
        scale: true,
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#667085' },
        splitLine: { lineStyle: { color: '#eef1f6' } },
      },
    ],
    dataZoom: [
      { type: 'inside', xAxisIndex: [0, 1], start: 50, end: 100 },
      { type: 'slider', xAxisIndex: [0, 1], top: '93%', height: 24 },
    ],
    series: [
      {
        name: 'K',
        type: 'candlestick',
        data: candle,
        itemStyle: {
          color: '#26a69a',
          color0: '#ef5350',
          borderColor: '#26a69a',
          borderColor0: '#ef5350',
        },
      },
      {
        name: 'Volume',
        type: 'bar',
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: volumes,
        barWidth: '60%',
      },
    ],
  }
}

function render() {
  if (!chart || !elRef.value) return
  chart.setOption(buildOption(props.bars, props.title), true)
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
  () => props.bars,
  () => render(),
  { deep: true },
)

onBeforeUnmount(() => {
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div ref="elRef" :style="{ width: '100%', height: `${props.height ?? 520}px`, borderRadius: '12px' }" />
</template>
