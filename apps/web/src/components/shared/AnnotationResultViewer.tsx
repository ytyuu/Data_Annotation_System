import React from 'react';
import { AnnotationSchema, AnnotationOption } from './AnnotationEditor';

export interface AnnotationResultViewerProps {
  /** 标注结果 JSON 字符串 */
  result: string | null;
  /** 标注结构定义，用于映射 value -> label */
  schema: AnnotationSchema | null;
  /** 是否显示争议标记 */
  isDisputed?: boolean | null;
  /** 自定义样式 */
  className?: string;
}

/**
 * 从标注结果 JSON 中提取选择值。
 */
export function parseAnnotationSelection(
  result: string,
  schema: AnnotationSchema | null
): string[] {
  try {
    const parsed = JSON.parse(result) as unknown;
    if (schema?.selectionMode === 'multiple') {
      if (Array.isArray((parsed as { values?: unknown }).values)) {
        return (parsed as { values: string[] }).values.filter(Boolean);
      }
    }
    if (typeof (parsed as { value?: unknown }).value === 'string') {
      return [(parsed as { value: string }).value];
    }
    if (Array.isArray(parsed)) {
      return parsed.filter((item): item is string => typeof item === 'string');
    }
    if (typeof parsed === 'string') {
      return [parsed];
    }
    return [];
  } catch {
    return [];
  }
}

/**
 * 将选择值映射为人类可读的标签。
 */
export function mapValuesToLabels(
  values: string[],
  schema: AnnotationSchema | null
): string[] {
  if (!schema?.options || schema.options.length === 0) {
    return values;
  }
  const labelMap = new Map(schema.options.map((opt) => [opt.value, opt.label]));
  return values.map((v) => labelMap.get(v) || v);
}

/**
 * 格式化标注结果为可读的字符串。
 */
export function formatAnnotationResult(
  result: string | null,
  schema: AnnotationSchema | null
): string {
  if (!result) return '无';
  const values = parseAnnotationSelection(result, schema);
  const labels = mapValuesToLabels(values, schema);
  return labels.join(', ') || '无';
}

/**
 * 标注结果展示组件。
 *
 * 将标注结果 JSON 解析并映射为人类可读的标签展示。
 * 未来扩展新标注类型时，在此组件中添加新的结果格式化逻辑。
 */
export const AnnotationResultViewer: React.FC<AnnotationResultViewerProps> = ({
  result,
  schema,
  isDisputed = false,
  className = '',
}) => {
  const displayText = formatAnnotationResult(result, schema);

  return (
    <div className={`flex items-center gap-2 ${className}`}>
      <span className="text-blue-700">{displayText}</span>
      {isDisputed && (
        <span className="rounded bg-red-100 px-2 py-0.5 text-xs text-red-700">
          争议
        </span>
      )}
    </div>
  );
};

export default AnnotationResultViewer;
