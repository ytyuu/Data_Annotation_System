import { startWebServer } from './lib.mjs';

const port = Number(process.env.PORT ?? '3000');
const server = startWebServer(port);

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

