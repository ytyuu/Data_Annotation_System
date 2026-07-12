import { createHash, timingSafeEqual } from 'node:crypto';
import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http';
import { formatConfigError, parseBatchRunConfig, type BatchRunConfig } from '../config.js';

const MAX_BODY_BYTES = 32 * 1024;

export interface BackgroundBatchRunner {
  run(): Promise<void>;
  requestStop(): void;
}

export interface WorkerHttpServerOptions {
  triggerToken: string;
  createRunner(config: BatchRunConfig): BackgroundBatchRunner;
  failBatch(batchId: string, errorMessage: string): Promise<void>;
  logger: WorkerServerLogger;
}

export interface WorkerServerLogger {
  error(message: string, context?: Record<string, unknown>): void;
}

export interface WorkerHttpServer {
  server: Server;
  runningBatchIds: ReadonlySet<string>;
  close(): Promise<void>;
}

export function createWorkerHttpServer(options: WorkerHttpServerOptions): WorkerHttpServer {
  const runningBatches = new Map<string, BackgroundBatchRunner>();
  const server = createServer((request, response) => {
    void handleRequest(request, response, options, runningBatches).catch((error) => {
      options.logger.error('Worker HTTP 请求处理失败', { error: safeErrorMessage(error) });
      if (!response.headersSent) sendJson(response, 500, { message: 'Worker 内部错误' });
      else response.end();
    });
  });

  return {
    server,
    get runningBatchIds() {
      return new Set(runningBatches.keys());
    },
    async close() {
      runningBatches.forEach((runner) => runner.requestStop());
      if (!server.listening) return;
      await new Promise<void>((resolve, reject) => {
        server.close((error) => error ? reject(error) : resolve());
      });
    },
  };
}

async function handleRequest(
  request: IncomingMessage,
  response: ServerResponse,
  options: WorkerHttpServerOptions,
  runningBatches: Map<string, BackgroundBatchRunner>,
) {
  const url = new URL(request.url ?? '/', 'http://worker.local');
  if (request.method === 'GET' && url.pathname === '/health') {
    sendJson(response, 200, { status: 'ok', runningBatchCount: runningBatches.size });
    return;
  }

  if (request.method !== 'POST' || url.pathname !== '/run') {
    sendJson(response, 404, { message: '接口不存在' });
    return;
  }
  if (!isAuthorized(request.headers.authorization, options.triggerToken)) {
    sendJson(response, 401, { message: 'Worker 触发令牌无效' });
    return;
  }

  let body: unknown;
  try {
    body = await readJsonBody(request);
  } catch (error) {
    const status = error instanceof RequestBodyError ? error.status : 400;
    sendJson(response, status, { message: safeErrorMessage(error) });
    return;
  }

  let config: BatchRunConfig;
  try {
    config = parseBatchRunConfig(body);
  } catch (error) {
    sendJson(response, 400, { message: formatConfigError(error) });
    return;
  }
  if (runningBatches.has(config.batchId)) {
    sendJson(response, 409, { message: '该批次已在 Worker 中运行' });
    return;
  }

  const runner = options.createRunner(config);
  runningBatches.set(config.batchId, runner);
  sendJson(response, 202, { message: '批次已进入后台执行', batchId: config.batchId });
  setImmediate(() => {
    void executeInBackground(config.batchId, runner, options, runningBatches);
  });
}

async function executeInBackground(
  batchId: string,
  runner: BackgroundBatchRunner,
  options: WorkerHttpServerOptions,
  runningBatches: Map<string, BackgroundBatchRunner>,
) {
  try {
    await runner.run();
  } catch (error) {
    const message = safeErrorMessage(error).slice(0, 2000);
    options.logger.error('后台批次执行失败', { batchId, error: message });
    try {
      await options.failBatch(batchId, message);
    } catch (reportError) {
      options.logger.error('批次失败状态上报失败', { batchId, error: safeErrorMessage(reportError) });
    }
  } finally {
    runningBatches.delete(batchId);
  }
}

async function readJsonBody(request: IncomingMessage): Promise<unknown> {
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of request) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    size += buffer.length;
    if (size > MAX_BODY_BYTES) throw new RequestBodyError(413, '请求体过大');
    chunks.push(buffer);
  }
  if (chunks.length === 0) return {};
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8')) as unknown;
  } catch {
    throw new RequestBodyError(400, '请求体不是合法 JSON');
  }
}

function isAuthorized(header: string | undefined, expectedToken: string): boolean {
  if (!header?.startsWith('Bearer ')) return false;
  const actualToken = header.slice('Bearer '.length).trim();
  if (!actualToken) return false;
  const expectedDigest = createHash('sha256').update(expectedToken).digest();
  const actualDigest = createHash('sha256').update(actualToken).digest();
  return timingSafeEqual(expectedDigest, actualDigest);
}

function sendJson(response: ServerResponse, status: number, body: Record<string, unknown>) {
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  response.end(JSON.stringify(body));
}

function safeErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '未知错误';
}

class RequestBodyError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
    this.name = 'RequestBodyError';
  }
}
