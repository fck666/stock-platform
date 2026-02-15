<script setup lang="ts">
/**
 * CandlestickChart
 * 
 * A comprehensive ECharts wrapper for financial data visualization.
 * 
 * Features:
 * - Candlestick (K-Line) series for OHLC data.
 * - Volume bar chart (color-coded by Up/Down).
 * - Moving Average (MA) lines overlay.
 * - Technical Indicators (MACD, KDJ) in separate grid panes.
 * - Corporate Actions (Dividends, Splits) markers on the chart.
 * - Custom Zoom & Pan interactions (Mouse wheel, Drag).
 * - Responsive resizing.
 */
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
  maLines?: Record<string, Array<number | null>>
  markers?: Array<{ date: string; type: 'DIVIDEND' | 'SPLIT'; label: string }>
  subIndicators?: Array<'macd' | 'kdj'>
  macd?: { dif: Array<number | null>; dea: Array<number | null>; hist: Array<number | null> } | null
  kdj?: { k: Array<number | null>; d: Array<number | null>; j: Array<number | null> } | null
  loading?: boolean
}>()

const elRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
let onThemeChange: (() => void) | null = null
const zoomStart = ref<number>(props.windowStart ?? 50)
const zoomEnd = ref<number>(props.windowEnd ?? 100)
const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad|iPod/.test(navigator.platform)

function cssVar(name: string, fallback: string) {
  if (typeof window === 'undefined') return fallback
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return v || fallback
}

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

/**
 * Custom Wheel Event Handler
 * 
 * Implements Google Finance / TradingView style zooming:
 * - Ctrl + Wheel (or Pinch on trackpad): Zoom in/out at cursor position.
 * - Wheel (without modifier): Pan left/right.
 */
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

/**
 * Constructs the ECharts option object.
 * 
 * Layout Logic:
 * - Calculates heights for Main Chart, Volume Chart, and Sub-Indicator Panes (MACD, KDJ).
 * - Dynamically stacks grids vertically.
 * - Links X-Axes for synchronized zooming/panning.
 * 
 * Series Logic:
 * - Candlestick series (Open, Close, Low, High).
 * - Volume series (Bar).
 * - MA lines (Line).
 * - Indicator series (Lines/Bars for MACD/KDJ).
 * - Event Markers (Scatter points for Dividends/Splits).
 */
function buildOption(bars: BarDto[], title?: string): echarts.EChartsOption {
  const bg = cssVar('--el-bg-color', '#ffffff')
  const muted = cssVar('--app-muted', '#667085')
  const border = cssVar('--app-border', '#cfd5e2')
  const gridLine = cssVar('--el-border-color-lighter', '#eef1f6')
  const tooltipBg = cssVar('--el-bg-color-overlay', 'rgba(17,17,17,0.9)')
  const tooltipText = cssVar('--el-text-color-primary', '#fff')
  const accent = cssVar('--app-accent', '#5b8ff9')
  const success = cssVar('--app-success', '#26a69a')
  const danger = cssVar('--app-danger', '#ef5350')

  if (!bars || bars.length === 0) {
    return {
      title: title ? { text: title, left: 10, top: 6, textStyle: { fontSize: 14 } } : undefined,
      backgroundColor: bg,
      graphic: {
        type: 'text',
        left: 'center',
        top: 'middle',
        style: { text: '暂无数据', fill: muted, fontSize: 14 },
      },
    }
  }
  const categories = bars.map((b) => b.date)
  const candle = bars.map((b) => [b.open ?? 0, b.close ?? 0, b.low ?? 0, b.high ?? 0])
  const volumes = bars.map((b) => {
    const up = (b.close ?? 0) >= (b.open ?? 0)
    return {
      value: b.volume === null || b.volume === undefined ? '-' : b.volume,
      itemStyle: { color: up ? success : danger },
    }
  })

  const showSlider = props.showSlider !== false
  const subs = (props.subIndicators || []).filter((x) => x === 'macd' || x === 'kdj')
  const subCount = subs.length
  const totalHeight = props.height ?? 520
  const topMain = 40
  const gap = 12
  const subPaneHeight = 130
  const volumeHeight = 110
  const axisLabelSpace = 26
  const sliderBottom = 8
  const sliderHeight = showSlider ? 20 : 0
  const sliderGap = showSlider ? 6 : 0
  const xLabelBottom = sliderBottom + sliderHeight + sliderGap
  const volumeGridBottom = xLabelBottom + axisLabelSpace

  const volumeTop = Math.max(topMain + 260 + gap, totalHeight - volumeGridBottom - volumeHeight)

  const subTops: number[] = []
  let curSubTop = volumeTop - gap - subPaneHeight
  for (let i = 0; i < subCount; i++) {
    subTops.push(curSubTop)
    curSubTop -= subPaneHeight + gap
  }
  subTops.reverse()

  const firstBelowMainTop = subCount > 0 ? (subTops[0] ?? volumeTop) : volumeTop
  const mainHeight = Math.max(260, firstBelowMainTop - gap - topMain)

  const grids: any[] = []
  grids.push({ left: 84, right: 24, top: topMain, height: mainHeight })
  for (let i = 0; i < subCount; i++) {
    grids.push({ left: 84, right: 24, top: subTops[i], height: subPaneHeight })
  }
  grids.push({ left: 84, right: 24, top: volumeTop, height: volumeHeight })

  const xAxes: any[] = []
  for (let i = 0; i < grids.length; i++) {
    xAxes.push({
      type: 'category',
      gridIndex: i,
      data: categories,
      boundaryGap: true,
      axisLine: { lineStyle: { color: border } },
      axisLabel: i === grids.length - 1 ? { color: muted } : { show: false },
      axisTick: { show: false },
      splitLine: { show: false },
      min: 'dataMin',
      max: 'dataMax',
    })
  }

  const yAxes: any[] = []
  yAxes.push({
    scale: true,
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: muted, margin: 12 },
    splitLine: { lineStyle: { color: gridLine } },
  })
  for (let i = 0; i < subCount; i++) {
    yAxes.push({
      gridIndex: 1 + i,
      scale: true,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: muted, margin: 12 },
      splitLine: { lineStyle: { color: gridLine } },
    })
  }
  yAxes.push({
    gridIndex: grids.length - 1,
    scale: true,
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: {
      color: muted,
      margin: 12,
      formatter: (v: number) => {
        const abs = Math.abs(v)
        if (abs >= 1_000_000_000) return `${(v / 1_000_000_000).toFixed(2)}B`
        if (abs >= 1_000_000) return `${(v / 1_000_000).toFixed(2)}M`
        if (abs >= 1_000) return `${(v / 1_000).toFixed(2)}K`
        return String(v)
      },
    },
    splitLine: { lineStyle: { color: gridLine } },
  })

  const series: any[] = [
    {
      name: 'K',
      type: 'candlestick',
      data: candle,
      itemStyle: {
        color: success,
        color0: danger,
        borderColor: success,
        borderColor0: danger,
      },
    },
  ]

  const dateToHigh = new Map<string, number>()
  for (let i = 0; i < categories.length; i++) {
    const h = candle[i]?.[3]
    const d = categories[i]
    if (typeof d === 'string' && typeof h === 'number') {
      dateToHigh.set(d, h)
    }
  }
  const markers = props.markers || []
  const divPoints: any[] = []
  const splitPoints: any[] = []
  for (const m of markers) {
    const y = dateToHigh.get(m.date)
    if (y == null) continue
    const item = {
      name: m.label,
      value: [m.date, y],
      label: { show: true, formatter: m.type === 'DIVIDEND' ? 'D' : 'S', color: '#fff', fontSize: 10 },
      tooltip: { valueFormatter: () => '' },
    }
    if (m.type === 'DIVIDEND') divPoints.push(item)
    else splitPoints.push(item)
  }
  if (divPoints.length > 0) {
    series.push({
      name: 'Dividend',
      type: 'scatter',
      xAxisIndex: 0,
      yAxisIndex: 0,
      symbol: 'pin',
      symbolSize: 18,
      itemStyle: { color: accent },
      data: divPoints,
      z: 20,
      tooltip: {
        formatter: (p: any) => `分红：${p?.name || ''}<br/>日期：${p?.value?.[0] || ''}`,
      },
    })
  }
  if (splitPoints.length > 0) {
    series.push({
      name: 'Split',
      type: 'scatter',
      xAxisIndex: 0,
      yAxisIndex: 0,
      symbol: 'diamond',
      symbolSize: 14,
      itemStyle: { color: '#f6bd16' },
      data: splitPoints,
      z: 20,
      tooltip: {
        formatter: (p: any) => `拆股：${p?.name || ''}<br/>日期：${p?.value?.[0] || ''}`,
      },
    })
  }

  const maLines = props.maLines || {}
  const maColors: Record<string, string> = {
    '20': accent,
    '60': '#61dca3',
    '180': '#f6bd16',
    '360': '#7262fd',
  }
  for (const [k, data] of Object.entries(maLines)) {
    series.push({
      name: `MA${k}`,
      type: 'line',
      data,
      showSymbol: false,
      smooth: true,
      lineStyle: { width: 1, color: maColors[k] || muted },
      emphasis: { focus: 'series' },
    })
  }

  for (let i = 0; i < subs.length; i++) {
    const idx = 1 + i
    const sub = subs[i]
    if (sub === 'macd' && props.macd) {
      const macd = props.macd
      series.push(
        {
          name: `DIF${subCount > 1 ? `#${i + 1}` : ''}`,
          type: 'line',
          xAxisIndex: idx,
          yAxisIndex: idx,
          data: macd.dif,
          showSymbol: false,
          smooth: true,
          lineStyle: { width: 1, color: accent },
        },
        {
          name: `DEA${subCount > 1 ? `#${i + 1}` : ''}`,
          type: 'line',
          xAxisIndex: idx,
          yAxisIndex: idx,
          data: macd.dea,
          showSymbol: false,
          smooth: true,
          lineStyle: { width: 1, color: '#f6bd16' },
        },
        {
          name: `HIST${subCount > 1 ? `#${i + 1}` : ''}`,
          type: 'bar',
          xAxisIndex: idx,
          yAxisIndex: idx,
          data: macd.hist.map((v) => {
            if (v === null || v === undefined) return null
            return {
              value: v,
              itemStyle: { color: v >= 0 ? success : danger },
            }
          }),
          barWidth: '60%',
        },
      )
    }
    if (sub === 'kdj' && props.kdj) {
      const kdj = props.kdj
      series.push(
        {
          name: `K${subCount > 1 ? `#${i + 1}` : ''}`,
          type: 'line',
          xAxisIndex: idx,
          yAxisIndex: idx,
          data: kdj.k,
          showSymbol: false,
          smooth: true,
          lineStyle: { width: 1, color: accent },
        },
        {
          name: `D${subCount > 1 ? `#${i + 1}` : ''}`,
          type: 'line',
          xAxisIndex: idx,
          yAxisIndex: idx,
          data: kdj.d,
          showSymbol: false,
          smooth: true,
          lineStyle: { width: 1, color: '#f6bd16' },
        },
        {
          name: `J${subCount > 1 ? `#${i + 1}` : ''}`,
          type: 'line',
          xAxisIndex: idx,
          yAxisIndex: idx,
          data: kdj.j,
          showSymbol: false,
          smooth: true,
          lineStyle: { width: 1, color: '#7262fd' },
        },
      )
    }
  }

  series.push({
    name: 'Volume',
    type: 'bar',
    xAxisIndex: grids.length - 1,
    yAxisIndex: yAxes.length - 1,
    data: volumes,
    barWidth: '60%',
  })

  return {
    title: title ? { text: title, left: 10, top: 6, textStyle: { fontSize: 14 } } : undefined,
    backgroundColor: bg,
    animation: false,
    grid: grids,
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      backgroundColor: tooltipBg,
      borderWidth: 0,
      textStyle: { color: tooltipText },
    },
    axisPointer: { link: [{ xAxisIndex: 'all' }] },
    xAxis: xAxes,
    yAxis: yAxes,
    dataZoom: [
      {
        type: 'inside',
        xAxisIndex: Array.from({ length: xAxes.length }, (_, i) => i),
        start: zoomStart.value,
        end: zoomEnd.value,
        zoomOnMouseWheel: false,
        moveOnMouseMove: false,
        moveOnMouseWheel: false,
      },
      {
        type: 'slider',
        xAxisIndex: Array.from({ length: xAxes.length }, (_, i) => i),
        start: zoomStart.value,
        end: zoomEnd.value,
        bottom: sliderBottom,
        left: 84,
        right: 24,
        height: 20,
        show: showSlider,
      },
    ],
    series,
  }
}

function render() {
  if (!chart || !elRef.value) return
  chart.setOption(buildOption(props.bars, props.title), {
    notMerge: false,
    replaceMerge: ['series', 'xAxis', 'yAxis', 'grid', 'dataZoom', 'graphic'],
  })
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
  if (props.loading) {
    chart.showLoading({
      text: '',
      color: cssVar('--app-accent', '#5b8ff9'),
      textColor: cssVar('--el-text-color-primary', '#333'),
      maskColor: 'rgba(255, 255, 255, 0.4)',
      zlevel: 0,
    })
  }
  render()
  const onResize = () => chart?.resize()
  window.addEventListener('resize', onResize)
  onThemeChange = () => render()
  window.addEventListener('themechange', onThemeChange)
  watch(
  () => props.loading,
  (val) => {
    if (!chart) return
    if (val) {
      chart.showLoading({
        text: '',
        color: cssVar('--app-accent', '#5b8ff9'),
        textColor: cssVar('--el-text-color-primary', '#333'),
        maskColor: 'rgba(255, 255, 255, 0.4)',
        zlevel: 0,
      })
    } else {
      chart.hideLoading()
    }
  },
)

onBeforeUnmount(() => {
    window.removeEventListener('resize', onResize)
    if (onThemeChange) window.removeEventListener('themechange', onThemeChange)
    zr.off('mousewheel')
    elRef.value?.removeEventListener('wheel', onWheel as any)
    elRef.value?.removeEventListener('pointerdown', onPointerDown as any)
    elRef.value?.removeEventListener('pointermove', onPointerMove as any)
    elRef.value?.removeEventListener('pointerup', onPointerUp as any)
    elRef.value?.removeEventListener('pointercancel', onPointerUp as any)
  })
})

watch(
  () => [props.bars, props.maLines, props.markers, props.subIndicators, props.macd, props.kdj],
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

watch(
  () => props.height,
  () => {
    // Wait for DOM update
    setTimeout(() => {
      chart?.resize()
    }, 50)
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
