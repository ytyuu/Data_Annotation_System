import type { ReactNode } from 'react';

type StatusBadgeTone = 'default' | 'info' | 'success' | 'warning' | 'danger' | 'muted';

interface StatusBadgeProps {
  status?: string | number | null;
  children: ReactNode;
  tone?: StatusBadgeTone;
  className?: string;
}

const toneClasses: Record<StatusBadgeTone, string> = {
  default: 'bg-gray-100 text-gray-700 ring-gray-200',
  info: 'bg-blue-50 text-blue-700 ring-blue-200',
  success: 'bg-green-50 text-green-700 ring-green-200',
  warning: 'bg-amber-50 text-amber-700 ring-amber-200',
  danger: 'bg-red-50 text-red-700 ring-red-200',
  muted: 'bg-gray-100 text-gray-600 ring-gray-200',
};

export function StatusBadge({ status, children, tone, className = '' }: StatusBadgeProps) {
  const classes = [
    'app-badge',
    tone ? toneClasses[tone] : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <span
      className={classes}
      data-kind={tone ? undefined : 'status'}
      data-status={tone || status === undefined || status === null ? undefined : String(status)}
    >
      {children}
    </span>
  );
}
