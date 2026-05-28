import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const webRoot = path.resolve(scriptDir, '..');
const distRoot = path.join(webRoot, 'dist');

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function requestText(port, requestPath) {
  return new Promise((resolve, reject) => {
    const req = http.get({ hostname: '127.0.0.1', port, path: requestPath }, (response) => {
      const chunks = [];
      response.on('data', (chunk) => chunks.push(chunk));
      response.on('end', () => {
        resolve({
          statusCode: response.statusCode ?? 0,
          body: Buffer.concat(chunks).toString('utf8'),
        });
      });
    });
    req.on('error', reject);
  });
}

function startServer(port, rootDir) {
  const mimeTypes = {
    '.html': 'text/html; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
    '.js': 'text/javascript; charset=utf-8',
    '.json': 'application/json; charset=utf-8',
    '.svg': 'image/svg+xml',
  };

  const server = http.createServer((request, response) => {
    const urlPath = new URL(request.url ?? '/', `http://${request.headers.host ?? 'localhost'}`).pathname;
    const relativePath = urlPath === '/' ? 'index.html' : urlPath.replace(/^\//, '');
    const filePath = path.join(rootDir, relativePath);

    if (!filePath.startsWith(rootDir) || !fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
      response.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      response.end('Not Found');
      return;
    }

    response.writeHead(200, { 'Content-Type': mimeTypes[path.extname(filePath)] ?? 'application/octet-stream' });
    fs.createReadStream(filePath).pipe(response);
  });

  server.listen(port, '127.0.0.1');
  return server;
}

async function waitForServer(port, timeoutMs = 4000) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeoutMs) {
    try {
      const result = await requestText(port, '/');
      if (result.statusCode === 200) return;
    } catch {
      // keep waiting
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error(`Server did not start within ${timeoutMs}ms`);
}

// Build first
const viteCli = path.join(webRoot, 'node_modules', 'vite', 'bin', 'vite.js');
const buildResult = spawnSync('node', [viteCli, 'build'], { cwd: webRoot, stdio: 'inherit' });
if (buildResult.status !== 0) {
  throw new Error('Vite build failed');
}

const port = Number(process.env.PORT ?? '3001');
const server = startServer(port, distRoot);

try {
  await waitForServer(port);

  const index = await requestText(port, '/');
  assert(index.statusCode === 200, `Expected 200 from /, got ${index.statusCode}`);
  assert(index.body.includes('id="root"'), 'HTML did not include the React mount point');

  const indexJs = await requestText(port, '/assets/index.js');
  const jsResult = indexJs.statusCode === 200
    ? indexJs
    : await (async () => {
        const files = fs.readdirSync(path.join(distRoot, 'assets')).filter((f) => f.endsWith('.js'));
        assert(files.length > 0, 'No JS files found in dist/assets');
        return requestText(port, `/assets/${files[0]}`);
      })();

  assert(jsResult.statusCode === 200, `Expected 200 from JS bundle, got ${jsResult.statusCode}`);
  assert(jsResult.body.includes('localhost:7000'), 'JS bundle did not include the API URL');
  assert(jsResult.body.includes('Data Annotation System'), 'JS bundle did not include the app content');

  console.log('Web tests passed.');
} finally {
  server.close();
}
