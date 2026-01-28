export interface AnalysisRequestDto {
  index: string
  type: 'TREND' | 'WIN_RATE'
  start: string
  end: string
  limit?: number
  params?: Record<string, any>
}

export interface AnalysisResultDto {
  symbol: string
  name: string
  score: number
  details: Record<string, any>
}

export interface AnalysisResponseDto {
  type: string
  results: AnalysisResultDto[]
}
