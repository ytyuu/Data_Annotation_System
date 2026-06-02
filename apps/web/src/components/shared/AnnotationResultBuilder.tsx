import React from 'react';
import { parseAnnotationSelection, mapValuesToLabels, mapSubValuesToLabels } from './AnnotationResultViewer';
import type { AnnotationSchema, AnnotationSelection } from './AnnotationEditor';

export interface AnnotationResultBuilderProps {
  /** 标注结构定义 */
  schema: AnnotationSchema | null;
  /** 当前选中的值 */
  selection: AnnotationSelection;
}

/**
 * 构建标注结果 JSON 字符串。
 *
 * 根据 schema 的 selectionMode 生成对应的 JSON 格式：
 * - single 无子选项: { value: "xxx" }
 * - single 有子选项: { value: "xxx", subValues: { "xxx": ["yyy"] } }
 * - multiple 无子选项: { values: ["xxx", "yyy"] }
 * - multiple 有子选项: { values: ["xxx"], subValues: { "xxx": ["yyy", "zzz"] } }
 *
 * 未来扩展新标注类型时，在此函数中添加新的结果构建逻辑。
 */
export function buildAnnotationResult(
  selection: AnnotationSelection,
  schema: AnnotationSchema | null,
): string {
  const hasSubValues = Object.keys(selection.sub).length > 0;

  if (schema?.selectionMode === 'multiple') {
    const result: Record<string, unknown> = { values: selection.main };
    if (hasSubValues) {
      result.subValues = selection.sub;
    }
    return JSON.stringify(result);
  }

  const mainValue = selection.main[0] ?? null;
  const result: Record<string, unknown> = { value: mainValue };
  if (hasSubValues && mainValue) {
    result.subValues = selection.sub;
  }
  return JSON.stringify(result);
}

/**
 * 标注结果构建器组件（用于展示预览）。
 */
export const AnnotationResultBuilder: React.FC<AnnotationResultBuilderProps> = ({
  schema,
  selection,
}) => {
  const result = buildAnnotationResult(selection, schema);
  const mainLabels = mapValuesToLabels(selection.main, schema);
  const subLabelMap = mapSubValuesToLabels(selection.sub, schema);

  const displayParts = mainLabels.map((mainLabel, index) => {
    const mainValue = selection.main[index];
    const subLabels = mainValue ? subLabelMap[mainValue] : [];
    if (subLabels && subLabels.length > 0) {
      return `${mainLabel} (${subLabels.join(', ')})`;
    }
    return mainLabel;
  });

  return (
    <div className="rounded border border-gray-200 bg-gray-50 p-3 text-xs text-gray-600">
      <div className="font-medium text-gray-700">结果预览</div>
      <div className="mt-1">标签：{displayParts.join(', ') || '无'}</div>
      <div className="mt-1 font-mono text-gray-500">{result}</div>
    </div>
  );
};

export default AnnotationResultBuilder;
