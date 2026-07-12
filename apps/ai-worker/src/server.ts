import { formatConfigError, loadServerEnvironmentConfig } from './config.js';
import { BackendApiClient } from './backend/api-client.js';
import { LlmClient } from './llm/llm-client.js';
import { Logger } from './log.js';
import { BatchRunner } from './runner/batch-runner.js';
import { createWorkerHttpServer } from './server/http-server.js';

async function main() {
  const environment = loadServerEnvironmentConfig();
  const backend = new BackendApiClient(environment.apiBaseUrl, environment.workerToken);
  const llm = new LlmClient(environment.llm);
  const serverLogger = new Logger('info');
  const workerServer = createWorkerHttpServer({
    triggerToken: environment.triggerToken,
    createRunner: (runConfig) => new BatchRunner(runConfig, backend, llm, new Logger(runConfig.logLevel)),
    failBatch: (batchId, errorMessage) => backend.failBatch(batchId, errorMessage),
    logger: serverLogger,
  });

  await new Promise<void>((resolve, reject) => {
    workerServer.server.once('error', reject);
    workerServer.server.listen(environment.httpPort, '127.0.0.1', () => resolve());
  });
  serverLogger.info('AI Worker HTTP 服务已启动', {
    address: `http://127.0.0.1:${environment.httpPort}`,
  });

  let shuttingDown = false;
  async function shutdown(signal: string) {
    if (shuttingDown) return;
    shuttingDown = true;
    serverLogger.info('AI Worker HTTP 服务正在停止', { signal });
    try {
      await workerServer.close();
    } catch (error) {
      serverLogger.error('AI Worker HTTP 服务停止失败', { error: formatConfigError(error) });
      process.exitCode = 1;
    }
  }
  process.once('SIGINT', () => void shutdown('SIGINT'));
  process.once('SIGTERM', () => void shutdown('SIGTERM'));
}

main().catch((error) => {
  console.error(JSON.stringify({
    timestamp: new Date().toISOString(),
    level: 'error',
    message: formatConfigError(error),
  }));
  process.exitCode = 1;
});
