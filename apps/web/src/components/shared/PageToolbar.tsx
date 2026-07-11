import type { ReactNode } from 'react';

interface PageToolbarProps {
  children?: ReactNode;
  actions?: ReactNode;
  className?: string;
}

export function PageToolbar({ children, actions, className = '' }: PageToolbarProps) {
  return (
    <div className={['mb-4 flex flex-col items-stretch justify-between gap-3 sm:flex-row sm:items-center sm:gap-4', className].filter(Boolean).join(' ')}>
      <div className="min-w-0 text-sm text-gray-500">{children}</div>
      {actions && <div className="flex flex-wrap items-center gap-2 sm:shrink-0 sm:justify-end">{actions}</div>}
    </div>
  );
}
