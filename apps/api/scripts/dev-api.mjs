#!/usr/bin/env node

import { spawn } from "node:child_process";
import net from "node:net";
import { fileURLToPath } from "node:url";

const DEFAULT_PORT = 7000;
const MAX_ATTEMPTS = 2;
const MAVEN_COMMAND = "mvn4";

const port = parsePort(process.argv.slice(2));
const scriptEnv = {
  ...process.env,
  JLINE_TERMINAL: "dumb",
  MAVEN_OPTS: process.env.MAVEN_OPTS ?? "",
};

const stopScriptPath = fileURLToPath(new URL("./stop-api-port.mjs", import.meta.url));
const shutdownSignals = process.platform === "win32"
  ? ["SIGINT", "SIGTERM"]
  : ["SIGINT", "SIGTERM", "SIGUSR2"];

let activeChild = null;

for (const signal of shutdownSignals) {
  process.on(signal, () => {
    if (activeChild && !activeChild.killed) {
      activeChild.kill(signal === "SIGUSR2" ? "SIGTERM" : signal);
    }

    setTimeout(() => {
      process.exit(signal === "SIGUSR2" ? 0 : 130);
    }, 300).unref();
  });
}

const compileExitCode = await runCommand(MAVEN_COMMAND, ["-q", "-ntp", "compile"], {
  env: scriptEnv,
});

if (compileExitCode !== 0) {
  process.exit(compileExitCode);
}

for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt += 1) {
  const runExitCode = await runCommand(MAVEN_COMMAND, ["-ntp", "exec:java", "-Dexec.fork=true"], {
    env: scriptEnv,
  });

  if (runExitCode === 0) {
    process.exit(0);
  }

  if (attempt >= MAX_ATTEMPTS) {
    process.exit(runExitCode);
  }

  const portInUse = await isPortListening(port);
  if (!portInUse) {
    process.exit(runExitCode);
  }

  console.log(`API exited with code ${runExitCode}. Releasing port ${port} and retrying once...`);

  const stopExitCode = await runCommand(
    process.execPath,
    [stopScriptPath, "--port", String(port)],
    { env: process.env },
  );

  if (stopExitCode !== 0) {
    process.exit(runExitCode);
  }
}

function parsePort(args) {
  let value;

  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];

    if ((arg === "--port" || arg === "-p") && args[index + 1]) {
      value = args[index + 1];
      index += 1;
      continue;
    }

    if (arg.startsWith("--port=")) {
      value = arg.slice("--port=".length);
      continue;
    }

    if (/^\d+$/.test(arg)) {
      value = arg;
    }
  }

  const parsed = Number(value ?? DEFAULT_PORT);
  if (!Number.isInteger(parsed) || parsed < 1 || parsed > 65535) {
    console.error(`Invalid port: ${value}`);
    process.exit(1);
  }

  return parsed;
}

function runCommand(command, args, options = {}) {
  return new Promise((resolve) => {
    const child = spawn(command, args, {
      cwd: process.cwd(),
      env: options.env ?? process.env,
      stdio: "inherit",
      shell: false,
      windowsHide: true,
    });

    activeChild = child;

    child.on("error", (error) => {
      if (error.code === "ENOENT") {
        console.error(`Command not found: ${command}`);
        resolve(127);
        return;
      }

      console.error(error.message);
      resolve(1);
    });

    child.on("close", (code, signal) => {
      if (activeChild === child) {
        activeChild = null;
      }

      if (code !== null) {
        resolve(code);
        return;
      }

      resolve(signal ? 130 : 1);
    });
  });
}

function isPortListening(targetPort) {
  return new Promise((resolve) => {
    const server = net.createServer();

    server.once("error", (error) => {
      resolve(error.code === "EADDRINUSE");
    });

    server.once("listening", () => {
      server.close(() => resolve(false));
    });

    server.listen({
      host: "0.0.0.0",
      port: targetPort,
      exclusive: true,
    });
  });
}
