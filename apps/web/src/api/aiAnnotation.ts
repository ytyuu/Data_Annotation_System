const apiBaseUrl = 'http://localhost:7000';

export interface ProviderDataset {
  id: string;
  name: string;
  annotationSchema: string;
  status: string;
  itemCount: number;
  completedItemCount: number;
  pendingItemCount: number | null;
}

export interface AiBatch {
  id: string;
  datasetId: string;
  datasetName: string;
  status: string;
  modelName: string;
  promptVersion: string;
  config: Record<string, unknown>;
  totalCount: number;
  processedCount: number;
  successCount: number;
  failedCount: number;
  needsReviewCount: number;
  acceptedCount: number;
  rejectedCount: number;
  errorMessage: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AiResult {
  id: string;
  batchId: string;
  datasetId: string;
  itemId: string;
  roundNo: number;
  status: string;
  content: string;
  contentType: string;
  metadata: Record<string, unknown>;
  result: Record<string, unknown> | null;
  acceptedResult: Record<string, unknown> | null;
  confidence: string | null;
  confidenceScore: string | null;
  reason: string | null;
  needsHumanReview: boolean;
  isSampled: boolean;
  riskFlags: string[];
  errorMessage: string | null;
  attemptCount: number;
  reviewedAt: string | null;
  reviewAction: string | null;
  reviewComment: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateBatchInput {
  maxItems: number;
  modelName: 'deepseek-v4-flash' | 'deepseek-v4-pro';
  promptVersion: string;
  confidenceThreshold: number;
  samplingRatio: number;
  highRiskOptionValues: string[];
  metadataAllowList: string[];
  maxAttempts: number;
}

export async function listProviderDatasets(): Promise<ProviderDataset[]> {
  return request('/api/provider/datasets');
}

export async function createAiBatch(datasetId: string, input: CreateBatchInput): Promise<AiBatch> {
  return request(`/api/provider/datasets/${datasetId}/ai-annotation-batches`, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export async function listAiBatches(datasetId: string): Promise<AiBatch[]> {
  const response = await request<{ items: AiBatch[] }>(`/api/provider/datasets/${datasetId}/ai-annotation-batches`);
  return response.items;
}

export async function runAiBatch(batchId: string) {
  return request<{ message: string }>(`/api/provider/ai-annotation-batches/${batchId}/run`, {
    method: 'POST',
  });
}

export async function listAiResults(
  batchId: string,
  options: { status?: string; reviewMode?: string; page?: number; pageSize?: number } = {},
) {
  const params = new URLSearchParams({
    batchId,
    page: String(options.page ?? 1),
    pageSize: String(options.pageSize ?? 100),
  });
  if (options.status) params.set('status', options.status);
  if (options.reviewMode) params.set('reviewMode', options.reviewMode);
  return request<{ items: AiResult[]; total: number; page: number; pageSize: number }>(
    `/api/provider/ai-annotation-results?${params.toString()}`,
  );
}

export async function reviewAiResult(
  resultId: string,
  input: { action: string; acceptedResult?: Record<string, unknown> | null; comment?: string },
) {
  return request<{ message: string; affectedCount: number }>(
    `/api/provider/ai-annotation-results/${resultId}/review`,
    { method: 'POST', body: JSON.stringify(input) },
  );
}

export async function batchAcceptAiResults(batchId: string, resultIds: string[], comment?: string) {
  return request<{ message: string; affectedCount: number }>('/api/provider/ai-annotation-results/batch-review', {
    method: 'POST',
    body: JSON.stringify({ batchId, resultIds, action: 'accept', comment }),
  });
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem('token');
  if (!token) throw new Error('登录状态已失效');
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      ...init.headers,
    },
  });
  const data = await response.json().catch(() => null);
  if (!response.ok) throw new Error(data?.message || `请求失败 (${response.status})`);
  return data as T;
}
