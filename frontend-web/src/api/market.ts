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

export type IndexListItemDto = {
  symbol: string
  name: string | null
  wikiUrl: string | null
}

export type BreadthSnapshotDto = {
  indexSymbol: string
  asOfDate: string | null
  totalMembers: number
  membersWithData: number
  up: number
  down: number
  flat: number
  aboveMa20: number
  aboveMa50: number
  aboveMa200: number
  newHigh52w: number
  newLow52w: number
  volumeSurge: number
}

export type ScreenerItemDto = {
  symbol: string
  name: string | null
  asOfDate: string
  close: number | null
  returnPct: number | null
  ma50: number | null
  ma200: number | null
  volume: number | null
}

export type StreakRankItemDto = {
  symbol: string
  name: string | null
  interval: string
  direction: string
  streak: number | null
  startDate: string | null
  endDate: string | null
}

export type FactorRankItemDto = {
  symbol: string
  name: string | null
  metric: string
  value: number | null
  count: number | null
  rate: number | null
  startDate: string | null
  endDate: string | null
}

export type RsPointDto = {
  date: string
  stockClose: number | null
  indexClose: number | null
  rs: number | null
  rsNormalized: number | null
}

export type RsSeriesDto = {
  symbol: string
  indexSymbol: string
  start: string
  end: string
  stockReturnPct: number | null
  indexReturnPct: number | null
  rsReturnPct: number | null
  points: RsPointDto[]
}

export type RsRankItemDto = {
  symbol: string
  name: string | null
  asOfDate: string
  stockReturnPct: number | null
  indexReturnPct: number | null
  rsReturnPct: number | null
}

export type TradePlanDto = {
  id: number
  symbol: string
  name: string | null
  direction: 'LONG' | 'SHORT'
  status: 'PLANNED' | 'OPEN' | 'CLOSED' | 'CANCELLED'
  startDate: string
  entryPrice: number | null
  entryLow: number | null
  entryHigh: number | null
  stopPrice: number | null
  targetPrice: number | null
  note: string | null
  lastBarDate: string | null
  lastClose: number | null
  pnlPct: number | null
  hitStop: boolean | null
  hitTarget: boolean | null
  updatedAt: string
}

export type CreateTradePlanRequestDto = {
  symbol: string
  direction?: 'LONG' | 'SHORT'
  status?: 'PLANNED' | 'OPEN' | 'CLOSED' | 'CANCELLED'
  startDate?: string
  entryPrice?: number
  entryLow?: number
  entryHigh?: number
  stopPrice?: number
  targetPrice?: number
  note?: string
}

export type UpdateTradePlanRequestDto = {
  direction?: 'LONG' | 'SHORT'
  status?: 'PLANNED' | 'OPEN' | 'CLOSED' | 'CANCELLED'
  startDate?: string
  entryPrice?: number
  entryLow?: number
  entryHigh?: number
  stopPrice?: number
  targetPrice?: number
  note?: string
}

export type AlertRuleDto = {
  id: number
  symbol: string
  name: string | null
  ruleType: 'PRICE_BREAKOUT' | 'MA_CROSS' | 'VOLUME_SURGE'
  enabled: boolean
  priceLevel: number | null
  priceDirection: 'ABOVE' | 'BELOW' | null
  maPeriod: 20 | 50 | 200 | null
  maDirection: 'ABOVE' | 'BELOW' | null
  volumeMultiple: number | null
  lastTriggeredDate: string | null
  updatedAt: string
}

export type CreateAlertRuleRequestDto = {
  symbol: string
  ruleType: 'PRICE_BREAKOUT' | 'MA_CROSS' | 'VOLUME_SURGE'
  enabled?: boolean
  priceLevel?: number
  priceDirection?: 'ABOVE' | 'BELOW'
  maPeriod?: 20 | 50 | 200
  maDirection?: 'ABOVE' | 'BELOW'
  volumeMultiple?: number
}

export type UpdateAlertRuleRequestDto = {
  enabled: boolean
  priceLevel?: number
  priceDirection?: 'ABOVE' | 'BELOW'
  maPeriod?: 20 | 50 | 200
  maDirection?: 'ABOVE' | 'BELOW'
  volumeMultiple?: number
}

export type AlertEventDto = {
  id: number
  ruleId: number
  symbol: string
  name: string | null
  barDate: string
  message: string
  createdAt: string
}

export type EvaluateAlertsResponseDto = {
  triggered: number
  latestEvents: AlertEventDto[]
}

export async function listIndices() {
  const res = await http.get<IndexListItemDto[]>('/api/indices')
  return res.data
}

export async function getBreadth(index: string) {
  const res = await http.get<BreadthSnapshotDto>('/api/market/breadth', { params: { index } })
  return res.data
}

export async function runScreener(params: { index: string; preset: string; lookbackDays: number; limit: number }) {
  const res = await http.get<ScreenerItemDto[]>('/api/market/screener', { params })
  return res.data
}

export async function rankStreaks(params: {
  index: string
  interval: string
  direction: string
  start?: string
  end?: string
  limit: number
  volumeMultiple?: number
  flatThresholdPct?: number
}) {
  const res = await http.get<StreakRankItemDto[]>('/api/market/streaks/rank', { params })
  return res.data
}

export async function getLongestStreakForSymbol(
  symbol: string,
  params: { interval: string; direction: string; start?: string; end?: string; volumeMultiple?: number; flatThresholdPct?: number }
) {
  const res = await http.get<StreakRankItemDto>(`/api/market/streaks/symbols/${encodeURIComponent(symbol)}/longest`, { params })
  return res.data
}

export async function rankFactors(params: {
  index: string
  interval: string
  metric: string
  mode?: string
  lookback?: number
  start?: string
  end?: string
  limit: number
}) {
  const res = await http.get<FactorRankItemDto[]>('/api/market/factors/rank', { params })
  return res.data
}

export async function getRelativeStrength(params: { symbol: string; index: string; start?: string; end?: string }) {
  const res = await http.get<RsSeriesDto>('/api/market/rs', { params })
  return res.data
}

export async function getRelativeStrengthRank(params: { index: string; lookbackDays: number; limit: number; requireAboveMa50?: boolean }) {
  const res = await http.get<RsRankItemDto[]>('/api/market/rs/rank', { params })
  return res.data
}

export async function listTradePlans(params?: { status?: string }) {
  const res = await http.get<TradePlanDto[]>('/api/plans', { params })
  return res.data
}

export async function createTradePlan(body: CreateTradePlanRequestDto) {
  const res = await http.post<TradePlanDto>('/api/plans', body)
  return res.data
}

export async function updateTradePlan(id: number, body: UpdateTradePlanRequestDto) {
  const res = await http.put<TradePlanDto>(`/api/plans/${id}`, body)
  return res.data
}

export async function deleteTradePlan(id: number) {
  const res = await http.delete<void>(`/api/plans/${id}`)
  return res.data
}

export async function listAlertRules() {
  const res = await http.get<AlertRuleDto[]>('/api/alerts/rules')
  return res.data
}

export async function createAlertRule(body: CreateAlertRuleRequestDto) {
  const res = await http.post<AlertRuleDto>('/api/alerts/rules', body)
  return res.data
}

export async function updateAlertRule(id: number, body: UpdateAlertRuleRequestDto) {
  const res = await http.put<AlertRuleDto>(`/api/alerts/rules/${id}`, body)
  return res.data
}

export async function deleteAlertRule(id: number) {
  const res = await http.delete<void>(`/api/alerts/rules/${id}`)
  return res.data
}

export async function listAlertEvents(params?: { limit?: number }) {
  const res = await http.get<AlertEventDto[]>('/api/alerts/events', { params })
  return res.data
}

export async function evaluateAlerts(params?: { latestLimit?: number }) {
  const res = await http.post<EvaluateAlertsResponseDto>('/api/alerts/evaluate', null, { params })
  return res.data
}

export async function createStock(params: {
  symbol: string
  name?: string
  wikiUrl?: string
  indexSymbols?: string[]
}) {
  const res = await http.post<void>('/api/admin/stocks', params)
  return res.data
}

export async function createIndex(params: {
  symbol: string
  name?: string
  wikiUrl?: string
  initialStockSymbols?: string[]
}) {
  const res = await http.post<IndexListItemDto>('/api/indices', params)
  return res.data
}

export async function getIndexConstituents(indexSymbol: string) {
  const res = await http.get<string[]>(`/api/indices/${encodeURIComponent(indexSymbol)}/constituents`)
  return res.data
}

export async function replaceIndexConstituents(indexSymbol: string, stockSymbols: string[]) {
  const res = await http.put<void>(`/api/indices/${encodeURIComponent(indexSymbol)}/constituents`, { stockSymbols })
  return res.data
}

export async function getIndexBars(symbol: string, interval: string = '1d', start?: string, end?: string) {
  const clean = symbol.startsWith('^') ? symbol.substring(1).toLowerCase() : symbol.toLowerCase()
  const res = await http.get<BarDto[]>(`/api/index/${encodeURIComponent(clean)}/bars`, {
    params: { interval, start, end }
  })
  return res.data
}

export async function getIndexIndicators(
  symbol: string,
  params?: { start?: string; end?: string; interval?: string; ma?: string; include?: string }
) {
  const clean = symbol.startsWith('^') ? symbol.substring(1).toLowerCase() : symbol.toLowerCase()
  const res = await http.get<IndicatorsResponseDto>(`/api/index/${encodeURIComponent(clean)}/indicators`, { params })
  return res.data
}

export async function getSp500Bars(params?: { start?: string; end?: string; interval?: string }) {
  return getIndexBars('^SPX', params?.interval || '1d', params?.start, params?.end)
}

export async function listStocks(params: {
  index?: string
  query?: string
  page: number
  size: number
  sortBy?: 'symbol' | 'name'
  sortDir?: 'asc' | 'desc'
}) {
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
