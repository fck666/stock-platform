import { http } from './client'

export type BarDto = {
  date: string
  open: number | null
  high: number | null
  low: number | null
  close: number | null
  volume: number | null
}

export type StockListItemDto = {
  symbol: string
  name: string | null
  gicsSector: string | null
  gicsSubIndustry: string | null
  headquarters: string | null
  wikiDescription: string | null
}

export type PagedResponse<T> = {
  items: T[]
  total: number
  page: number
  size: number
}

export type SecurityIdentifierDto = {
  provider: string
  identifier: string
}

export type StockDetailDto = {
  symbol: string
  name: string | null
  gicsSector: string | null
  gicsSubIndustry: string | null
  headquarters: string | null
  dateFirstAdded: string | null
  cik: string | null
  founded: string | null
  wikiUrl: string | null
  wikiTitle: string | null
  wikiDescription: string | null
  wikiExtract: string | null
  identifiers: SecurityIdentifierDto[]
}

export type SyncJobDto = {
  jobId: string
  status: string
  startedAt: string | null
  finishedAt: string | null
  exitCode: number | null
  outputTail: string | null
}

export async function getSp500Bars(params?: { start?: string; end?: string; interval?: string }) {
  const res = await http.get<BarDto[]>('/api/index/sp500/bars', { params })
  return res.data
}

export async function listStocks(params: { query?: string; page: number; size: number }) {
  const res = await http.get<PagedResponse<StockListItemDto>>('/api/stocks', { params })
  return res.data
}

export async function getStockDetail(symbol: string) {
  const res = await http.get<StockDetailDto>(`/api/stocks/${encodeURIComponent(symbol)}`)
  return res.data
}

export async function getStockBars(symbol: string, params?: { start?: string; end?: string; interval?: string }) {
  const res = await http.get<BarDto[]>(`/api/stocks/${encodeURIComponent(symbol)}/bars`, { params })
  return res.data
}

export async function syncSp500Index() {
  const res = await http.post<SyncJobDto>('/api/sync/sp500-index')
  return res.data
}

export async function syncStock(symbol: string) {
  const res = await http.post<SyncJobDto>(`/api/sync/stocks/${encodeURIComponent(symbol)}`)
  return res.data
}

export async function syncStocks(symbols: string[]) {
  const res = await http.post<SyncJobDto>('/api/sync/stocks', { symbols })
  return res.data
}

export async function getSyncJob(jobId: string) {
  const res = await http.get<SyncJobDto>(`/api/sync/jobs/${encodeURIComponent(jobId)}`)
  return res.data
}

