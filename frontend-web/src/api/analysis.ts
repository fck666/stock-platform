import { http } from './client'
import type { AnalysisRequestDto, AnalysisResponseDto } from './analysis_types'

export async function executeAnalysis(request: AnalysisRequestDto): Promise<AnalysisResponseDto> {
  const res = await http.post('/api/analysis/execute', request)
  return res.data
}
