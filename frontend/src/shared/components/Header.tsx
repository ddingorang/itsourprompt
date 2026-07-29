import { Link, NavLink } from 'react-router-dom';

type HeaderProps = {
  variant?: 'default' | 'workspace';
  mobileBreakpoint?: '640' | '760';
  mode?: 'guest' | 'authenticated';
  onLogout?: () => void;
};

const defaultHeaderBaseClasses =
  'sticky top-0 z-10 flex min-h-[66px] items-center justify-between gap-6 border-b border-[#343434] bg-[rgba(9,9,9,0.94)] px-[5vw] font-mono text-[15px] tracking-[0.04em] backdrop-blur-[12px] max-[480px]:flex-col max-[480px]:items-start max-[480px]:gap-3 max-[480px]:py-4';

const mobilePaddingClasses = {
  '640': 'max-[640px]:px-5',
  '760': 'max-[760px]:px-5',
};

const workspaceHeaderClasses =
  'flex min-h-[66px] items-center justify-between gap-6 border-b border-[#343434] bg-[#090909] px-6 font-mono text-[15px] tracking-[0.04em] max-[700px]:px-4 max-[480px]:flex-col max-[480px]:items-start max-[480px]:gap-3 max-[480px]:py-4';

const menuItemClasses =
  'transition-colors hover:text-[#d6ff50] focus-visible:text-[#d6ff50] focus-visible:outline-none';

const getMenuLinkClasses = ({ isActive }: { isActive: boolean }) =>
  `${menuItemClasses} ${isActive ? 'text-[#d6ff50]' : 'text-[#a3a3a3]'}`;

export default function Header({
  variant = 'default',
  mobileBreakpoint = '640',
  mode = 'guest',
  onLogout,
}: HeaderProps) {
  const headerClasses =
    variant === 'workspace'
      ? workspaceHeaderClasses
      : `${defaultHeaderBaseClasses} ${mobilePaddingClasses[mobileBreakpoint]}`;

  return (
    <header className={headerClasses}>
      <Link
        className="text-xl leading-none font-black tracking-[-1.6px] [font-family:Arial,'Noto_Sans_KR',sans-serif]"
        to="/"
        aria-label="홈으로 이동"
      >
        prompt<i className="not-italic text-[#d6ff50]">.</i>practice
      </Link>
      <nav
        className="flex items-center gap-3 whitespace-nowrap max-[480px]:w-full max-[480px]:justify-between max-[480px]:gap-2 max-[480px]:text-xs"
        aria-label="주요 메뉴"
      >
        <NavLink className={getMenuLinkClasses} to="/problems">
          PROBLEM LIST
        </NavLink>
        <span className="text-[#555]" aria-hidden="true">
          |
        </span>
        {mode === 'guest' ? (
          <>
            <NavLink className={getMenuLinkClasses} to="/login">
              LOGIN
            </NavLink>
            <span className="text-[#555]" aria-hidden="true">
              |
            </span>
            <NavLink className={getMenuLinkClasses} to="/signup">
              SIGN UP
            </NavLink>
          </>
        ) : (
          <>
            <NavLink className={getMenuLinkClasses} to="/my">
              PROFILE
            </NavLink>
            <span className="text-[#555]" aria-hidden="true">
              |
            </span>
            <button
              className={`${menuItemClasses} cursor-pointer border-0 bg-transparent p-0 font-[inherit] tracking-[inherit] text-[#a3a3a3]`}
              type="button"
              onClick={onLogout}
            >
              LOGOUT
            </button>
          </>
        )}
      </nav>
    </header>
  );
}
