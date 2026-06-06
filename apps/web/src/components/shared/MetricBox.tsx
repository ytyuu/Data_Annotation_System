import type { ReactNode } from 'react';

interface MetricBoxProps {
  label: ReactNode;
  value?: ReactNode;
  children?: ReactNode;
  compact?: boolean;
  className?: string;
  valueClassName?: string;
}

export function MetricBox({
  label,
  value,
  children,
  compact = false,
  className = '',
  valueClassName = '',
}: MetricBoxProps) {
  return (
    <div className={['app-metric', compact ? 'inline-block px-2 py-1' : 'min-w-28', className].filter(Boolean).join(' ')}>
      <div className="app-metric-label">{label}</div>
      <div className={['app-metric-value', compact ? 'mt-0.5' : '', valueClassName].filter(Boolean).join(' ')}>
        {children ?? value}
      </div>
    </div>
  );
}
