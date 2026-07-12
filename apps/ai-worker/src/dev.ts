const entrypoint = process.argv.length > 2 ? './index.js' : './server.js';

await import(entrypoint);
