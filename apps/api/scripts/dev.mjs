import { compileJava, startJavaServer } from './lib.mjs';

const port = Number(process.env.PORT ?? '7000');
compileJava();
const server = startJavaServer(port);

console.log(`API server started at http://localhost:${port}`);

const shutdown = () => {
  server.kill('SIGTERM');
  process.exit(0);
};

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

