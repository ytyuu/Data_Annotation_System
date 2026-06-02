import React from 'react';
import { parseAnnotationSelection, mapValuesToLabels } from './AnnotationResultViewer';
import type { AnnotationSchema } from './AnnotationEditor';

export interface AnnotationResultBuilderProps {
  /** 标注结构定义 */
  schema: AnnotationSchema | null;
  /** 当前选中的值 */
  selection: string[];
}

/**
 * 构建标注结果 JSON 字符串。
 *
 * 根据 schema 的 selectionMode 生成对应的 JSON 格式：
 * - single: { value: "xxx" }
 * - multiple: { values: ["xxx", "yyy"] }
 *
 * 未来扩展新标注类型时，在此函数中添加新的结果构建逻辑。
 */
export function buildAnnotationResult(
  selection: string[],
  schema: AnnotationSchema | null
): string {
  if (schema?.selectionMode === 'multiple') {
    return JSON.stringify({ values: selection });
  }
  return JSON.stringify({ value: selection[0] ?? null });
}

/**
 * 标注结果构建器组件（用于展示预览）。
 */
export const AnnotationResultBuilder: React.FC<AnnotationResultBuilderProps> = ({
  schema,
  selection,
}) => {
  const result = buildAnnotationResult(selection, schema);
  const labels = mapValuesToLabels(selection, schema);

  return (
    <div className="rounded border border-gray-200 bg-gray-50 p-3 text-xs text-gray-600">
      <div className="font-medium text-gray-700">结果预览</div>
      <div className="mt-1">标签：{labels.join(', ') || '无'}</div>
      <div className="mt-1 font-mono text-gray-500">{result}</div>
    </div>
  );
};

export default AnnotationResultBuilder;
