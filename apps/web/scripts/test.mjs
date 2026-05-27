import fs from 'node:fs';
import path from 'node:path';
import { buildWeb, requestText, srcRoot, startWebServer } from './lib.mjs';

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

async function waitForServer(port, timeoutMs = 4000) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeoutMs) {
    try {
      const result = await requestText(port, '/');
      if (result.statusCode === 200) {
        return;
      }
    } catch {
      // keep waiting
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error(`Web server did not start within ${timeoutMs}ms`);
}

buildWeb();
const port = Number(process.env.PORT ?? '3001');
const server = startWebServer(port, srcRoot);

try {
  await waitForServer(port);

  const index = await requestText(port, '/');
  assert(index.statusCode === 200, `Expected 200 from web root, got ${index.statusCode}`);
  assert(index.body.includes('Turbo Monorepo Demo'), 'Web root HTML did not load correctly');

  const appJs = await requestText(port, '/app.js');
  assert(appJs.statusCode === 200, `Expected 200 from /app.js, got ${appJs.statusCode}`);
  assert(appJs.body.includes('http://localhost:7000'), 'Web JS did not include the API URL');

  const distIndex = path.join(path.dirname(srcRoot), 'dist', 'index.html');
  assert(fs.existsSync(distIndex), 'Build output dist/index.html is missing');

  console.log('Web tests passed.');
} finally {
  server.close();
}

