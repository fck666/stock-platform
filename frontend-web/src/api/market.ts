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

export type CorporateActionDto = {
  exDate: string
  actionType: string
  cashAmount: number | null
  currency: string | null
  splitNumerator: number | null
  splitDenominator: number | null
  source: string | null
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
  sharesOutstanding: number | null
  floatShares: number | null
  marketCap: number | null
  currency: string | null
  identifiers: SecurityIdentifierDto[]
  corporateActions: CorporateActionDto[]
}

export type MacdDto = {
  dif: number | null
  dea: number | null
  hist: number | null
}

export type KdjDto = {
  k: number | null
  d: number | null
  j: number | null
}

export type IndicatorPointDto = {
  date: string
  ma: Record<string, number | null> | null
  macd: MacdDto | null
  kdj: KdjDto | null
}

export type IndicatorsResponseDto = {
  interval: string
  points: IndicatorPointDto[]
}

export type SyncJobDto = {
  jobId: string
  status: string
  startedAt: string | null
  finishedAt: string | null
  exitCode: number | null
  outputTail: string | null
}

export async function getIndexBars(symbol: string, interval: string = '1d', start?: string, end?: string) {
  const clean = symbol.startsWith('^') ? symbol.substring(1).toLowerCase() : symbol.toLowerCase()
  const res = await http.get<BarDto[]>(`/api/index/${encodeURIComponent(clean)}/bars`, {
    params: { interval, start, end }
  })
  return res.data
}

export async function getSp500Bars(params?: { start?: string; end?: string; interval?: string }) {
  return getIndexBars('^SPX', params?.interval || '1d', params?.start, params?.end)
}

export async function listStocks(params: { index?: string; query?: string; page: number; size: number }) {
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

export async function getStockIndicators(
  symbol: string,
  params?: { start?: string; end?: string; interval?: string; ma?: string; include?: string }
) {
  const res = await http.get<IndicatorsResponseDto>(`/api/stocks/${encodeURIComponent(symbol)}/indicators`, { params })
  return res.data
}

export async function syncWiki(index: string = '^SPX') {
  if (import.meta.env.DEV) console.info('[api] POST /api/sync/wiki', { index })
  const res = await http.post<SyncJobDto>('/api/sync/wiki', null, { params: { index } })
  if (import.meta.env.DEV) console.info('[api] <- /api/sync/wiki', { status: res.status, data: res.data })
  return res.data
}

export async function syncFundamentals(index: string = '^SPX') {
  if (import.meta.env.DEV) console.info('[api] POST /api/sync/fundamentals', { index })
  const res = await http.post<SyncJobDto>('/api/sync/fundamentals', null, { params: { index } })
  if (import.meta.env.DEV) console.info('[api] <- /api/sync/fundamentals', { status: res.status, data: res.data })
  return res.data
}

export async function syncPrices(index: string = '^SPX') {
  if (import.meta.env.DEV) console.info('[api] POST /api/sync/prices', { index })
  const res = await http.post<SyncJobDto>('/api/sync/prices', null, { params: { index } })
  if (import.meta.env.DEV) console.info('[api] <- /api/sync/prices', { status: res.status, data: res.data })
  return res.data
}

export async function syncStock(symbol: string, index: string = '^SPX') {
  if (import.meta.env.DEV) console.info('[api] POST /api/sync/stocks/{symbol}', { symbol, index })
  const res = await http.post<SyncJobDto>(`/api/sync/stocks/${encodeURIComponent(symbol)}`, null, { params: { index } })
  if (import.meta.env.DEV) console.info('[api] <- /api/sync/stocks/{symbol}', { status: res.status, data: res.data })
  return res.data
}

export async function syncStocks(symbols: string[], index: string = '^SPX') {
  if (import.meta.env.DEV) console.info('[api] POST /api/sync/stocks', { index, count: symbols.length })
  const res = await http.post<SyncJobDto>('/api/sync/stocks', { symbols }, { params: { index } })
  if (import.meta.env.DEV) console.info('[api] <- /api/sync/stocks', { status: res.status, data: res.data })
  return res.data
}

export async function getSyncJob(jobId: string) {
  const res = await http.get<SyncJobDto>(`/api/sync/jobs/${encodeURIComponent(jobId)}`)
  return res.data
}
