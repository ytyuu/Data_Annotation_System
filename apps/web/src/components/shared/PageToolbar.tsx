import type { ReactNode } from 'react';

interface PageToolbarProps {
  children?: ReactNode;
  actions?: ReactNode;
  className?: string;
}

export function PageToolbar({ children, actions, className = '' }: PageToolbarProps) {
  return (
    <div className={['mb-4 flex items-center justify-between gap-4', className].filter(Boolean).join(' ')}>
      <div className="min-w-0 text-sm text-gray-500">{children}</div>
      {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
    </div>
  );
}
