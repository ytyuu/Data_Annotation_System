import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, isAbsolute, resolve } from 'node:path';
import { createInterface } from 'node:readline/promises';
import { stdin as input, stdout as output } from 'node:process';

function readOption(name, fallback) {
  const prefix = `--${name}=`;
  const inlineValue = process.argv.find((arg) => arg.startsWith(prefix));
  if (inlineValue) {
    return inlineValue.slice(prefix.length);
  }

  const index = process.argv.indexOf(`--${name}`);
  if (index >= 0 && process.argv[index + 1]) {
    return process.argv[index + 1];
  }

  return fallback;
}

function hasOption(name) {
  return process.argv.includes(`--${name}`) || process.argv.some((arg) => arg.startsWith(`--${name}=`));
}

function toPositiveInteger(value, name) {
  const parsed = Number.parseInt(value, 10);
  if (!Number.isInteger(parsed) || parsed <= 0) {
    throw new Error(`${name} must be a positive integer.`);
  }
  return parsed;
}

function createSeededRandom(seed) {
  let state = seed >>> 0;
  return () => {
    state = (1664525 * state + 1013904223) >>> 0;
    return state / 0x100000000;
  };
}

function normalizeQuestionType(value) {
  const trimmed = value.trim().toLowerCase();
  if (['1', 'single', 'radio', '单选'].includes(trimmed)) {
    return 'single';
  }
  if (['2', 'multiple', 'checkbox', 'multi', '多选'].includes(trimmed)) {
    return 'multiple';
  }
  throw new Error('question-type must be single or multiple.');
}

function normalizeMode(value) {
  const trimmed = value.trim().toLowerCase();
  if (['round-robin', 'roundrobin', '轮转', ''].includes(trimmed)) {
    return 'round-robin';
  }
  if (['random', '随机'].includes(trimmed)) {
    return 'random';
  }
  throw new Error('mode must be either round-robin or random.');
}

async function askRequired(rl, question, fallback) {
  const answer = await rl.question(fallback ? `${question} (${fallback}): ` : `${question}: `);
  return answer.trim() || fallback;
}

async function readConfig() {
  const interactive = !hasOption('count') && !hasOption('option-count') && !hasOption('question-type');

  if (!interactive) {
    return {
      count: toPositiveInteger(readOption('count', '100'), 'count'),
      optionCount: toPositiveInteger(readOption('option-count', '4'), 'option-count'),
      outputPath: readOption('output', 'test-dataset.txt'),
      mode: normalizeMode(readOption('mode', 'round-robin')),
      questionType: normalizeQuestionType(readOption('question-type', 'single')),
      seedValue: readOption('seed', ''),
    };
  }

  if (!input.isTTY) {
    const answers = readFileSync(0, 'utf8').split(/\r?\n/);
    const nextAnswer = (fallback) => {
      const answer = answers.shift();
      return answer?.trim() || fallback;
    };

    return {
      questionType: normalizeQuestionType(nextAnswer('1')),
      optionCount: toPositiveInteger(nextAnswer('4'), 'option-count'),
      count: toPositiveInteger(nextAnswer('100'), 'count'),
      outputPath: nextAnswer('test-dataset.txt'),
      mode: normalizeMode(nextAnswer('round-robin')),
      seedValue: nextAnswer(''),
    };
  }

  const rl = createInterface({ input, output });
  try {
    console.log('请选择题型：');
    console.log('1. 单选');
    console.log('2. 多选');

    return {
      questionType: normalizeQuestionType(await askRequired(rl, '输入题型序号', '1')),
      optionCount: toPositiveInteger(await askRequired(rl, '选项数量', '4'), 'option-count'),
      count: toPositiveInteger(await askRequired(rl, '生成条数', '100'), 'count'),
      outputPath: await askRequired(rl, '输出文件', 'test-dataset.txt'),
      mode: normalizeMode(await askRequired(rl, '答案生成方式 round-robin/random', 'round-robin')),
      seedValue: await askRequired(rl, '随机种子，留空则不固定', ''),
    };
  } finally {
    rl.close();
  }
}

function buildAnswer({ index, mode, optionCount, questionType, random }) {
  if (questionType === 'single') {
    return mode === 'random'
      ? [Math.floor(random() * optionCount) + 1]
      : [(index % optionCount) + 1];
  }

  const answerCount = mode === 'random'
    ? Math.floor(random() * Math.min(optionCount, 3)) + 1
    : (index % Math.min(optionCount, 3)) + 1;
  const selected = new Set();

  while (selected.size < answerCount) {
    const optionNumber = mode === 'random'
      ? Math.floor(random() * optionCount) + 1
      : ((index + selected.size) % optionCount) + 1;
    selected.add(optionNumber);
  }

  return [...selected].sort((left, right) => left - right);
}

const config = await readConfig();
const random = config.seedValue === ''
  ? Math.random
  : createSeededRandom(toPositiveInteger(config.seedValue, 'seed'));

const lines = Array.from({ length: config.count }, (_, index) => {
  const itemNumber = index + 1;
  const answers = buildAnswer({ index, random, ...config });
  const answerText = answers.map((optionNumber) => `选项 ${optionNumber}`).join('、');

  return `第 ${itemNumber} 条-应该选${answerText}`;
});

const resolvedOutputPath = isAbsolute(config.outputPath) ? config.outputPath : resolve(process.cwd(), config.outputPath);
mkdirSync(dirname(resolvedOutputPath), { recursive: true });
writeFileSync(resolvedOutputPath, `${lines.join('\n')}\n`, 'utf8');

console.log(`Generated ${config.count} ${config.questionType} rows with ${config.optionCount} options: ${resolvedOutputPath}`);
