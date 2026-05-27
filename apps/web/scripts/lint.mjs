import fs from 'node:fs';
import path from 'node:path';
import { buildWeb, runNodeCheck, srcRoot } from './lib.mjs';

const indexPath = path.join(srcRoot, 'index.html');
const appPath = path.join(srcRoot, 'app.js');

if (!fs.existsSync(indexPath)) {
  throw new Error('Missing src/index.html');
}
if (!fs.existsSync(appPath)) {
  throw new Error('Missing src/app.js');
}

const html = fs.readFileSync(indexPath, 'utf8');
if (!html.includes('<script src="./app.js"></script>')) {
  throw new Error('index.html must load app.js');
}

runNodeCheck(appPath);
buildWeb();
console.log('Web lint passed.');

