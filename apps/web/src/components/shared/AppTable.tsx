import type { ReactNode } from 'react';

interface AppTableProps {
  children: ReactNode;
  className?: string;
}

interface AppTableRowProps {
  children: ReactNode;
  className?: string;
}

export function AppTable({ children, className = '' }: AppTableProps) {
  return (
    <div className={['overflow-hidden rounded border border-gray-200', className].filter(Boolean).join(' ')}>
      <table className="w-full text-left text-sm">{children}</table>
    </div>
  );
}

export function AppTableHead({ children, className = '' }: AppTableProps) {
  return (
    <thead className={['border-b border-gray-200 bg-gray-50 text-xs font-medium text-gray-500', className].filter(Boolean).join(' ')}>
      {children}
    </thead>
  );
}

export function AppTableBody({ children, className = '' }: AppTableProps) {
  return (
    <tbody className={['divide-y divide-gray-200 bg-white', className].filter(Boolean).join(' ')}>
      {children}
    </tbody>
  );
}

export function AppTableRow({ children, className = '' }: AppTableRowProps) {
  return (
    <tr className={['align-middle hover:bg-gray-50', className].filter(Boolean).join(' ')}>
      {children}
    </tr>
  );
}
