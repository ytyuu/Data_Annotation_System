import assert from 'node:assert/strict';
import test from 'node:test';
import type { BatchRunConfig } from '../config.js';
import type { AnnotationSchema, UploadResultsRequest, WorkItem, WorkResponse } from '../backend/types.js';
import type { LlmCompletion } from '../llm/llm-client.js';
import { Logger } from '../log.js';
import { BatchRunner, type BatchBackend, type BatchLlm } from './batch-runner.js';

const batchId = '00000000-0000-4000-8000-000000000100';
const datasetId = '00000000-0000-4000-8000-000000000200';
const schema: AnnotationSchema = {
  type: 'classification',
  selectionMode: 'single',
  options: [{ value: 'yes', label: '是' }, { value: 'no', label: '否' }],
};

test('小批失败后减半处理并保持上传顺序', async () => {
  const backend = new FakeBackend([workItem(1), workItem(2)]);
  const llm: BatchLlm = {
    async complete(_model, _systemPrompt, userPrompt): Promise<LlmCompletion> {
      const input = JSON.parse(userPrompt) as { items: Array<{ id: string }> };
      if (input.items.length > 1) throw new Error('模拟批量失败');
      return {
        output: { items: input.items.map((item) => modelItem(item.id)) },
        promptTokens: 10,
        completionTokens: 5,
      };
    },
  };

  await new BatchRunner(config(), backend, llm, new Logger('error')).run();

  assert.equal(backend.uploads.length, 1);
  assert.ok((backend.uploads[0]?.requestId.length ?? 0) <= 80);
  assert.deepEqual(backend.uploads[0]?.items.map((item) => item.itemId), backend.initialItems.map((item) => item.itemId));
  assert.equal(backend.uploads[0]?.modelRequestCount, 3);
  assert.equal(backend.uploads[0]?.promptTokens, 20);
  assert.equal(backend.uploads[0]?.completionTokens, 10);
});

test('单条持续失败时上传可追踪的失败结果', async () => {
  const backend = new FakeBackend([workItem(1)]);
  const llm: BatchLlm = { async complete() { throw new Error('模型空响应'); } };

  await new BatchRunner(config(), backend, llm, new Logger('error')).run();

  assert.equal(backend.uploads.length, 1);
  assert.equal(backend.uploads[0]?.items[0]?.errorMessage, '模型空响应');
  assert.equal(backend.uploads[0]?.modelRequestCount, 1);
});

test('并发上传使用不同且稳定的分段编号', async () => {
  const backend = new FakeBackend([workItem(1), workItem(2)]);
  const llm: BatchLlm = {
    async complete(_model, _systemPrompt, userPrompt): Promise<LlmCompletion> {
      const input = JSON.parse(userPrompt) as { items: Array<{ id: string }> };
      return {
        output: { items: input.items.map((item) => modelItem(item.id)) },
        promptTokens: 10,
        completionTokens: 5,
      };
    },
  };

  await new BatchRunner(
    { ...config(), modelBatchSize: 1, concurrency: 2 },
    backend,
    llm,
    new Logger('error'),
  ).run();

  assert.deepEqual(backend.uploads.map((upload) => upload.chunkNo).sort((left, right) => left - right), [1, 2]);
});

class FakeBackend implements BatchBackend {
  readonly uploads: UploadResultsRequest[] = [];
  readonly initialItems: WorkItem[];
  private claimed = false;

  constructor(items: WorkItem[]) {
    this.initialItems = items;
  }

  async claimItems(): Promise<WorkResponse> {
    const items = this.claimed ? [] : this.initialItems;
    this.claimed = true;
    return {
      batchId,
      datasetId,
      modelName: 'deepseek-v4-flash',
      promptVersion: 'classification-v1',
      annotationGuide: null,
      annotationSchema: schema,
      config: {},
      items,
    };
  }

  async uploadResults(_batchId: string, request: UploadResultsRequest): Promise<void> {
    this.uploads.push(request);
  }
}

function config(): BatchRunConfig {
  return {
    batchId,
    chunkSize: 100,
    modelBatchSize: 10,
    concurrency: 2,
    maxRetries: 0,
    dryRun: false,
    logLevel: 'error',
  };
}

function workItem(index: number): WorkItem {
  const suffix = index.toString().padStart(12, '0');
  return {
    resultId: `00000000-0000-4000-8000-${suffix}`,
    itemId: `00000000-0000-4001-8000-${suffix}`,
    roundNo: 1,
    content: `测试文本 ${index}`,
    contentType: 'text',
    metadata: {},
  };
}

function modelItem(id: string) {
  return {
    id,
    result: { value: 'yes' },
    confidence: 'high',
    confidenceScore: 0.96,
    reason: '符合规则',
    needsHumanReview: false,
  };
}
