import type { ButtonHTMLAttributes, MouseEvent, ReactNode } from 'react';
import { Link } from 'react-router-dom';

type ButtonVariant = 'primary' | 'secondary' | 'ghost';

interface ButtonProps
  extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> {
  children: ReactNode;
  to?: string;
  variant?: ButtonVariant;
  fullWidth?: boolean;
}

export default function Button({
  children,
  className = '',
  disabled = false,
  fullWidth = false,
  to,
  type = 'button',
  variant = 'primary',
  ...buttonProps
}: ButtonProps) {
  const classes = [
    'app-button',
    `app-button--${variant}`,
    fullWidth ? 'app-button--full' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  if (to) {
    const handleClick = (event: MouseEvent<HTMLAnchorElement>) => {
      if (disabled) event.preventDefault();
    };

    return (
      <Link
        aria-disabled={disabled}
        className={classes}
        onClick={handleClick}
        tabIndex={disabled ? -1 : undefined}
        to={to}
      >
        {children}
      </Link>
    );
  }

  return (
    <button
      {...buttonProps}
      className={classes}
      disabled={disabled}
      type={type}
    >
      {children}
    </button>
  );
}
