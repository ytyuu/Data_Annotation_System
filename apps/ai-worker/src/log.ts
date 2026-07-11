type LogLevel = 'debug' | 'info' | 'warn' | 'error';

const priorities: Record<LogLevel, number> = { debug: 10, info: 20, warn: 30, error: 40 };

export class Logger {
  constructor(private readonly level: LogLevel) {}

  debug(message: string, context: Record<string, unknown> = {}) { this.write('debug', message, context); }
  info(message: string, context: Record<string, unknown> = {}) { this.write('info', message, context); }
  warn(message: string, context: Record<string, unknown> = {}) { this.write('warn', message, context); }
  error(message: string, context: Record<string, unknown> = {}) { this.write('error', message, context); }

  private write(level: LogLevel, message: string, context: Record<string, unknown>) {
    if (priorities[level] < priorities[this.level]) return;
    const output = JSON.stringify({ timestamp: new Date().toISOString(), level, message, ...context });
    if (level === 'error') console.error(output);
    else console.log(output);
  }
}
