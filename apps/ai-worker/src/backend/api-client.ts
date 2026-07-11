import { workResponseSchema, type UploadResultsRequest, type WorkResponse } from './types.js';

export class BackendApiClient {
  constructor(
    private readonly baseUrl: string,
    private readonly token: string,
  ) {}

  async claimItems(batchId: string, limit: number): Promise<WorkResponse> {
    const data = await this.request(`/api/ai/annotation-batches/${batchId}/items?limit=${limit}`);
    return workResponseSchema.parse(data);
  }

  async uploadResults(batchId: string, request: UploadResultsRequest): Promise<void> {
    await this.request(`/api/ai/annotation-batches/${batchId}/results`, {
      method: 'POST',
      body: JSON.stringify(request),
    });
  }

  async failBatch(batchId: string, errorMessage: string): Promise<void> {
    await this.request(`/api/ai/annotation-batches/${batchId}/fail`, {
      method: 'POST',
      body: JSON.stringify({ errorMessage }),
    });
  }

  private async request(path: string, init: RequestInit = {}): Promise<unknown> {
    const response = await fetch(`${this.baseUrl}${path}`, {
      ...init,
      headers: {
        Authorization: `Bearer ${this.token}`,
        'Content-Type': 'application/json',
        ...init.headers,
      },
    });
    const data = await response.json().catch(() => null) as { message?: string } | null;
    if (!response.ok) {
      throw new BackendApiError(response.status, data?.message ?? `后端请求失败 (${response.status})`);
    }
    return data;
  }
}

export class BackendApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
    this.name = 'BackendApiError';
  }
}
