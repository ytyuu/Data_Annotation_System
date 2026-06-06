import type { ReactNode } from 'react';

interface ListCardProps {
  title: ReactNode;
  subtitle?: ReactNode;
  meta?: ReactNode;
  badges?: ReactNode;
  metrics?: ReactNode;
  actions?: ReactNode;
  className?: string;
}

export function ListCard({
  title,
  subtitle,
  meta,
  badges,
  metrics,
  actions,
  className = '',
}: ListCardProps) {
  const layoutClass = metrics && actions
    ? 'lg:grid-cols-[minmax(220px,1fr)_minmax(280px,auto)_auto]'
    : actions
      ? 'lg:grid-cols-[minmax(220px,1fr)_auto]'
      : metrics
        ? 'lg:grid-cols-[minmax(220px,1fr)_minmax(280px,auto)]'
        : '';

  return (
    <article
      className={[
        'rounded border border-gray-200 bg-white shadow-sm transition-colors hover:border-gray-300 hover:bg-gray-50/70',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <div className={['grid gap-4 px-4 py-4 lg:items-center', layoutClass].filter(Boolean).join(' ')}>
        <div className="min-w-0">
          <div className="truncate text-sm font-semibold text-gray-900">{title}</div>
          {subtitle && <div className="mt-1 line-clamp-1 text-xs text-gray-500">{subtitle}</div>}
          {(meta || badges) && (
            <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-gray-500">
              {meta}
              {badges}
            </div>
          )}
        </div>

        {metrics && <div className="min-w-0">{metrics}</div>}

        {actions && (
          <div className="flex shrink-0 flex-wrap items-center justify-start gap-2 text-xs text-gray-500 lg:justify-end">
            {actions}
          </div>
        )}
      </div>
    </article>
  );
}
