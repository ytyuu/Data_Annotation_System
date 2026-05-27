import { startWebServer } from './lib.mjs';

const port = Number(process.env.PORT ?? '3000');
const server = startWebServer(port);
console.log(`Web server started at http://localhost:${port}`);

const shutdown = () => {
  server.close(() => process.exit(0));
};

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

