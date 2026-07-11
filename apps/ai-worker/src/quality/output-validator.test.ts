import assert from 'node:assert/strict';
import test from 'node:test';
import type { AnnotationSchema, WorkItem } from '../backend/types.js';
import { ModelOutputError, validateModelOutput } from './output-validator.js';

const schema: AnnotationSchema = {
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
};

const items: WorkItem[] = [
  workItem('00000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000011'),
  workItem('00000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-000000000012'),
];

test('按输入顺序恢复乱序模型输出', () => {
  const result = validateModelOutput({ items: [modelItem(items[1]!, 'safe'), modelItem(items[0]!, 'risk')] }, items, schema);

  assert.deepEqual(result.map((item) => item.itemId), items.map((item) => item.itemId));
  assert.deepEqual(result[0]?.result, { value: 'risk', subValues: { risk: ['high'] } });
});

test('拒绝重复、遗漏和未知 ID', () => {
  assert.throws(
    () => validateModelOutput({ items: [modelItem(items[0]!, 'safe'), modelItem(items[0]!, 'safe')] }, items, schema),
    ModelOutputError,
  );
  assert.throws(
    () => validateModelOutput({ items: [modelItem(items[0]!, 'safe')] }, items, schema),
    ModelOutputError,
  );
});

test('拒绝未定义的分类值和非法子选项', () => {
  const unknownValue = modelItem(items[0]!, 'safe');
  unknownValue.result = { value: 'unknown' };
  assert.throws(() => validateModelOutput({ items: [unknownValue] }, [items[0]!], schema), ModelOutputError);

  const invalidSub = modelItem(items[0]!, 'risk');
  invalidSub.result = { value: 'risk', subValues: { risk: ['unknown'] } };
  assert.throws(() => validateModelOutput({ items: [invalidSub] }, [items[0]!], schema), ModelOutputError);
});

function workItem(resultId: string, itemId: string): WorkItem {
  return { resultId, itemId, roundNo: 1, content: '测试文本', contentType: 'text', metadata: {} };
}

function modelItem(item: WorkItem, value: 'safe' | 'risk'): {
  id: string;
  result: Record<string, unknown>;
  confidence: 'high';
  confidenceScore: number;
  reason: string;
  needsHumanReview: boolean;
} {
  return {
    id: item.itemId,
    result: value === 'risk' ? { value, subValues: { risk: ['high'] } } : { value },
    confidence: 'high' as const,
    confidenceScore: 0.95,
    reason: '符合分类规则',
    needsHumanReview: false,
  };
}
