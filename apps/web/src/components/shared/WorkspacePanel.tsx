import type { ReactNode } from 'react';

interface WorkspacePanelProps {
  title: ReactNode;
  eyebrow?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  contentClassName?: string;
}

export function WorkspacePanel({
  title,
  eyebrow,
  actions,
  children,
  className = '',
  contentClassName = '',
}: WorkspacePanelProps) {
  return (
    <section
      className={[
        'min-h-0 overflow-hidden rounded border border-gray-200 bg-white shadow-sm',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <div className="flex items-start justify-between gap-4 border-b border-gray-200 bg-gray-50 px-4 py-3">
        <div className="min-w-0">
          {eyebrow && <div className="mb-1 text-xs font-medium text-gray-500">{eyebrow}</div>}
          <div className="truncate text-sm font-semibold text-gray-900">{title}</div>
        </div>
        {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
      </div>
      <div className={['min-h-0 p-4', contentClassName].filter(Boolean).join(' ')}>
        {children}
      </div>
    </section>
  );
}
