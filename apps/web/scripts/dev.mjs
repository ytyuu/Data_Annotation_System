import { spawn } from 'node:child_process';
import { buildWeb, distRoot, startWebServer, tscBin } from './lib.mjs';

const port = Number(process.env.PORT ?? '3000');

// 先 build 确保 dist 存在
buildWeb();

// 启动 tsc --watch 后台编译
const tsc = spawn('node', [tscBin, '--watch', '--preserveWatchOutput'], {
  cwd: process.cwd(),
  stdio: 'inherit'
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
  server.close(() => process.exit(0));
};

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

