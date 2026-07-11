import { ZodError } from 'zod';
import { loadConfig } from './config.js';
import { Logger } from './log.js';
import { BackendApiClient } from './backend/api-client.js';
import { LlmClient } from './llm/llm-client.js';
import { BatchRunner } from './runner/batch-runner.js';

async function main() {
  const config = loadConfig();
  const logger = new Logger(config.logLevel);
  const backend = new BackendApiClient(config.apiBaseUrl, config.workerToken);
  const llm = new LlmClient(config.llm);
  const runner = new BatchRunner(config, backend, llm, logger);

  process.once('SIGINT', () => runner.requestStop());
  process.once('SIGTERM', () => runner.requestStop());

  try {
    await runner.run();
  } catch (error) {
    logger.error('AI Worker 执行失败，未完成数据将由租约机制回收', {
      error: error instanceof Error ? error.message : '未知错误',
    });
    process.exitCode = 1;
  }
}

main().catch((error) => {
  const message = error instanceof ZodError ? error.issues.map((issue) => {
    const field = issue.path.join('.');
    return field ? `${field}: ${issue.message}` : issue.message;
  }).join('; ')
    : error instanceof Error ? error.message : 'Worker 启动失败';
  console.error(JSON.stringify({ timestamp: new Date().toISOString(), level: 'error', message }));
  process.exitCode = 1;
});
