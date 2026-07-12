import assert from 'node:assert/strict';
import type { AddressInfo } from 'node:net';
import test from 'node:test';
import type { BatchRunConfig } from '../config.js';
import {
  createWorkerHttpServer,
  type BackgroundBatchRunner,
  type WorkerHttpServerOptions,
} from './http-server.js';

const batchId = '00000000-0000-4000-8000-000000000100';
const triggerToken = 'test-trigger-token';
const silentLogger = { error() {} };

test('health 无需鉴权并返回运行数量', async (context) => {
  const fixture = await startServer({ createRunner: () => completedRunner() });
  context.after(() => fixture.close());

  const response = await fetch(`${fixture.baseUrl}/health`);

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { status: 'ok', runningBatchCount: 0 });
});

test('run 缺失或错误 token 返回 401', async (context) => {
  const fixture = await startServer({ createRunner: () => completedRunner() });
  context.after(() => fixture.close());

  const missing = await fetch(`${fixture.baseUrl}/run`, request({ batchId }, null));
  const wrong = await fetch(`${fixture.baseUrl}/run`, request({ batchId }, 'wrong-token'));

  assert.equal(missing.status, 401);
  assert.equal(wrong.status, 401);
});

test('run 非法 body 和参数返回 400', async (context) => {
  const fixture = await startServer({ createRunner: () => completedRunner() });
  context.after(() => fixture.close());

  const missingBatch = await fetch(`${fixture.baseUrl}/run`, request({}, triggerToken));
  const invalidConcurrency = await fetch(
    `${fixture.baseUrl}/run`,
    request({ batchId, concurrency: 11 }, triggerToken),
  );
  const invalidJson = await fetch(`${fixture.baseUrl}/run`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${triggerToken}`, 'Content-Type': 'application/json' },
    body: '{',
  });

  assert.equal(missingBatch.status, 400);
  assert.equal(invalidConcurrency.status, 400);
  assert.equal(invalidJson.status, 400);
});

test('run 立即返回 202 且同一批次运行中返回 409', async (context) => {
  let finish: (() => void) | undefined;
  const runner: BackgroundBatchRunner = {
    run: () => new Promise<void>((resolve) => { finish = resolve; }),
    requestStop() { finish?.(); },
  };
  const fixture = await startServer({ createRunner: () => runner });
  context.after(() => fixture.close());

  const accepted = await fetch(`${fixture.baseUrl}/run`, request({ batchId }, triggerToken));
  const duplicate = await fetch(`${fixture.baseUrl}/run`, request({ batchId }, triggerToken));

  assert.equal(accepted.status, 202);
  assert.equal(duplicate.status, 409);
  assert.deepEqual([...fixture.runningBatchIds], [batchId]);
  finish?.();
  await waitFor(() => fixture.runningBatchIds.size === 0);
});

test('后台异常上报批次失败并释放运行登记', async (context) => {
  const failures: Array<{ batchId: string; message: string }> = [];
  const fixture = await startServer({
    createRunner: () => ({
      async run() { throw new Error('模拟后台失败'); },
      requestStop() {},
    }),
    failBatch: async (failedBatchId, message) => { failures.push({ batchId: failedBatchId, message }); },
  });
  context.after(() => fixture.close());

  const response = await fetch(`${fixture.baseUrl}/run`, request({ batchId }, triggerToken));

  assert.equal(response.status, 202);
  await waitFor(() => failures.length === 1 && fixture.runningBatchIds.size === 0);
  assert.deepEqual(failures, [{ batchId, message: '模拟后台失败' }]);
});

async function startServer(overrides: Partial<WorkerHttpServerOptions>) {
  const workerServer = createWorkerHttpServer({
    triggerToken,
    createRunner: () => completedRunner(),
    failBatch: async () => {},
    logger: silentLogger,
    ...overrides,
  });
  await new Promise<void>((resolve) => workerServer.server.listen(0, '127.0.0.1', resolve));
  const address = workerServer.server.address() as AddressInfo;
  return {
    baseUrl: `http://127.0.0.1:${address.port}`,
    get runningBatchIds() { return workerServer.runningBatchIds; },
    close: () => workerServer.close(),
  };
}

function completedRunner(): BackgroundBatchRunner {
  return { async run() {}, requestStop() {} };
}

function request(body: unknown, token: string | null): RequestInit {
  return {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  };
}

async function waitFor(predicate: () => boolean) {
  const deadline = Date.now() + 1000;
  while (!predicate()) {
    if (Date.now() >= deadline) throw new Error('等待后台状态超时');
    await new Promise((resolve) => setTimeout(resolve, 10));
  }
}
