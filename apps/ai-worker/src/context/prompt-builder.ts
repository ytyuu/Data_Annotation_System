import type { AnnotationSchema, WorkItem } from '../backend/types.js';

export function buildPrompts(
  annotationGuide: string | null,
  schema: AnnotationSchema,
  items: WorkItem[],
) {
  const resultExample = buildResultExample(schema);

  const systemPrompt = [
    '你是数据标注系统中的文本分类执行器。',
    '你必须对每条输入独立判断，只输出 JSON，不输出 Markdown 或额外解释。',
    '输出必须包含 items 数组，数量与输入完全一致，并原样保留每条 id。',
    '必须严格遵守 result 的字段和类型约定，不得输出 null、额外字段或自行改变数据结构。',
    '不确定时降低 confidenceScore，并将 needsHumanReview 设为 true。',
  ].join('\n');

  const userPrompt = JSON.stringify({
    task: '根据标注说明和分类结构，对输入文本逐条标注并输出 JSON。',
    annotationGuide: annotationGuide ?? '',
    annotationSchema: schema,
    rules: [
      `主选项模式为 ${schema.selectionMode}`,
      schema.selectionMode === 'single'
        ? '主选项为单选：result 必须使用 value 字符串，禁止使用 values。'
        : '主选项为多选：result 必须使用非空 values 字符串数组，禁止使用 value。',
      'value、values 和 subValues 中的值只能使用 annotationSchema 中定义的 value，禁止输出 label。',
      'subValues 如果出现，必须是 JSON 对象，禁止使用 null、字符串或数组作为 subValues 本身。',
      'subValues 的 key 必须是本条结果已选中且配置了子选项的主选项 value。',
      '选中配置了子选项的主选项时，必须在 subValues 中填写该主选项，禁止省略。',
      'subValues 每个 key 对应的值必须是非空字符串数组；即使 subSelectionMode 为 single，也必须写成只有一个元素的数组。',
      'subSelectionMode 为 single 时子选项数组必须恰好包含一个值；为 multiple 时可以包含一个或多个不重复值。',
      '未配置子选项的主选项禁止出现在 subValues 中；没有选择任何带子选项的主选项时，必须完全省略 subValues 字段。',
      'confidence 只能是 high、medium、low，confidenceScore 必须在 0 到 1 之间。',
      'reason 必须说明判断依据，不得复述无关内容。',
      '每个 item 必须完整包含 id、result、confidence、confidenceScore、reason、needsHumanReview。',
    ],
    resultFormatExample: resultExample,
    outputExample: {
      items: [{
        id: items[0]?.itemId ?? 'item-id',
        result: resultExample,
        confidence: 'high',
        confidenceScore: 0.95,
        reason: '判断理由',
        needsHumanReview: false,
      }],
    },
    items: items.map((item) => ({ id: item.itemId, content: item.content, metadata: item.metadata })),
  });

  return { systemPrompt, userPrompt };
}

function buildResultExample(schema: AnnotationSchema): Record<string, unknown> {
  const optionWithSubs = schema.options.find((option) => option.hasSubOptions && (option.subOptions?.length ?? 0) > 0);
  const optionWithoutSubs = schema.options.find((option) => option.value !== optionWithSubs?.value);
  const selectedOptions = schema.selectionMode === 'single'
    ? [optionWithSubs ?? schema.options[0]].filter((option) => option !== undefined)
    : [optionWithSubs, optionWithoutSubs ?? schema.options[0]]
      .filter((option): option is AnnotationSchema['options'][number] => option !== undefined)
      .filter((option, index, options) => options.findIndex((candidate) => candidate.value === option.value) === index);

  const selectedValues = selectedOptions.map((option) => option.value);
  const subValues = Object.fromEntries(selectedOptions.flatMap((option) => {
    const configuredSubs = option.hasSubOptions ? option.subOptions ?? [] : [];
    if (configuredSubs.length === 0) return [];
    const selectedSubs = option.subSelectionMode === 'multiple' ? configuredSubs.slice(0, 2) : configuredSubs.slice(0, 1);
    return [[option.value, selectedSubs.map((subOption) => subOption.value)]];
  }));
  const result: Record<string, unknown> = schema.selectionMode === 'single'
    ? { value: selectedValues[0] ?? 'option_1' }
    : { values: selectedValues.length > 0 ? selectedValues : ['option_1'] };
  if (Object.keys(subValues).length > 0) result.subValues = subValues;
  return result;
}
