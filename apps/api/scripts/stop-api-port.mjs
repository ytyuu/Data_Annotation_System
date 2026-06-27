#!/usr/bin/env node

import { spawn } from "node:child_process";
import { setTimeout as delay } from "node:timers/promises";

const DEFAULT_PORT = 7000;
const port = parsePort(process.argv.slice(2));

const listeners = await findListeningPids(port);

if (listeners.length === 0) {
  console.log(`No API process is listening on port ${port}.`);
  process.exit(0);
}

for (const pid of listeners) {
  await stopProcess(pid);
}

await delay(300);

const remaining = await findListeningPids(port);
if (remaining.length > 0) {
  console.error(`Port ${port} is still in use after stopping API processes.`);
  process.exit(1);
}

console.log(`Port ${port} is free.`);

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

async function findListeningPids(targetPort) {
  const pids = process.platform === "win32"
    ? await findWindowsListeningPids(targetPort)
    : await findUnixListeningPids(targetPort);

  return [...new Set(pids)]
    .filter((pid) => Number.isInteger(pid) && pid > 0)
    .filter((pid) => pid !== process.pid && pid !== process.ppid)
    .sort((left, right) => left - right);
}

async function findUnixListeningPids(targetPort) {
  const lsof = await capture("lsof", ["-nP", `-tiTCP:${targetPort}`, "-sTCP:LISTEN"]);
  if (lsof.ok || lsof.stdout.trim()) {
    return parsePids(lsof.stdout);
  }

  const ss = await capture("ss", ["-ltnp"]);
  if (ss.ok || ss.stdout.trim()) {
    return ss.stdout
      .split(/\r?\n/)
      .filter((line) => line.includes(`:${targetPort} `) || line.includes(`:${targetPort}\t`))
      .flatMap((line) => [...line.matchAll(/pid=(\d+)/g)].map((match) => Number(match[1])));
  }

  return [];
}

async function findWindowsListeningPids(targetPort) {
  const powershellCommand = [
    `Get-NetTCPConnection -LocalPort ${targetPort} -State Listen -ErrorAction SilentlyContinue`,
    "Select-Object -ExpandProperty OwningProcess -Unique",
  ].join(" | ");

  const powershell = await capture("powershell.exe", [
    "-NoProfile",
    "-ExecutionPolicy",
    "Bypass",
    "-Command",
    powershellCommand,
  ]);

  if (powershell.ok || powershell.stdout.trim()) {
    return parsePids(powershell.stdout);
  }

  const netstat = await capture("netstat.exe", ["-ano", "-p", "tcp"]);
  if (!netstat.ok && !netstat.stdout.trim()) {
    return [];
  }

  return netstat.stdout
    .split(/\r?\n/)
    .filter((line) => line.toUpperCase().includes("LISTENING"))
    .filter((line) => line.match(new RegExp(`[:.]${targetPort}\\s`)))
    .map((line) => Number(line.trim().split(/\s+/).at(-1)))
    .filter(Number.isInteger);
}

async function stopProcess(pid) {
  if (process.platform === "win32") {
    console.log(`Stopping process ${pid} listening on port ${port}.`);
    await capture("taskkill.exe", ["/PID", String(pid), "/F"]);
    return;
  }

  console.log(`Stopping process ${pid} listening on port ${port}.`);
  try {
    process.kill(pid, "SIGKILL");
  } catch (error) {
    if (error.code !== "ESRCH") {
      throw error;
    }
  }
}

function capture(command, args) {
  return new Promise((resolve) => {
    const child = spawn(command, args, {
      cwd: process.cwd(),
      env: process.env,
      shell: false,
      windowsHide: true,
    });

    let stdout = "";
    let stderr = "";

    child.stdout?.on("data", (chunk) => {
      stdout += chunk;
    });

    child.stderr?.on("data", (chunk) => {
      stderr += chunk;
    });

    child.on("error", (error) => {
      resolve({
        ok: false,
        code: error.code === "ENOENT" ? 127 : 1,
        stdout,
        stderr: stderr || error.message,
      });
    });

    child.on("close", (code) => {
      resolve({
        ok: code === 0,
        code: code ?? 1,
        stdout,
        stderr,
      });
    });
  });
}

function parsePids(output) {
  return output
    .split(/\s+/)
    .map((value) => Number(value.trim()))
    .filter(Number.isInteger);
}
