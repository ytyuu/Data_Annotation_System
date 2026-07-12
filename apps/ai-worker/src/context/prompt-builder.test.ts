import assert from 'node:assert/strict';
import test from 'node:test';
import type { AnnotationSchema, WorkItem } from '../backend/types.js';
import { buildPrompts } from './prompt-builder.js';

const item: WorkItem = {
  resultId: '00000000-0000-4000-8000-000000000001',
  itemId: '00000000-0000-4000-8000-000000000011',
  roundNo: 1,
  content: '测试文本',
  contentType: 'text',
  metadata: {},
};

test('单选 schema 的提示词使用对象形式的 subValues 示例', () => {
  const prompt = parseUserPrompt({
    type: 'classification',
    selectionMode: 'single',
    options: [
      { value: 'safe', label: '安全' },
      {
        value: 'risk',
        label: '风险',
        hasSubOptions: true,
        subSelectionMode: 'single',
        subOptions: [{ value: 'high', label: '高风险' }],
      },
    ],
  });

  assert.deepEqual(prompt.resultFormatExample, {
    value: 'risk',
    subValues: { risk: ['high'] },
  });
  assert.match(prompt.rules.join('\n'), /subValues 如果出现，必须是 JSON 对象/);
  assert.match(prompt.rules.join('\n'), /single，也必须写成只有一个元素的数组/);
});

test('多选 schema 的提示词按主选项映射多个子选项', () => {
  const prompt = parseUserPrompt({
    type: 'classification',
    selectionMode: 'multiple',
    options: [
      {
        value: 'risk',
        label: '风险',
        hasSubOptions: true,
        subSelectionMode: 'multiple',
        subOptions: [
          { value: 'high', label: '高风险' },
          { value: 'medium', label: '中风险' },
        ],
      },
      { value: 'manual', label: '人工检查' },
    ],
  });

  assert.deepEqual(prompt.resultFormatExample, {
    values: ['risk', 'manual'],
    subValues: { risk: ['high', 'medium'] },
  });
});

test('没有子选项时示例完全省略 subValues', () => {
  const prompt = parseUserPrompt({
    type: 'classification',
    selectionMode: 'single',
    options: [
      { value: 'safe', label: '安全' },
      { value: 'manual', label: '人工检查' },
    ],
  });

  assert.deepEqual(prompt.resultFormatExample, { value: 'safe' });
});

function parseUserPrompt(schema: AnnotationSchema) {
  return JSON.parse(buildPrompts(null, schema, [item]).userPrompt) as {
    rules: string[];
    resultFormatExample: Record<string, unknown>;
  };
}
