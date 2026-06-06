import type { ReactNode } from 'react';

interface EmptyStateProps {
  children: ReactNode;
  align?: 'left' | 'center';
  spacious?: boolean;
  className?: string;
}

export function EmptyState({ children, align = 'left', spacious = false, className = '' }: EmptyStateProps) {
  return (
    <div
      className={[
        'rounded border border-gray-200 bg-gray-50 text-sm text-gray-500',
        align === 'center' ? 'text-center' : '',
        spacious ? 'p-8' : 'p-6',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {children}
    </div>
  );
}
