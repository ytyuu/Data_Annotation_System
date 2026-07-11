import type { MouseEvent, ReactNode } from 'react';

type AppModalWidth = 'sm' | 'md' | 'lg' | 'xl' | '2xl';

interface AppModalProps {
  title?: ReactNode;
  subtitle?: ReactNode;
  actions?: ReactNode;
  footer?: ReactNode;
  children?: ReactNode;
  onClose?: () => void;
  closeOnOverlayClick?: boolean;
  width?: AppModalWidth;
  fullHeight?: boolean;
  className?: string;
  overlayClassName?: string;
  headerClassName?: string;
  titleClassName?: string;
  contentClassName?: string;
  footerClassName?: string;
}

const widthClasses: Record<AppModalWidth, string> = {
  sm: 'max-w-sm',
  md: 'max-w-md',
  lg: 'max-w-2xl',
  xl: 'max-w-3xl',
  '2xl': 'max-w-5xl',
};

export function AppModal({
  title,
  subtitle,
  actions,
  footer,
  children,
  onClose,
  closeOnOverlayClick = false,
  width = 'lg',
  fullHeight = false,
  className = '',
  overlayClassName = '',
  headerClassName = '',
  titleClassName = '',
  contentClassName = '',
  footerClassName = '',
}: AppModalProps) {
  function handleOverlayMouseDown(event: MouseEvent<HTMLDivElement>) {
    if (closeOnOverlayClick && event.target === event.currentTarget) {
      onClose?.();
    }
  }

  const hasHeader = title || subtitle || actions;

  return (
    <div
      className={[
        'fixed inset-0 z-50 flex items-center justify-center bg-gray-900/45 px-6 py-8',
        overlayClassName,
      ]
        .filter(Boolean)
        .join(' ')}
      onMouseDown={handleOverlayMouseDown}
    >
      <div
        className={[
          'w-full overflow-hidden rounded border border-gray-200 bg-white shadow-2xl',
          widthClasses[width],
          fullHeight ? 'flex h-full flex-col' : '',
          className,
        ]
          .filter(Boolean)
          .join(' ')}
      >
        {hasHeader && (
          <div
            className={[
              'flex items-start justify-between gap-4 border-b border-gray-200 px-6 py-5',
              headerClassName,
            ]
              .filter(Boolean)
              .join(' ')}
          >
            <div className="min-w-0">
              {title && (
                <div className={['text-base font-semibold text-gray-900', titleClassName].filter(Boolean).join(' ')}>
                  {title}
                </div>
              )}
              {subtitle && <div className="mt-1 text-sm text-gray-500">{subtitle}</div>}
            </div>
            {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
          </div>
        )}

        {children !== undefined && children !== null && (
          <div className={contentClassName || (fullHeight ? 'flex-1 overflow-hidden' : 'px-6 py-5')}>
            {children}
          </div>
        )}

        {footer && (
          <div
            className={[
              'flex flex-wrap items-center justify-end gap-3 border-t border-gray-200 bg-gray-50 px-4 py-4 sm:px-6',
              footerClassName,
            ]
              .filter(Boolean)
              .join(' ')}
          >
            {footer}
          </div>
        )}
      </div>
    </div>
  );
}
