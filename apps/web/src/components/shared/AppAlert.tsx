import type { ReactNode } from 'react';

type AppAlertKind = 'error' | 'success' | 'info' | 'warning';

interface AppAlertProps {
  kind?: AppAlertKind;
  title?: ReactNode;
  children: ReactNode;
  className?: string;
}

const kindClasses: Record<AppAlertKind, string> = {
  error: 'border-red-200 bg-red-50 text-red-700',
  success: 'border-green-200 bg-green-50 text-green-700',
  info: 'border-blue-100 bg-blue-50 text-blue-900',
  warning: 'border-amber-200 bg-amber-50 text-amber-800',
};

const titleClasses: Record<AppAlertKind, string> = {
  error: 'text-red-800',
  success: 'text-green-800',
  info: 'text-blue-950',
  warning: 'text-amber-900',
};

export function AppAlert({ kind = 'info', title, children, className = '' }: AppAlertProps) {
  return (
    <div
      className={[
        'rounded border px-4 py-3 text-sm',
        kindClasses[kind],
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {title && (
        <div className={['font-medium', titleClasses[kind]].join(' ')}>
          {title}
        </div>
      )}
      <div className={title ? 'mt-2 whitespace-pre-wrap' : 'whitespace-pre-wrap'}>{children}</div>
    </div>
  );
}
