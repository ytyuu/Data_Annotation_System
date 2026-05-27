import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
export const webRoot = path.resolve(scriptDir, '..');
export const workspaceRoot = path.resolve(webRoot, '..', '..');
export const srcRoot = path.join(webRoot, 'src');
export const distRoot = path.join(webRoot, 'dist');
export const tscBin = path.join(workspaceRoot, 'node_modules', 'typescript', 'bin', 'tsc');

function copyRecursive(source, target) {
  const stat = fs.statSync(source);
  if (stat.isDirectory()) {
	fs.mkdirSync(target, { recursive: true });
	for (const entry of fs.readdirSync(source)) {
	  copyRecursive(path.join(source, entry), path.join(target, entry));
	}
	return;
  }

  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.copyFileSync(source, target);
}

export function buildWeb() {
  fs.rmSync(distRoot, { recursive: true, force: true });
  fs.mkdirSync(distRoot, { recursive: true });

  // 编译 TypeScript
  const result = spawnSync('node', [tscBin], { cwd: webRoot, stdio: 'inherit' });
  if (result.status !== 0) {
    throw new Error('TypeScript compilation failed');
  }

  // 复制非 TS 静态文件到 dist
  for (const file of ['index.html', 'styles.css']) {
    const srcPath = path.join(srcRoot, file);
    const dstPath = path.join(distRoot, file);
    if (fs.existsSync(srcPath)) {
      fs.copyFileSync(srcPath, dstPath);
    }
  }

  return distRoot;
}

function mimeType(filePath) {
  switch (path.extname(filePath)) {
	case '.html':
	  return 'text/html; charset=utf-8';
	case '.css':
	  return 'text/css; charset=utf-8';
	case '.js':
	  return 'text/javascript; charset=utf-8';
	case '.json':
	  return 'application/json; charset=utf-8';
	case '.svg':
	  return 'image/svg+xml';
	default:
	  return 'application/octet-stream';
  }
}

export function startWebServer(port, rootDir = srcRoot) {
  const server = http.createServer((request, response) => {
	const urlPath = new URL(request.url ?? '/', `http://${request.headers.host ?? 'localhost'}`).pathname;
	const relativePath = urlPath === '/' ? 'index.html' : urlPath.replace(/^\//, '');
	const filePath = path.join(rootDir, relativePath);

	if (!filePath.startsWith(rootDir)) {
	  response.writeHead(403, { 'Content-Type': 'text/plain; charset=utf-8' });
	  response.end('Forbidden');
	  return;
	}

	if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
	  response.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
	  response.end('Not Found');
	  return;
	}

	response.writeHead(200, { 'Content-Type': mimeType(filePath) });
	fs.createReadStream(filePath).pipe(response);
  });

  server.listen(port, '127.0.0.1');
  return server;
}

export function requestText(port, requestPath) {
  return new Promise((resolve, reject) => {
	const request = http.get(
	  {
		hostname: '127.0.0.1',
		port,
		path: requestPath
	  },
	  (response) => {
		const chunks = [];
		response.on('data', (chunk) => chunks.push(chunk));
		response.on('end', () => {
		  resolve({
			statusCode: response.statusCode ?? 0,
			body: Buffer.concat(chunks).toString('utf8')
		  });
		});
	  }
	);

	request.on('error', reject);
  });
}

export function runNodeCheck(filePath) {
  const result = spawnSync('node', ['--check', filePath], { stdio: 'inherit' });
  if (result.status !== 0) {
	throw new Error(`Node syntax check failed for ${filePath}`);
  }
}

