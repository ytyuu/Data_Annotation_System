import { AppButton } from './AppButton';
import { SegmentedControl } from './SegmentedControl';

type SelectionMode = 'single' | 'multiple';

export interface OptionRuleSubOption {
  id: string;
  label: string;
}

export interface OptionRuleOption {
  id: string;
  label: string;
  hasSubOptions: boolean;
  subSelectionMode: SelectionMode;
  subOptions: OptionRuleSubOption[];
}

interface OptionRuleEditorProps {
  options: OptionRuleOption[];
  onAddOption: () => void;
  onRemoveOption: (id: string) => void;
  onChangeOptionLabel: (id: string, label: string) => void;
  onToggleSubOptions: (id: string) => void;
  onChangeSubSelectionMode: (id: string, mode: SelectionMode) => void;
  onAddSubOption: (optionId: string) => void;
  onRemoveSubOption: (optionId: string, subOptionId: string) => void;
  onChangeSubOptionLabel: (optionId: string, subOptionId: string, label: string) => void;
}

const selectionModeOptions = [
  { value: 'single', label: '单选' },
  { value: 'multiple', label: '多选' },
] satisfies Array<{ value: SelectionMode; label: string }>;

export function OptionRuleEditor({
  options,
  onAddOption,
  onRemoveOption,
  onChangeOptionLabel,
  onToggleSubOptions,
  onChangeSubSelectionMode,
  onAddSubOption,
  onRemoveSubOption,
  onChangeSubOptionLabel,
}: OptionRuleEditorProps) {
  return (
    <div className="app-field">
      <div className="mb-2 flex items-center justify-between gap-3">
        <label className="app-label mb-0">标注选项</label>
        <AppButton type="button" variant="secondary" size="sm" onClick={onAddOption}>
          新增选项
        </AppButton>
      </div>

      <div className="space-y-3">
        {options.map((option, index) => (
          <div key={option.id} className="rounded border border-gray-200 bg-white shadow-sm">
            <div className="grid gap-3 p-3 md:grid-cols-[auto_minmax(0,1fr)_auto] md:items-center">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded border border-gray-200 bg-gray-50 text-xs font-semibold text-gray-500">
                {index + 1}
              </div>

              <input
                type="text"
                value={option.label}
                onChange={(event) => onChangeOptionLabel(option.id, event.target.value)}
                className="app-input"
                placeholder="请输入选项名称"
              />

              <div className="flex flex-wrap items-center gap-2">
                <AppButton
                  type="button"
                  variant="custom"
                  className={[
                    'h-10 rounded border px-3 text-xs font-medium transition-colors',
                    option.hasSubOptions
                      ? 'border-gray-900 bg-gray-900 text-white hover:bg-gray-800'
                      : 'border-gray-300 bg-white text-gray-600 hover:bg-gray-100',
                  ].join(' ')}
                  onClick={() => onToggleSubOptions(option.id)}
                >
                  {option.hasSubOptions ? '已开启子选项' : '开启子选项'}
                </AppButton>
                <AppButton
                  type="button"
                  variant="secondary"
                  size="sm"
                  disabled={options.length <= 2}
                  onClick={() => onRemoveOption(option.id)}
                >
                  删除
                </AppButton>
              </div>
            </div>

            {option.hasSubOptions && (
              <div className="border-t border-gray-200 bg-gray-50 px-3 py-3">
                <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <div className="text-xs font-semibold text-gray-700">子选项配置</div>
                    <div className="mt-1 text-xs text-gray-500">为当前选项继续细分可选标签</div>
                  </div>
                  <SegmentedControl
                    value={option.subSelectionMode}
                    options={selectionModeOptions}
                    onChange={(value) => onChangeSubSelectionMode(option.id, value)}
                    size="sm"
                  />
                </div>

                <div className="space-y-2">
                  {option.subOptions.map((subOption, subIndex) => (
                    <div key={subOption.id} className="grid gap-2 md:grid-cols-[auto_minmax(0,1fr)_auto] md:items-center">
                      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded border border-gray-200 bg-white text-xs font-medium text-gray-500">
                        {subIndex + 1}
                      </div>
                      <input
                        type="text"
                        value={subOption.label}
                        onChange={(event) => onChangeSubOptionLabel(option.id, subOption.id, event.target.value)}
                        className="app-input py-1.5 text-sm"
                        placeholder="子选项名称"
                      />
                      <AppButton
                        type="button"
                        variant="secondary"
                        size="sm"
                        disabled={option.subOptions.length <= 2}
                        onClick={() => onRemoveSubOption(option.id, subOption.id)}
                      >
                        删除
                      </AppButton>
                    </div>
                  ))}
                </div>

                <AppButton
                  type="button"
                  variant="secondary"
                  size="sm"
                  className="mt-3"
                  onClick={() => onAddSubOption(option.id)}
                >
                  新增子选项
                </AppButton>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
