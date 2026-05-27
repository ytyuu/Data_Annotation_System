import { compileJava, startApiServer } from './lib.mjs';

const port = Number(process.env.PORT ?? '7000');
compileJava();
const server = startApiServer(port);

console.log(`API server started at http://localhost:${port}`);

const shutdown = () => {
  server.close(() => process.exit(0));
};

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

