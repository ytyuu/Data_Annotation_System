import { spawn } from 'node:child_process';
import { buildWeb, distRoot, startWebServer, tailwindCliEntry, tscBin, webRoot } from './lib.mjs';

const port = Number(process.env.PORT ?? '3000');

// 先 build 确保 dist 存在
buildWeb();

// 启动 tsc --watch 后台编译
const tsc = spawn('node', [tscBin, '--watch', '--preserveWatchOutput'], {
  cwd: webRoot,
  stdio: 'inherit'
});

const tailwind = spawn('node', [tailwindCliEntry, '-i', 'src/styles.css', '-o', 'dist/styles.css', '--watch'], {
  cwd: webRoot,
  stdio: 'inherit'
});

tailwind.once('error', (error) => {
  console.error(error);
  process.exit(1);
});

tsc.once('error', (error) => {
  console.error(error);
  process.exit(1);
});

tsc.once('exit', (code) => {
  if (code !== 0 && code !== null) {
    process.exit(code);
  }
});

tailwind.once('exit', (code) => {
  if (code !== 0 && code !== null) {
    process.exit(code);
  }
});

const server = startWebServer(port, distRoot);

server.once('listening', () => {
  console.log(`Web server started at http://localhost:${port}`);
});

server.once('error', (error) => {
  if (error.code === 'EADDRINUSE') {
    console.error(`Web server failed: port ${port} is already in use.`);
  } else {
    console.error(error);
  }
  process.exit(1);
});

const shutdown = () => {
  tsc.kill('SIGTERM');
  tailwind.kill('SIGTERM');
  server.close(() => process.exit(0));
};

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
