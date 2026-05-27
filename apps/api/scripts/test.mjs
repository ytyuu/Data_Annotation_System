import { compileJava, requestJson, startApiServer } from './lib.mjs';

async function waitForServer(port, timeoutMs = 5000) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeoutMs) {
    try {
      const result = await requestJson(port, '/api/health');
      if (result.statusCode === 200) {
        return;
      }
    } catch {
      // keep waiting
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error(`API server did not start within ${timeoutMs}ms`);
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

const port = Number(process.env.PORT ?? '7071');
compileJava();
const server = startApiServer(port);

try {
  await waitForServer(port);

  const health = await requestJson(port, '/api/health');
  assert(health.statusCode === 200, `Expected 200 from /api/health, got ${health.statusCode}`);
  assert(health.body.includes('"status":"ok"'), 'Health endpoint did not return the expected payload');

  const hello = await requestJson(port, '/api/hello?name=Turbo');
  assert(hello.statusCode === 200, `Expected 200 from /api/hello, got ${hello.statusCode}`);
  assert(hello.body.includes('Hello, Turbo!'), 'Hello endpoint did not personalize the response');

  const root = await requestJson(port, '/');
  assert(root.statusCode === 200, `Expected 200 from /, got ${root.statusCode}`);
  assert(root.body.includes('API server is running'), 'Root endpoint did not return the expected message');

  console.log('API tests passed.');
} finally {
  server.close();
}

