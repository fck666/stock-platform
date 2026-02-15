import { http } from './client'

/**
 * 轻量行为采集与看板 API
 *
 * - recordPageView：页面浏览上报（仅登录用户）
 * - getAnalyticsSummary：管理员行为看板聚合数据
 */
export type AnalyticsSeriesPointDto = {
  day: string
  count: number
}

export type AnalyticsTopItemDto = {
  key: string
  count: number
}

export type AnalyticsTopApiDto = {
  method: string
  path: string
  count: number
  errorCount: number
  p95LatencyMs: number
}

export type AnalyticsSummaryDto = {
  pageViews: AnalyticsSeriesPointDto[]
  apiCalls: AnalyticsSeriesPointDto[]
  topPages: AnalyticsTopItemDto[]
  topApis: AnalyticsTopApiDto[]
  topUsers: AnalyticsTopItemDto[]
}

export async function recordPageView(path: string, title?: string) {
  await http.post('/api/analytics/page-view', { path, title })
}

export async function getAnalyticsSummary(days = 14) {
  const res = await http.get<AnalyticsSummaryDto>('/api/admin/analytics/summary', { params: { days } })
  return res.data
}
