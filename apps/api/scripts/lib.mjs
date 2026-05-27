import { spawn, spawnSync } from 'node:child_process';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
export const apiRoot = path.resolve(scriptDir, '..');
export const workspaceRoot = path.resolve(apiRoot, '..', '..');
export const sourceRoot = path.join(apiRoot, 'src', 'main', 'java');
export const buildRoot = path.join(apiRoot, 'target');
export const classesRoot = path.join(buildRoot, 'classes');
export const mainClass = 'com.example.api.Main';

function collectJavaFiles(dir) {
  if (!fs.existsSync(dir)) {
    return [];
  }

  const entries = fs.readdirSync(dir, { withFileTypes: true });
  const files = [];

  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...collectJavaFiles(fullPath));
    } else if (entry.isFile() && fullPath.endsWith('.java')) {
      files.push(fullPath);
    }
  }

  return files;
}

export function compileJava({ lint = false } = {}) {
  fs.rmSync(buildRoot, { recursive: true, force: true });
  fs.mkdirSync(classesRoot, { recursive: true });

  const sources = collectJavaFiles(sourceRoot);
  if (sources.length === 0) {
    throw new Error(`No Java sources found under ${sourceRoot}`);
  }

  const args = [];
  if (lint) {
    args.push('-Xlint:all', '-Werror');
  }
  args.push('-encoding', 'UTF-8', '-d', classesRoot, ...sources);

  const result = spawnSync('javac', args, { stdio: 'inherit' });
  if (result.status !== 0) {
    throw new Error(`javac failed with exit code ${result.status ?? 'unknown'}`);
  }

  return classesRoot;
}

export function startJavaServer(port) {
  return spawn('java', ['-cp', classesRoot, mainClass, String(port)], {
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

