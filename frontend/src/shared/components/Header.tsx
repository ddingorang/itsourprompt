import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';

type HeaderProps = {
  children?: ReactNode;
  variant?: 'default' | 'workspace';
  mobileBreakpoint?: '640' | '760';
};

const defaultHeaderClasses = {
  '640':
    'sticky top-0 z-10 flex min-h-[66px] items-center justify-between border-b border-[#343434] bg-[rgba(9,9,9,0.94)] px-[5vw] font-mono text-[15px] tracking-[0.04em] backdrop-blur-[12px] max-[640px]:px-5',
  '760':
    'sticky top-0 z-10 flex min-h-[66px] items-center justify-between border-b border-[#343434] bg-[rgba(9,9,9,0.94)] px-[5vw] font-mono text-[15px] tracking-[0.04em] backdrop-blur-[12px] max-[760px]:px-5',
};

const workspaceHeaderClasses =
  'grid min-h-[66px] grid-cols-[auto_minmax(0,1fr)] items-center gap-6 border-b border-[#343434] bg-[#090909] px-6 font-mono text-[15px] max-[700px]:px-4';

export default function Header({
  children,
  variant = 'default',
  mobileBreakpoint = '640',
}: HeaderProps) {
  const headerClasses =
    variant === 'workspace'
      ? workspaceHeaderClasses
      : defaultHeaderClasses[mobileBreakpoint];

  return (
    <header className={headerClasses}>
      <Link
        className="text-xl leading-none font-black tracking-[-1.6px] [font-family:Arial,'Noto_Sans_KR',sans-serif]"
        to="/"
        aria-label="홈으로 이동"
      >
        prompt<i className="not-italic text-[#d6ff50]">.</i>practice
      </Link>
      {children}
    </header>
  );
}
