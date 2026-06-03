import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, isAbsolute, resolve } from 'node:path';
import { createInterface } from 'node:readline/promises';
import { stdin as input, stdout as output } from 'node:process';

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

async function askRequired(rl, question, fallback) {
  const answer = await rl.question(fallback ? `${question} (${fallback}): ` : `${question}: `);
  return answer.trim() || fallback;
}

// 主选项配置
const MAIN_OPTIONS = [
  { label: '没有子选项', value: 'option_1', hasSubOptions: false },
  { label: '有单选子选项', value: 'option_2', hasSubOptions: true, subOptions: ['子选项 A', '子选项 B', '子选项 C'] },
  { label: '有多选子选项', value: 'option_3', hasSubOptions: true, subOptions: ['1', '2', '3'], subSelectionMode: 'multiple' },
];

async function readConfig() {
  if (!input.isTTY) {
    const answers = readFileSync(0, 'utf8').split(/\r?\n/);
    const nextAnswer = (fallback) => {
      const answer = answers.shift();
      return answer?.trim() || fallback;
    };

    return {
      count: toPositiveInteger(nextAnswer('100'), 'count'),
      outputPath: nextAnswer('test-dataset-suboptions.txt'),
      seedValue: nextAnswer(''),
    };
  }

  const rl = createInterface({ input, output });
  try {
    console.log('============================================');
    console.log('  带子选项分类测试数据生成器');
    console.log('============================================');
    console.log('');
    console.log('支持的标注类型：');
    console.log('  1. 没有子选项');
    console.log('  2. 有单选子选项（子选项 A/B/C）');
    console.log('  3. 有多选子选项（1/2/3，可多选）');
    console.log('');

    return {
      count: toPositiveInteger(await askRequired(rl, '生成条数', '100'), 'count'),
      outputPath: await askRequired(rl, '输出文件', 'test-dataset-suboptions.txt'),
      seedValue: await askRequired(rl, '随机种子，留空则不固定', ''),
    };
  } finally {
    rl.close();
  }
}

function buildAnswer({ index, random }) {
  const mainOptionCount = MAIN_OPTIONS.length;

  // 主选项按轮转分配，确保三种类型均匀分布
  const mainIndex = index % mainOptionCount;
  const mainOption = MAIN_OPTIONS[mainIndex];

  if (!mainOption.hasSubOptions) {
    return { main: mainOption.label };
  }

  const subOptions = mainOption.subOptions;

  if (mainOption.subSelectionMode === 'multiple') {
    // 多选子选项：随机选 1-3 个
    const subCount = Math.floor(random() * subOptions.length) + 1;
    const selected = new Set();
    while (selected.size < subCount) {
      const subIdx = Math.floor(random() * subOptions.length);
      selected.add(subOptions[subIdx]);
    }
    return { main: mainOption.label, sub: [...selected] };
  } else {
    // 单选子选项：随机选择
    const subIdx = Math.floor(random() * subOptions.length);
    return { main: mainOption.label, sub: [subOptions[subIdx]] };
  }
}

function formatAnswer(answer) {
  if (!answer.sub || answer.sub.length === 0) {
    return answer.main;
  }
  return `${answer.main}[${answer.sub.join('、')}]`;
}

const config = await readConfig();
const random = config.seedValue === ''
  ? Math.random
  : createSeededRandom(toPositiveInteger(config.seedValue, 'seed'));

const lines = Array.from({ length: config.count }, (_, index) => {
  const itemNumber = index + 1;
  const answer = buildAnswer({ index, random });
  const answerText = formatAnswer(answer);

  return `第 ${itemNumber} 条-应该选${answerText}`;
});

const resolvedOutputPath = isAbsolute(config.outputPath) ? config.outputPath : resolve(process.cwd(), config.outputPath);
mkdirSync(dirname(resolvedOutputPath), { recursive: true });
writeFileSync(resolvedOutputPath, `${lines.join('\n')}\n`, 'utf8');

console.log('');
console.log(`✓ 已生成 ${config.count} 条测试数据: ${resolvedOutputPath}`);
console.log('');
console.log('数据预览（前 10 条）：');
lines.slice(0, 10).forEach((line) => console.log(`  ${line}`));
console.log('');
console.log('选项分布统计：');
const stats = lines.reduce((acc, line) => {
  if (line.includes('没有子选项')) acc.noSub++;
  else if (line.includes('有单选子选项')) acc.singleSub++;
  else if (line.includes('有多选子选项')) acc.multiSub++;
  return acc;
}, { noSub: 0, singleSub: 0, multiSub: 0 });
console.log(`  没有子选项: ${stats.noSub} 条`);
console.log(`  有单选子选项: ${stats.singleSub} 条`);
console.log(`  有多选子选项: ${stats.multiSub} 条`);
