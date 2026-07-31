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
const baseClasses =
  'inline-flex min-h-11 cursor-pointer items-center justify-center gap-2.5 ' +
  "border px-[18px] text-xs leading-none font-extrabold tracking-[-0.01em] [font-family:Arial,'Noto_Sans_KR',sans-serif] " +
  'no-underline transition-[background,border-color,color,opacity] duration-200 ' +
  'disabled:cursor-not-allowed disabled:opacity-45'

const variantClasses: Record<ButtonVariant, string> = {
  primary:
    'border-[#d6ff50] bg-[#d6ff50] text-[#090909] ' +
    'hover:bg-transparent hover:text-[#d6ff50] ' +
    'focus-visible:bg-transparent focus-visible:text-[#d6ff50] focus-visible:outline-none',

  secondary:
    'border-[#4b4b4b] bg-transparent text-[#f5f5ef] ' +
    'hover:border-[#d6ff50] hover:bg-[#d6ff50] hover:text-[#090909] ' +
    'focus-visible:border-[#d6ff50] focus-visible:bg-[#d6ff50] ' +
    'focus-visible:text-[#090909] focus-visible:outline-none',

  ghost:
    'border-[#535353] bg-transparent text-[#f5f5ef] ' +
    'hover:border-[#d6ff50] hover:bg-[#d6ff50] hover:text-[#090909] ' +
    'focus-visible:border-[#d6ff50] focus-visible:bg-[#d6ff50] ' +
    'focus-visible:text-[#090909] focus-visible:outline-none',
}

const classes = [
  baseClasses,
  variantClasses[variant],
  fullWidth ? 'w-full' : '',
  className,
]
  .filter(Boolean)
  .join(' ')

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
