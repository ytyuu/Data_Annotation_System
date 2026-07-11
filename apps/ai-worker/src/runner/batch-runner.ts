import { randomUUID } from 'node:crypto';
import type { WorkerConfig } from '../config.js';
import type { AnnotationSchema, UploadResultItem, UploadResultsRequest, WorkItem, WorkResponse } from '../backend/types.js';
import type { LlmCompletion } from '../llm/llm-client.js';
import type { Logger } from '../log.js';
import { buildPrompts } from '../context/prompt-builder.js';
import { validateModelOutput } from '../quality/output-validator.js';

interface ProcessedGroup {
  items: UploadResultItem[];
  modelRequestCount: number;
  promptTokens: number;
  completionTokens: number;
}

export interface BatchBackend {
  claimItems(batchId: string, limit: number): Promise<WorkResponse>;
  uploadResults(batchId: string, request: UploadResultsRequest): Promise<void>;
}

export interface BatchLlm {
  complete(model: string, systemPrompt: string, userPrompt: string): Promise<LlmCompletion>;
}

export class BatchRunner {
  private stopping = false;
  private chunkNo = 0;

  constructor(
    private readonly config: WorkerConfig,
    private readonly backend: BatchBackend,
    private readonly llm: BatchLlm,
    private readonly logger: Logger,
  ) {}

  requestStop() {
    this.stopping = true;
    this.logger.warn('收到终止信号，将在当前模型请求结束后停止');
  }

  async run() {
    this.logger.info('开始执行 AI 标注批次', {
      batchId: this.config.batchId,
      chunkSize: this.config.chunkSize,
      modelBatchSize: this.config.modelBatchSize,
      concurrency: this.config.concurrency,
      dryRun: this.config.dryRun,
    });

    while (!this.stopping) {
      const work = await this.backend.claimItems(this.config.batchId, this.config.chunkSize);
      if (work.items.length === 0) {
        this.logger.info('当前没有可领取数据，Worker 正常结束', { batchId: this.config.batchId });
        return;
      }
      const groups = partition(work.items, this.config.modelBatchSize);
      await mapWithConcurrency(groups, this.config.concurrency, async (group) => {
        const processed = await this.processWithFallback(
          work.modelName,
          work.annotationGuide,
          work.annotationSchema,
          group,
        );
        if (this.config.dryRun) {
          this.logger.info('dry-run 完成，结果未上传且租约将在到期后释放', { itemCount: processed.items.length });
          return;
        }
        const chunkNo = ++this.chunkNo;
        await this.backend.uploadResults(this.config.batchId, {
          requestId: randomUUID(),
          chunkNo,
          modelRequestCount: processed.modelRequestCount,
          promptTokens: processed.promptTokens,
          completionTokens: processed.completionTokens,
          items: processed.items,
        });
        this.logger.info('模型小批结果已上传', {
          chunkNo,
          itemCount: processed.items.length,
          failedCount: processed.items.filter((item) => item.errorMessage).length,
        });
      });
      if (this.config.dryRun) return;
    }
  }

  private async processWithFallback(
    model: string,
    annotationGuide: string | null,
    schema: AnnotationSchema,
    items: WorkItem[],
  ): Promise<ProcessedGroup> {
    let modelRequestCount = 0;
    let promptTokens = 0;
    let completionTokens = 0;
    let lastError: unknown;

    for (let attempt = 0; attempt <= this.config.maxRetries; attempt += 1) {
      try {
        modelRequestCount += 1;
        const prompts = buildPrompts(annotationGuide, schema, items);
        const completion = await this.llm.complete(model, prompts.systemPrompt, prompts.userPrompt);
        promptTokens += completion.promptTokens;
        completionTokens += completion.completionTokens;
        return {
          items: validateModelOutput(completion.output, items, schema),
          modelRequestCount,
          promptTokens,
          completionTokens,
        };
      } catch (error) {
        lastError = error;
        this.logger.warn('模型小批处理失败', {
          itemCount: items.length,
          attempt: attempt + 1,
          error: safeErrorMessage(error),
        });
        if (attempt < this.config.maxRetries) await delay(500 * 2 ** attempt + Math.floor(Math.random() * 250));
      }
    }

    if (items.length > 1) {
      const middle = Math.ceil(items.length / 2);
      const left = await this.processWithFallback(model, annotationGuide, schema, items.slice(0, middle));
      const right = await this.processWithFallback(model, annotationGuide, schema, items.slice(middle));
      return {
        items: [...left.items, ...right.items],
        modelRequestCount: modelRequestCount + left.modelRequestCount + right.modelRequestCount,
        promptTokens: promptTokens + left.promptTokens + right.promptTokens,
        completionTokens: completionTokens + left.completionTokens + right.completionTokens,
      };
    }

    const item = items[0];
    if (!item) throw lastError;
    return {
      items: [{
        resultId: item.resultId,
        itemId: item.itemId,
        roundNo: item.roundNo,
        errorMessage: safeErrorMessage(lastError).slice(0, 2000),
      }],
      modelRequestCount,
      promptTokens,
      completionTokens,
    };
  }
}

function partition<T>(items: T[], size: number): T[][] {
  const groups: T[][] = [];
  for (let index = 0; index < items.length; index += size) groups.push(items.slice(index, index + size));
  return groups;
}

async function mapWithConcurrency<T>(items: T[], concurrency: number, task: (item: T) => Promise<void>) {
  let nextIndex = 0;
  const workers = Array.from({ length: Math.min(concurrency, items.length) }, async () => {
    while (nextIndex < items.length) {
      const index = nextIndex;
      nextIndex += 1;
      const item = items[index];
      if (item !== undefined) await task(item);
    }
  });
  await Promise.all(workers);
}

function safeErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '未知模型错误';
}

function delay(milliseconds: number) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}
