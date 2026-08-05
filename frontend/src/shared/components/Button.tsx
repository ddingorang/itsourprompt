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
  "border px-[18px] text-[14px] leading-none font-extrabold tracking-[-0.01em] [font-family:Arial,'Noto_Sans_KR',sans-serif] " +
  'no-underline transition-[background,border-color,color,opacity] duration-200 ' +
  'disabled:cursor-not-allowed disabled:opacity-45'

// 강조색은 var(--acid)로 읽되 폴백을 둔다. --acid는 .landing-page 안에서만
// 정의되므로, 랜딩에서는 라이트 모드 강조색(#bee034)을 자동으로 따라가고
// 그 바깥 화면에서는 폴백 #d6ff50 그대로다.
//
// 클래스 이름을 변수로 조립하지 말 것 — Tailwind는 소스에서 문자열을 통째로
// 찾으므로 쪼개는 순간 해당 유틸리티가 생성되지 않는다.
const variantClasses: Record<ButtonVariant, string> = {
  primary:
    'border-[var(--acid,#d6ff50)] bg-[var(--acid,#d6ff50)] text-[#090909] ' +
    'hover:bg-transparent hover:text-[var(--acid,#d6ff50)] ' +
    'focus-visible:bg-transparent focus-visible:text-[var(--acid,#d6ff50)] focus-visible:outline-none',

  secondary:
    'border-[#4b4b4b] bg-transparent text-[#f5f5ef] ' +
    'hover:border-[var(--acid,#d6ff50)] hover:bg-[var(--acid,#d6ff50)] hover:text-[#090909] ' +
    'focus-visible:border-[var(--acid,#d6ff50)] focus-visible:bg-[var(--acid,#d6ff50)] ' +
    'focus-visible:text-[#090909] focus-visible:outline-none',

  ghost:
    'border-[#535353] bg-transparent text-[#f5f5ef] ' +
    'hover:border-[var(--acid,#d6ff50)] hover:bg-[var(--acid,#d6ff50)] hover:text-[#090909] ' +
    'focus-visible:border-[var(--acid,#d6ff50)] focus-visible:bg-[var(--acid,#d6ff50)] ' +
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
        data-variant={variant}
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
      data-variant={variant}
      disabled={disabled}
      type={type}
    >
      {children}
    </button>
  );
}
