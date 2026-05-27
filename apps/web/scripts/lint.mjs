import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { buildWeb, srcRoot, tscBin, webRoot } from './lib.mjs';

const indexPath = path.join(srcRoot, 'index.html');
const appPath = path.join(srcRoot, 'app.ts');

if (!fs.existsSync(indexPath)) {
  throw new Error('Missing src/index.html');
}
if (!fs.existsSync(appPath)) {
  throw new Error('Missing src/app.ts');
}

const html = fs.readFileSync(indexPath, 'utf8');
if (!html.includes('<script src="./app.js"></script>')) {
  throw new Error('index.html must load app.js');
}

// TypeScript 类型检查
const result = spawnSync('node', [tscBin, '--noEmit'], { cwd: webRoot, stdio: 'inherit' });
if (result.status !== 0) {
  throw new Error('TypeScript type check failed');
}

buildWeb();
console.log('Web lint passed.');

