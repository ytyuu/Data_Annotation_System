import { formatConfigError, loadCliRunConfig, loadEnvironmentConfig } from './config.js';
import { Logger } from './log.js';
import { BackendApiClient } from './backend/api-client.js';
import { LlmClient } from './llm/llm-client.js';
import { BatchRunner } from './runner/batch-runner.js';

async function main() {
  const environment = loadEnvironmentConfig();
  const runConfig = loadCliRunConfig();
  const logger = new Logger(runConfig.logLevel);
  const backend = new BackendApiClient(environment.apiBaseUrl, environment.workerToken);
  const llm = new LlmClient(environment.llm);
  const runner = new BatchRunner(runConfig, backend, llm, logger);

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
  const message = formatConfigError(error);
  console.error(JSON.stringify({ timestamp: new Date().toISOString(), level: 'error', message }));
  process.exitCode = 1;
});
