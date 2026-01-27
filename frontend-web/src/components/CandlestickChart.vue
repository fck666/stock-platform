<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { BarDto } from '../api/market'

const props = defineProps<{
  bars: BarDto[]
  height?: number
  title?: string
  windowStart?: number
  windowEnd?: number
  showSlider?: boolean
}>()

const elRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
const zoomStart = ref<number>(props.windowStart ?? 50)
const zoomEnd = ref<number>(props.windowEnd ?? 100)
const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad|iPod/.test(navigator.platform)

function clamp(n: number, min: number, max: number) {
  return Math.min(Math.max(n, min), max)
}

function applyZoom(start: number, end: number) {
  const s = clamp(start, 0, 100)
  const e = clamp(end, 0, 100)
  const safeStart = Math.min(s, e)
  const safeEnd = Math.max(s, e)
  zoomStart.value = safeStart
  zoomEnd.value = safeEnd

  if (!chart) return
  chart.dispatchAction({ type: 'dataZoom', dataZoomIndex: 0, start: safeStart, end: safeEnd })
  chart.dispatchAction({ type: 'dataZoom', dataZoomIndex: 1, start: safeStart, end: safeEnd })
}

function panBy(stepPercent: number) {
  const width = zoomEnd.value - zoomStart.value
  if (width <= 0) return
  const nextStart = clamp(zoomStart.value + stepPercent, 0, 100 - width)
  applyZoom(nextStart, nextStart + width)
}

function panLeft() {
  panBy(-5)
}

function panRight() {
  panBy(5)
}

function resetZoom() {
  applyZoom(props.windowStart ?? 50, props.windowEnd ?? 100)
}

defineExpose({ panLeft, panRight, resetZoom })

function onWheel(e: WheelEvent) {
  if (!chart || !elRef.value) return

  const elWidth = elRef.value.clientWidth || 1
  const range = zoomEnd.value - zoomStart.value
  if (range <= 0) return

  const absX = Math.abs(e.deltaX)
  const absY = Math.abs(e.deltaY)

  const shouldZoom =
    (isMac && e.ctrlKey) ||
    (!isMac && absY >= absX && absY > 0) ||
    (!isMac && e.ctrlKey)

  if (shouldZoom) {
    e.preventDefault()

    const ratio = clamp(e.offsetX / elWidth, 0, 1)
    const anchor = zoomStart.value + range * ratio

    const delta = e.deltaY
    const factor = Math.exp(delta * 0.001)
    const minWindow = 1.5
    const newWidth = clamp(range * factor, minWindow, 100)

    const newStart = clamp(anchor - newWidth * ratio, 0, 100 - newWidth)
    applyZoom(newStart, newStart + newWidth)
    return
  }

  if (absX > 0) {
    e.preventDefault()
    const shift = (e.deltaX / elWidth) * range * 1.2
    applyZoom(zoomStart.value + shift, zoomEnd.value + shift)
  }
}

const dragging = ref(false)
let dragStartX = 0
let dragStartZoomStart = 0
let dragStartZoomEnd = 0

function onPointerDown(e: PointerEvent) {
  if (!chart || !elRef.value) return
  if (e.button !== 0) return
  dragging.value = true
  dragStartX = e.clientX
  dragStartZoomStart = zoomStart.value
  dragStartZoomEnd = zoomEnd.value
  elRef.value.setPointerCapture(e.pointerId)
}

function onPointerMove(e: PointerEvent) {
  if (!dragging.value || !elRef.value) return
  const elWidth = elRef.value.clientWidth || 1
  const range = dragStartZoomEnd - dragStartZoomStart
  if (range <= 0) return
  const dx = e.clientX - dragStartX
  const shift = (-dx / elWidth) * range
  applyZoom(dragStartZoomStart + shift, dragStartZoomEnd + shift)
}

function onPointerUp(e: PointerEvent) {
  if (!dragging.value || !elRef.value) return
  dragging.value = false
  try {
    elRef.value.releasePointerCapture(e.pointerId)
  } catch {}
}

function buildOption(bars: BarDto[], title?: string): echarts.EChartsOption {
  if (!bars || bars.length === 0) {
    return {
      title: title ? { text: title, left: 10, top: 6, textStyle: { fontSize: 14 } } : undefined,
      backgroundColor: '#ffffff',
      graphic: {
        type: 'text',
        left: 'center',
        top: 'middle',
        style: { text: '暂无数据', fill: '#667085', fontSize: 14 },
      },
    }
  }
  const categories = bars.map((b) => b.date)
  const candle = bars.map((b) => [b.open ?? 0, b.close ?? 0, b.low ?? 0, b.high ?? 0])
  const volumes = bars.map((b) => {
    const up = (b.close ?? 0) >= (b.open ?? 0)
    return {
      value: b.volume ?? 0,
      itemStyle: { color: up ? '#26a69a' : '#ef5350' },
    }
  })

  const showSlider = props.showSlider !== false

  return {
    title: title ? { text: title, left: 10, top: 6, textStyle: { fontSize: 14 } } : undefined,
    backgroundColor: '#ffffff',
    animation: false,
    grid: [
      { left: 84, right: 24, top: 40, height: '58%' },
      { left: 84, right: 24, top: '70%', height: '14%' },
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
        axisLabel: { color: '#667085', margin: 12 },
        splitLine: { lineStyle: { color: '#eef1f6' } },
      },
      {
        gridIndex: 1,
        scale: true,
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: {
          color: '#667085',
          margin: 12,
          formatter: (v: number) => {
            const abs = Math.abs(v)
            if (abs >= 1_000_000_000) return `${(v / 1_000_000_000).toFixed(2)}B`
            if (abs >= 1_000_000) return `${(v / 1_000_000).toFixed(2)}M`
            if (abs >= 1_000) return `${(v / 1_000).toFixed(2)}K`
            return String(v)
          },
        },
        splitLine: { lineStyle: { color: '#eef1f6' } },
      },
    ],
    dataZoom: [
      {
        type: 'inside',
        xAxisIndex: [0, 1],
        start: zoomStart.value,
        end: zoomEnd.value,
        zoomOnMouseWheel: false,
        moveOnMouseMove: false,
        moveOnMouseWheel: false,
      },
      {
        type: 'slider',
        xAxisIndex: [0, 1],
        start: zoomStart.value,
        end: zoomEnd.value,
        top: '88%',
        height: 20,
        show: showSlider,
      },
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
  chart.on('dataZoom', () => {
    if (!chart) return
    const opt: any = chart.getOption()
    const dz = Array.isArray(opt?.dataZoom) ? opt.dataZoom : []
    const first = dz[0]
    const s = typeof first?.start === 'number' ? first.start : null
    const e = typeof first?.end === 'number' ? first.end : null
    if (s != null && e != null) {
      zoomStart.value = s
      zoomEnd.value = e
    }
  })
  const zr = chart.getZr()
  zr.on('mousewheel', (params: any) => {
    const nativeEvent: WheelEvent | undefined = params?.event
    if (nativeEvent) onWheel(nativeEvent)
  })

  elRef.value.addEventListener('wheel', onWheel, { passive: false })
  elRef.value.addEventListener('pointerdown', onPointerDown)
  elRef.value.addEventListener('pointermove', onPointerMove)
  elRef.value.addEventListener('pointerup', onPointerUp)
  elRef.value.addEventListener('pointercancel', onPointerUp)
  render()
  const onResize = () => chart?.resize()
  window.addEventListener('resize', onResize)
  onBeforeUnmount(() => {
    window.removeEventListener('resize', onResize)
    zr.off('mousewheel')
    elRef.value?.removeEventListener('wheel', onWheel as any)
    elRef.value?.removeEventListener('pointerdown', onPointerDown as any)
    elRef.value?.removeEventListener('pointermove', onPointerMove as any)
    elRef.value?.removeEventListener('pointerup', onPointerUp as any)
    elRef.value?.removeEventListener('pointercancel', onPointerUp as any)
  })
})

watch(
  () => props.bars,
  () => render(),
  { deep: true },
)

watch(
  () => props.windowStart,
  (v) => {
    if (typeof v === 'number') applyZoom(v, zoomEnd.value)
  },
)

watch(
  () => props.windowEnd,
  (v) => {
    if (typeof v === 'number') applyZoom(zoomStart.value, v)
  },
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
      height: `${props.height ?? 520}px`,
      borderRadius: '12px',
      cursor: dragging ? 'grabbing' : 'grab',
      touchAction: 'none',
      overscrollBehavior: 'contain',
    }"
  />
</template>
