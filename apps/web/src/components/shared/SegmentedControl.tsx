import { AppButton } from './AppButton';

export interface SegmentedControlOption<T extends string> {
  value: T;
  label: string;
  description?: string;
  disabled?: boolean;
}

interface SegmentedControlProps<T extends string> {
  value: T;
  options: SegmentedControlOption<T>[];
  onChange: (value: T) => void;
  size?: 'sm' | 'md' | 'lg';
  fullWidth?: boolean;
  className?: string;
}

export function SegmentedControl<T extends string>({
  value,
  options,
  onChange,
  size = 'md',
  fullWidth = false,
  className = '',
}: SegmentedControlProps<T>) {
  const hasDescriptions = options.some((option) => option.description);
  const containerClasses = [
    'inline-flex items-stretch gap-1 rounded border border-gray-300 bg-gray-100 p-1',
    fullWidth ? 'w-full' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  const buttonPadding = hasDescriptions
    ? 'px-4 py-3'
    : size === 'sm'
      ? 'px-3 py-1.5'
      : size === 'lg'
        ? 'px-4 py-2'
        : 'px-3 py-1.5';

  return (
    <div className={containerClasses}>
      {options.map((option) => {
        const selected = option.value === value;
        return (
          <AppButton
            key={option.value}
            type="button"
            variant="custom"
            disabled={option.disabled}
            aria-pressed={selected}
            className={[
              'min-w-0 rounded text-sm font-medium transition-colors',
              fullWidth || hasDescriptions ? 'flex-1' : '',
              buttonPadding,
              selected
                ? 'bg-white text-gray-900 shadow-sm ring-1 ring-gray-300'
                : 'text-gray-500 hover:bg-white/70 hover:text-gray-800',
              option.disabled ? 'cursor-not-allowed opacity-50 hover:bg-transparent hover:text-gray-500' : '',
            ]
              .filter(Boolean)
              .join(' ')}
            onClick={() => onChange(option.value)}
          >
            {option.description ? (
              <span className="block text-center">
                <span className="block">{option.label}</span>
                <span className={`mt-1 block text-xs ${selected ? 'text-gray-500' : 'text-gray-400'}`}>
                  {option.description}
                </span>
              </span>
            ) : (
              option.label
            )}
          </AppButton>
        );
      })}
    </div>
  );
}
