import type { ReactNode } from 'react';

interface StatsPanelProps {
  title: ReactNode;
  subtitle?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  className?: string;
  contentClassName?: string;
}

export function StatsPanel({
  title,
  subtitle,
  children,
  footer,
  className = '',
  contentClassName = '',
}: StatsPanelProps) {
  return (
    <section
      className={[
        'overflow-hidden rounded border border-gray-200 bg-white shadow-sm',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <div className="border-b border-gray-200 bg-gray-50 px-4 py-3">
        <div className="text-sm font-semibold text-gray-900">{title}</div>
        {subtitle && <div className="mt-1 text-xs text-gray-500">{subtitle}</div>}
      </div>
      <div className={['p-5', contentClassName].filter(Boolean).join(' ')}>{children}</div>
      {footer && (
        <div className="border-t border-gray-100 bg-gray-50 px-4 py-3 text-sm text-gray-500">
          {footer}
        </div>
      )}
    </section>
  );
}
