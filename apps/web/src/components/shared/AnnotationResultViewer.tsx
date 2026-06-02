import React from 'react';
import { AnnotationSchema, AnnotationSelection } from './AnnotationEditor';

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
  schema: AnnotationSchema | null,
): AnnotationSelection {
  try {
    const parsed = JSON.parse(result) as unknown;
    const main: string[] = [];
    const sub: Record<string, string[]> = {};

    if (schema?.selectionMode === 'multiple') {
      const values = (parsed as { values?: unknown }).values;
      if (Array.isArray(values)) {
        main.push(...values.filter((v): v is string => typeof v === 'string'));
      }
    } else {
      const value = (parsed as { value?: unknown }).value;
      if (typeof value === 'string') {
        main.push(value);
      }
    }

    const subValues = (parsed as { subValues?: Record<string, unknown> }).subValues;
    if (subValues && typeof subValues === 'object') {
      Object.entries(subValues).forEach(([key, val]) => {
        if (Array.isArray(val)) {
          sub[key] = val.filter((v): v is string => typeof v === 'string');
        }
      });
    }

    return { main, sub };
  } catch {
    return { main: [], sub: {} };
  }
}

/**
 * 将选择值映射为人类可读的标签。
 */
export function mapValuesToLabels(
  values: string[],
  schema: AnnotationSchema | null,
): string[] {
  if (!schema?.options || schema.options.length === 0) {
    return values;
  }
  const labelMap = new Map(schema.options.map((opt) => [opt.value, opt.label]));
  return values.map((v) => labelMap.get(v) || v);
}

/**
 * 将子选项选择值映射为人类可读的标签。
 */
export function mapSubValuesToLabels(
  subValues: Record<string, string[]>,
  schema: AnnotationSchema | null,
): Record<string, string[]> {
  if (!schema?.options) {
    return subValues;
  }

  const result: Record<string, string[]> = {};
  Object.entries(subValues).forEach(([mainValue, subList]) => {
    const mainOption = schema.options!.find((o) => o.value === mainValue);
    if (mainOption?.subOptions) {
      const subLabelMap = new Map(mainOption.subOptions.map((s) => [s.value, s.label]));
      result[mainValue] = subList.map((v) => subLabelMap.get(v) || v);
    } else {
      result[mainValue] = subList;
    }
  });
  return result;
}

/**
 * 格式化标注结果为可读的字符串。
 */
export function formatAnnotationResult(
  result: string | null,
  schema: AnnotationSchema | null,
): string {
  if (!result) return '无';
  const { main, sub } = parseAnnotationSelection(result, schema);
  if (main.length === 0) return '无';

  const mainLabels = mapValuesToLabels(main, schema);
  const subLabelMap = mapSubValuesToLabels(sub, schema);

  const parts = mainLabels.map((mainLabel, index) => {
    const mainValue = main[index];
    const subLabels = mainValue ? subLabelMap[mainValue] : [];
    if (subLabels && subLabels.length > 0) {
      return `${mainLabel} (${subLabels.join(', ')})`;
    }
    return mainLabel;
  });

  return parts.join(', ');
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
