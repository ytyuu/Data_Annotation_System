import type { AnnotationSchema, WorkItem } from '../backend/types.js';

export function buildPrompts(
  annotationGuide: string | null,
  schema: AnnotationSchema,
  items: WorkItem[],
) {
  const outputExample = schema.selectionMode === 'single'
    ? { value: schema.options[0]?.value ?? 'option_1' }
    : { values: schema.options.slice(0, 2).map((option) => option.value) };

  const systemPrompt = [
    '你是数据标注系统中的文本分类执行器。',
    '你必须对每条输入独立判断，只输出 JSON，不输出 Markdown 或额外解释。',
    '输出必须包含 items 数组，数量与输入完全一致，并原样保留每条 id。',
    '不确定时降低 confidenceScore，并将 needsHumanReview 设为 true。',
  ].join('\n');

  const userPrompt = JSON.stringify({
    task: '根据标注说明和分类结构，对输入文本逐条标注并输出 JSON。',
    annotationGuide: annotationGuide ?? '',
    annotationSchema: schema,
    rules: [
      `主选项模式为 ${schema.selectionMode}`,
      'result 必须使用 value/values/subValues，并且只能使用 schema 中定义的 value。',
      '选中带子选项的主选项时，必须按照 subSelectionMode 填写 subValues。',
      'confidence 只能是 high、medium、low，confidenceScore 必须在 0 到 1 之间。',
      'reason 必须说明判断依据，不得复述无关内容。',
    ],
    outputExample: {
      items: [{
        id: items[0]?.itemId ?? 'item-id',
        result: outputExample,
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
