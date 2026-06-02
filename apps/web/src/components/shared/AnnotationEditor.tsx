import React from 'react';

export interface AnnotationOption {
  label: string;
  value: string;
}

export interface AnnotationSchema {
  type?: string;
  options?: AnnotationOption[];
  selectionMode?: 'single' | 'multiple';
}

export interface AnnotationEditorProps {
  /** 标注结构定义 */
  schema: AnnotationSchema | null;
  /** 当前选中的值 */
  selection: string[];
  /** 选择变化回调 */
  onChange: (selection: string[]) => void;
  /** 是否禁用 */
  disabled?: boolean;
  /** 自定义样式 */
  className?: string;
}

/**
 * 标注编辑器组件。
 *
 * 根据 schema 类型渲染对应的标注界面：
 * - classification: 分类选择（单选/多选）
 *
 * 未来扩展新标注类型时，在此组件中添加新的编辑器模式。
 */
export const AnnotationEditor: React.FC<AnnotationEditorProps> = ({
  schema,
  selection,
  onChange,
  disabled = false,
  className = '',
}) => {
  if (!schema || schema.type !== 'classification' || !Array.isArray(schema.options)) {
    return (
      <div className={`text-sm text-gray-500 ${className}`}>
        暂不支持该标注类型
      </div>
    );
  }

  const isMultiple = schema.selectionMode === 'multiple';

  function handleToggle(value: string) {
    if (disabled) return;

    if (isMultiple) {
      const newSelection = selection.includes(value)
        ? selection.filter((v) => v !== value)
        : [...selection, value];
      onChange(newSelection);
    } else {
      onChange(selection.includes(value) ? [] : [value]);
    }
  }

  return (
    <div className={`grid gap-3 ${className}`}>
      {schema.options.map((option) => {
        const checked = selection.includes(option.value);
        return (
          <label
            key={option.value}
            className={`flex min-h-12 cursor-pointer items-center gap-3 rounded border px-4 py-3 text-sm font-medium transition-colors ${
              checked
                ? 'border-blue-500 bg-blue-50 text-blue-700'
                : 'border-gray-200 text-gray-700'
            } ${disabled ? 'cursor-not-allowed opacity-50' : ''}`}
          >
            <input
              type={isMultiple ? 'checkbox' : 'radio'}
              checked={checked}
              onChange={() => handleToggle(option.value)}
              disabled={disabled}
              className="h-4 w-4"
            />
            {option.label}
          </label>
        );
      })}
    </div>
  );
};

export default AnnotationEditor;
