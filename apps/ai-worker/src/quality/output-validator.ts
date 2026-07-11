import { z } from 'zod';
import type { AnnotationSchema, UploadResultItem, WorkItem } from '../backend/types.js';

const modelItemSchema = z.object({
  id: z.uuid(),
  result: z.record(z.string(), z.unknown()),
  confidence: z.enum(['high', 'medium', 'low']),
  confidenceScore: z.number().min(0).max(1),
  reason: z.string(),
  needsHumanReview: z.boolean(),
}).strict();

const modelOutputSchema = z.object({ items: z.array(modelItemSchema) }).strict();

export function validateModelOutput(
  rawOutput: unknown,
  inputItems: WorkItem[],
  schema: AnnotationSchema,
): UploadResultItem[] {
  const output = modelOutputSchema.parse(rawOutput);
  const expectedIds = new Set(inputItems.map((item) => item.itemId));
  const actualIds = output.items.map((item) => item.id);
  if (actualIds.length !== expectedIds.size || new Set(actualIds).size !== actualIds.length) {
    throw new ModelOutputError('模型输出数量不一致或包含重复 ID');
  }
  if (actualIds.some((id) => !expectedIds.has(id))) throw new ModelOutputError('模型输出包含未知或遗漏 ID');

  const byId = new Map(output.items.map((item) => [item.id, item]));
  return inputItems.map((input) => {
    const item = byId.get(input.itemId);
    if (!item) throw new ModelOutputError(`模型遗漏 ID：${input.itemId}`);
    validateClassificationResult(item.result, schema);
    return {
      resultId: input.resultId,
      itemId: input.itemId,
      roundNo: input.roundNo,
      result: item.result,
      confidence: item.confidence,
      confidenceScore: item.confidenceScore,
      reason: item.reason.trim(),
      needsHumanReview: item.needsHumanReview,
      rawOutput: item,
    };
  });
}

function validateClassificationResult(result: Record<string, unknown>, schema: AnnotationSchema) {
  const allowedFields = new Set(['value', 'values', 'subValues']);
  if (Object.keys(result).some((field) => !allowedFields.has(field))) {
    throw new ModelOutputError('result 包含不支持的字段');
  }

  const selectedValues = schema.selectionMode === 'single'
    ? typeof result.value === 'string' && result.value ? [result.value] : null
    : Array.isArray(result.values) && result.values.length > 0 && result.values.every((value) => typeof value === 'string')
      ? result.values as string[] : null;
  if (!selectedValues) throw new ModelOutputError('主选项结果格式无效');
  if (new Set(selectedValues).size !== selectedValues.length) throw new ModelOutputError('主选项不能重复');

  const options = new Map(schema.options.map((option) => [option.value, option]));
  if (selectedValues.some((value) => !options.has(value))) throw new ModelOutputError('结果包含未定义的主选项');
  const subValues = result.subValues;
  if (subValues !== undefined && (!subValues || typeof subValues !== 'object' || Array.isArray(subValues))) {
    throw new ModelOutputError('subValues 必须是对象');
  }
  const subMap = (subValues ?? {}) as Record<string, unknown>;
  if (Object.keys(subMap).some((key) => !selectedValues.includes(key))) {
    throw new ModelOutputError('subValues 包含未选中的主选项');
  }

  for (const value of selectedValues) {
    const option = options.get(value)!;
    const configuredSubs = option.hasSubOptions ? option.subOptions ?? [] : [];
    const selectedSubs = subMap[value];
    if (configuredSubs.length === 0) {
      if (selectedSubs !== undefined) throw new ModelOutputError('无子选项的主选项不能填写 subValues');
      continue;
    }
    if (!Array.isArray(selectedSubs) || selectedSubs.length === 0 ||
      !selectedSubs.every((subValue) => typeof subValue === 'string')) {
      throw new ModelOutputError('带子选项的主选项必须选择子选项');
    }
    if (new Set(selectedSubs).size !== selectedSubs.length) throw new ModelOutputError('子选项不能重复');
    if (option.subSelectionMode === 'single' && selectedSubs.length !== 1) {
      throw new ModelOutputError('单选子选项只能选择一项');
    }
    const allowedSubs = new Set(configuredSubs.map((subOption) => subOption.value));
    if (selectedSubs.some((subValue) => !allowedSubs.has(subValue))) {
      throw new ModelOutputError('结果包含未定义的子选项');
    }
  }
}

export class ModelOutputError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ModelOutputError';
  }
}
