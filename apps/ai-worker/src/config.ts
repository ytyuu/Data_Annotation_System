import { config as loadEnv } from 'dotenv';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { z } from 'zod';

const currentDirectory = dirname(fileURLToPath(import.meta.url));
loadEnv({ path: resolve(currentDirectory, '../../../.env'), quiet: true });

const booleanFromEnv = z.string().default('false').transform((value, ctx) => {
  if (value === 'true') return true;
  if (value === 'false') return false;
  ctx.addIssue({ code: 'custom', message: '必须是 true 或 false' });
  return z.NEVER;
});

const envSchema = z.object({
  API_BASE_URL: z.url().default('http://localhost:7000'),
  AI_WORKER_TOKEN: z.string().min(1, '环境变量 AI_WORKER_TOKEN 未配置'),
  LLM_PROVIDER: z.literal('deepseek').default('deepseek'),
  LLM_BASE_URL: z.url().default('https://api.deepseek.com'),
  LLM_MODEL: z.enum(['deepseek-v4-flash', 'deepseek-v4-pro']).default('deepseek-v4-flash'),
  LLM_API_KEY: z.string().min(1, '环境变量 LLM_API_KEY 未配置'),
  LLM_RESPONSE_FORMAT: z.literal('json_object').default('json_object'),
  LLM_THINKING_ENABLED: booleanFromEnv,
  LLM_TEMPERATURE: z.coerce.number().min(0).max(2).default(0.1),
  LLM_MAX_TOKENS: z.coerce.number().int().positive().default(4096),
  LLM_TIMEOUT_MS: z.coerce.number().int().positive().default(60_000),
});

const cliSchema = z.object({
  batchId: z.uuid('请通过 --batch-id <UUID> 指定要执行的批次'),
  chunkSize: z.number().int().min(1).max(500).default(100),
  modelBatchSize: z.number().int().min(1).max(20).default(10),
  concurrency: z.number().int().min(1).max(10).default(2),
  maxRetries: z.number().int().min(0).max(5).default(2),
  dryRun: z.boolean().default(false),
  logLevel: z.enum(['debug', 'info', 'warn', 'error']).default('info'),
});

export type WorkerConfig = ReturnType<typeof loadConfig>;

export function loadConfig(argv = process.argv.slice(2)) {
  const env = envSchema.parse(process.env);
  const args = cliSchema.parse(parseCliArgs(argv));
  return {
    apiBaseUrl: env.API_BASE_URL.replace(/\/$/, ''),
    workerToken: env.AI_WORKER_TOKEN,
    llm: {
      provider: env.LLM_PROVIDER,
      baseUrl: env.LLM_BASE_URL.replace(/\/$/, ''),
      model: env.LLM_MODEL,
      apiKey: env.LLM_API_KEY,
      responseFormat: env.LLM_RESPONSE_FORMAT,
      thinkingEnabled: env.LLM_THINKING_ENABLED,
      temperature: env.LLM_TEMPERATURE,
      maxTokens: env.LLM_MAX_TOKENS,
      timeoutMs: env.LLM_TIMEOUT_MS,
    },
    ...args,
  };
}

function parseCliArgs(argv: string[]): Record<string, unknown> {
  const parsed: Record<string, unknown> = {};
  const numericOptions: Record<string, string> = {
    '--chunk-size': 'chunkSize',
    '--model-batch-size': 'modelBatchSize',
    '--concurrency': 'concurrency',
    '--max-retries': 'maxRetries',
  };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (!argument) throw new Error('命令行参数不能为空');
    if (argument === '--dry-run') {
      parsed.dryRun = true;
      continue;
    }
    const key = argument === '--batch-id' ? 'batchId'
      : argument === '--log-level' ? 'logLevel'
        : argument ? numericOptions[argument] : undefined;
    if (!key) throw new Error(`未知参数：${argument}`);
    const value = argv[index + 1];
    if (!value || value.startsWith('--')) throw new Error(`${argument} 缺少参数值`);
    parsed[key] = argument in numericOptions ? Number(value) : value;
    index += 1;
  }
  return parsed;
}
