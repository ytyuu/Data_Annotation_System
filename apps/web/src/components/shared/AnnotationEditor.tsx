import React from 'react';

export interface AnnotationSubOption {
  label: string;
  value: string;
}

export interface AnnotationOption {
  label: string;
  value: string;
  hasSubOptions?: boolean;
  subSelectionMode?: 'single' | 'multiple';
  subOptions?: AnnotationSubOption[];
}

export interface AnnotationSchema {
  type?: string;
  options?: AnnotationOption[];
  selectionMode?: 'single' | 'multiple';
}

export interface AnnotationSelection {
  main: string[];
  sub: Record<string, string[]>;
}

export interface AnnotationEditorProps {
  /** 标注结构定义 */
  schema: AnnotationSchema | null;
  /** 当前选中的值 */
  selection: AnnotationSelection;
  /** 选择变化回调 */
  onChange: (selection: AnnotationSelection) => void;
  /** 是否禁用 */
  disabled?: boolean;
  /** 自定义样式 */
  className?: string;
}

/**
 * 标注编辑器组件。
 *
 * 根据 schema 类型渲染对应的标注界面：
 * - classification: 分类选择（单选/多选），支持二级子选项
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

  function handleToggleMain(value: string) {
    if (disabled) return;

    let newMain: string[];
    if (isMultiple) {
      newMain = selection.main.includes(value)
        ? selection.main.filter((v) => v !== value)
        : [...selection.main, value];
    } else {
      newMain = selection.main.includes(value) ? [] : [value];
    }

    // 清除未选中的主选项对应的子选项
    const newSub: Record<string, string[]> = {};
    newMain.forEach((mainValue) => {
      if (selection.sub[mainValue]) {
        newSub[mainValue] = selection.sub[mainValue];
      }
    });

    onChange({ main: newMain, sub: newSub });
  }

  function handleToggleSub(mainValue: string, subValue: string) {
    if (disabled || !schema) return;

    const mainOption = schema.options?.find((o) => o.value === mainValue);
    if (!mainOption?.hasSubOptions) return;

    const isSubMultiple = mainOption.subSelectionMode === 'multiple';
    const currentSub = selection.sub[mainValue] || [];
    let newSubValues: string[];

    if (isSubMultiple) {
      newSubValues = currentSub.includes(subValue)
        ? currentSub.filter((v) => v !== subValue)
        : [...currentSub, subValue];
    } else {
      newSubValues = currentSub.includes(subValue) ? [] : [subValue];
    }

    onChange({
      ...selection,
      sub: {
        ...selection.sub,
        [mainValue]: newSubValues,
      },
    });
  }

  return (
    <div className={`grid gap-3 ${className}`}>
      {schema.options.map((option) => {
        const mainChecked = selection.main.includes(option.value);
        const hasSubs = option.hasSubOptions && Array.isArray(option.subOptions) && option.subOptions.length > 0;
        const subSelection = selection.sub[option.value] || [];

        return (
          <div key={option.value}>
            <label
              className={`flex min-h-12 cursor-pointer items-center gap-3 rounded border px-4 py-3 text-sm font-medium transition-colors ${
                mainChecked
                  ? 'border-blue-500 bg-blue-50 text-blue-700'
                  : 'border-gray-200 text-gray-700'
              } ${disabled ? 'cursor-not-allowed opacity-50' : ''}`}
            >
              <input
                type={isMultiple ? 'checkbox' : 'radio'}
                checked={mainChecked}
                onChange={() => handleToggleMain(option.value)}
                disabled={disabled}
                className="h-4 w-4"
              />
              {option.label}
            </label>

            {mainChecked && hasSubs && (
              <div className="mt-2 ml-6 rounded border-l-2 border-blue-200 bg-blue-50/50 py-2 pl-4 pr-2">
                <div className="mb-2 text-xs font-medium text-blue-600">
                  {option.subSelectionMode === 'multiple' ? '请选择（可多选）' : '请选择'}
                </div>
                <div className="grid gap-2">
                  {option.subOptions!.map((subOption) => {
                    const subChecked = subSelection.includes(subOption.value);
                    return (
                      <label
                        key={subOption.value}
                        className={`flex min-h-10 cursor-pointer items-center gap-2 rounded border px-3 py-2 text-sm transition-colors ${
                          subChecked
                            ? 'border-blue-400 bg-blue-100 text-blue-700'
                            : 'border-gray-200 bg-white text-gray-700'
                        } ${disabled ? 'cursor-not-allowed opacity-50' : ''}`}
                      >
                        <input
                          type={option.subSelectionMode === 'multiple' ? 'checkbox' : 'radio'}
                          checked={subChecked}
                          onChange={() => handleToggleSub(option.value, subOption.value)}
                          disabled={disabled}
                          className="h-4 w-4"
                        />
                        {subOption.label}
                      </label>
                    );
                  })}
                </div>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
};

export default AnnotationEditor;
