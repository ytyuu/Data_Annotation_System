import type { ButtonHTMLAttributes, ReactNode } from 'react';

type AppButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost' | 'custom';
type AppButtonSize = 'sm' | 'md';

interface AppButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: AppButtonVariant;
  size?: AppButtonSize;
  fullWidth?: boolean;
  children: ReactNode;
}

export function AppButton({
  variant = 'secondary',
  size = 'md',
  fullWidth = false,
  className = '',
  type = 'button',
  children,
  ...props
}: AppButtonProps) {
  const classes = [
    variant === 'custom' ? '' : 'app-button',
    variant === 'custom' ? '' : `app-button-variant-${variant}`,
    variant === 'custom' ? '' : `app-button-${size}`,
    fullWidth ? 'w-full' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <button type={type} className={classes} {...props}>
      {children}
    </button>
  );
}
