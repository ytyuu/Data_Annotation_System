import fs from 'node:fs';
import path from 'node:path';
import { compileWithMvnd, sourceRoot, startApiServer } from './lib.mjs';

const port = Number(process.env.PORT ?? '7000');
let server = null;
let compileTimer = null;
let compiling = false;
let pendingCompile = false;
let shuttingDown = false;

function stopServer() {
  if (!server || server.killed) {
    return Promise.resolve();
  }

  const currentServer = server;
  server = null;

  const stopped = new Promise((resolve) => {
    currentServer.once('exit', resolve);
    currentServer.once('error', resolve);
  });

  currentServer.kill('SIGTERM');
  return stopped;
}

async function startServer() {
  await stopServer();
  server = startApiServer(port);
  console.log(`API server started at http://localhost:${port}`);
}

async function compileAndRestart(reason) {
  if (compiling) {
    pendingCompile = true;
    return;
  }

  compiling = true;
  pendingCompile = false;

  try {
    console.log(`API ${reason}: compiling with mvnd...`);
    compileWithMvnd();
    if (!shuttingDown) {
      await startServer();
    }
  } catch (error) {
    console.error(error instanceof Error ? error.message : error);
    if (!server) {
      process.exitCode = 1;
    }
  } finally {
    compiling = false;
    if (pendingCompile && !shuttingDown) {
      await compileAndRestart('source changed');
    }
  }
}

function scheduleCompile() {
  clearTimeout(compileTimer);
  compileTimer = setTimeout(() => {
    compileAndRestart('source changed');
  }, 250);
}

function watchDirectory(directory) {
  const watchers = [];

  const watchOne = (currentDirectory) => {
    watchers.push(fs.watch(currentDirectory, (eventType, fileName) => {
      if (!fileName || !String(fileName).endsWith('.kt')) {
        return;
      }

      const changedPath = path.join(currentDirectory, String(fileName));
      if (eventType === 'rename' && fs.existsSync(changedPath) && fs.statSync(changedPath).isDirectory()) {
        watchOne(changedPath);
      }

      scheduleCompile();
    }));
  };

  const walk = (currentDirectory) => {
    watchOne(currentDirectory);
    for (const entry of fs.readdirSync(currentDirectory, { withFileTypes: true })) {
      if (entry.isDirectory()) {
        walk(path.join(currentDirectory, entry.name));
      }
    }
  };

  walk(directory);
  return () => watchers.forEach((watcher) => watcher.close());
}

const closeWatchers = watchDirectory(sourceRoot);
await compileAndRestart('dev startup');

console.log(`API watch mode enabled for ${sourceRoot}`);

const shutdown = async () => {
  shuttingDown = true;
  clearTimeout(compileTimer);
  closeWatchers();
  await stopServer();
  process.exit(0);
};

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

