import assert from 'node:assert/strict';
import test from 'node:test';
import { loadCliRunConfig, loadServerEnvironmentConfig, parseBatchRunConfig } from './config.js';

const batchId = '00000000-0000-4000-8000-000000000100';

test('HTTP 与 CLI 运行参数复用相同默认值和校验', () => {
  const fromHttp = parseBatchRunConfig({ batchId });
  const fromCli = loadCliRunConfig(['--batch-id', batchId]);

  assert.deepEqual(fromHttp, fromCli);
  assert.equal(fromHttp.chunkSize, 100);
  assert.equal(fromHttp.modelBatchSize, 10);
  assert.equal(fromHttp.concurrency, 2);
});

test('HTTP 运行参数拒绝缺失批次和非法范围', () => {
  assert.throws(() => parseBatchRunConfig({}), /batchId/);
  assert.throws(() => parseBatchRunConfig({ batchId, concurrency: 0 }), /concurrency/);
  assert.throws(() => parseBatchRunConfig({ batchId, unknown: true }), /Unrecognized key/);
});

test('服务模式读取触发令牌和默认端口', () => {
  const environment = loadServerEnvironmentConfig({
    API_BASE_URL: 'http://localhost:7000',
    AI_WORKER_TOKEN: 'callback-token',
    LLM_API_KEY: 'llm-key',
    WORKER_TRIGGER_TOKEN: 'trigger-token',
  });

  assert.equal(environment.triggerToken, 'trigger-token');
  assert.equal(environment.httpPort, 7100);
  assert.equal(environment.apiBaseUrl, 'http://localhost:7000');
});

test('服务模式要求独立的触发令牌', () => {
  assert.throws(() => loadServerEnvironmentConfig({
    AI_WORKER_TOKEN: 'callback-token',
    LLM_API_KEY: 'llm-key',
  }), /WORKER_TRIGGER_TOKEN/);
});
