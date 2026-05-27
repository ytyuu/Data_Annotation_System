import { spawn, spawnSync } from 'node:child_process';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
export const apiRoot = path.resolve(scriptDir, '..');
export const workspaceRoot = path.resolve(apiRoot, '..', '..');
export const sourceRoot = path.join(apiRoot, 'src', 'main', 'kotlin');
export const buildRoot = path.join(apiRoot, 'target');
export const classesRoot = path.join(buildRoot, 'classes');
export const mainClass = 'com.example.api.Main';
export const mvndCommand = process.platform === 'win32' ? 'mvnd.exe' : 'mvnd';

function collectKotlinFiles(dir) {
  if (!fs.existsSync(dir)) {
    return [];
  }

  const entries = fs.readdirSync(dir, { withFileTypes: true });
  const files = [];

  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...collectKotlinFiles(fullPath));
    } else if (entry.isFile() && fullPath.endsWith('.kt')) {
      files.push(fullPath);
    }
  }

  return files;
}

export function compileKotlin() {
  const sources = collectKotlinFiles(sourceRoot);
  if (sources.length === 0) {
    throw new Error(`No Kotlin sources found under ${sourceRoot}`);
  }

  return compileWithMvnd();
}

export function compileWithMvnd() {
  const result = spawnSync(mvndCommand, ['-q', 'compile'], {
    cwd: apiRoot,
    stdio: 'inherit'
  });

  if (result.error?.code === 'ENOENT') {
    throw new Error('mvnd was not found. Install Maven Daemon and make sure mvnd is available on PATH.');
  }

  if (result.status !== 0) {
    throw new Error(`mvnd compile failed with exit code ${result.status ?? 'unknown'}`);
  }

  return classesRoot;
}

export function startApiServer(port) {
  return spawn(mvndCommand, ['-q', 'exec:java', `-Dexec.args=${port}`], {
    stdio: 'inherit',
    cwd: apiRoot,
    env: {
      ...process.env,
      PORT: String(port)
    }
  });
}

export function requestJson(port, requestPath) {
  return new Promise((resolve, reject) => {
    const request = http.get(
      {
        hostname: '127.0.0.1',
        port,
        path: requestPath,
        headers: {
          Accept: 'application/json'
        }
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

