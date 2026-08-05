import { useState } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';

import { useAuth } from '../../features/auth/AuthContext';
import { useTheme } from '../../features/theme/ThemeContext';

type HeaderProps = {
  variant?: 'default' | 'workspace';
  mobileBreakpoint?: '640' | '760';
  onLogoClick?: () => void;
};

const defaultHeaderBaseClasses =
  'sticky top-0 z-50 flex min-h-[66px] shrink-0 items-center justify-between gap-6 border-b border-[#343434] bg-[rgba(9,9,9,0.94)] px-[5vw] font-mono text-[15px] tracking-[0.04em] backdrop-blur-[12px] max-[760px]:px-5';

const mobilePaddingClasses = {
  '640': 'max-[640px]:px-5',
  '760': 'max-[760px]:px-5',
};

const workspaceHeaderClasses =
  'relative flex min-h-[66px] items-center justify-between gap-6 border-b border-[#343434] bg-[#090909] px-6 font-mono text-[15px] tracking-[0.04em] max-[760px]:px-4';

const menuItemClasses =
  'transition-colors hover:text-[#d6ff50] focus-visible:text-[#d6ff50] focus-visible:outline-none';

const getMenuLinkClasses = ({ isActive }: { isActive: boolean }) =>
  `${menuItemClasses} ${isActive ? 'text-[#d6ff50]' : 'text-[#a3a3a3]'}`;

export default function Header({
  variant = 'default',
  mobileBreakpoint = '640',
  onLogoClick,
}: HeaderProps) {
  const { loading, logout, user } = useAuth();
  const { colorMode, setColorMode } = useTheme();
  const navigate = useNavigate();
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [isAccountMenuOpen, setIsAccountMenuOpen] = useState(false);

  const headerClasses =
    variant === 'workspace'
      ? workspaceHeaderClasses
      : `${defaultHeaderBaseClasses} ${mobilePaddingClasses[mobileBreakpoint]}`;

  const closeMenus = () => {
    setIsMenuOpen(false);
    setIsAccountMenuOpen(false);
  };

  const handleLogout = async () => {
    closeMenus();
    navigate('/');
    await logout();
  };

  const toggleColorMode = () => {
    setColorMode((currentMode) =>
      currentMode === 'dark' ? 'light' : 'dark',
    );
  };

  const accountLinks = user ? (
    <>
      <NavLink className={getMenuLinkClasses} to="/my" onClick={closeMenus}>
        MY PAGE
      </NavLink>
      <button
        className={`${menuItemClasses} cursor-pointer border-0 bg-transparent p-0 font-[inherit] tracking-[inherit] text-[#a3a3a3]`}
        type="button"
        onClick={() => void handleLogout()}
      >
        LOGOUT
      </button>
    </>
  ) : (
    <>
      <NavLink className={getMenuLinkClasses} to="/login" onClick={closeMenus}>
        LOGIN
      </NavLink>
      <NavLink className={getMenuLinkClasses} to="/signup" onClick={closeMenus}>
        SIGN UP
      </NavLink>
    </>
  );

  return (
    <header className={headerClasses}>
      <Link
        className="shrink-0 text-xl leading-none font-black tracking-[-1.6px] [font-family:Arial,'Noto_Sans_KR',sans-serif]"
        to="/"
        aria-label="홈으로 이동"
        onClick={onLogoClick}
      >
        prompt<i className="not-italic text-[#d6ff50]">.</i>practice
      </Link>

      <button
        aria-controls="header-navigation"
        aria-expanded={isMenuOpen}
        aria-label={isMenuOpen ? '메뉴 닫기' : '메뉴 열기'}
        className={`${menuItemClasses} header-menu-toggle hidden cursor-pointer border-0 bg-transparent p-1 font-[inherit] text-2xl leading-none text-[#a3a3a3]`}
        onClick={() => setIsMenuOpen((isOpen) => !isOpen)}
        type="button"
      >
        {isMenuOpen ? '×' : '☰'}
      </button>

      <nav
        aria-label="주요 메뉴"
        className="header-navigation flex items-center gap-3 whitespace-nowrap max-[760px]:flex-col max-[760px]:items-stretch max-[760px]:gap-0 max-[760px]:border max-[760px]:border-[#343434] max-[760px]:bg-[#090909] max-[760px]:p-3 max-[760px]:shadow-xl"
        data-open={isMenuOpen}
        id="header-navigation"
      >
        <div className="flex items-center gap-7 max-[760px]:flex-col max-[760px]:items-stretch max-[760px]:gap-0">
          <NavLink className={(state) => `${getMenuLinkClasses(state)} max-[760px]:px-3 max-[760px]:py-3`} end to="/problems" onClick={closeMenus}>
            PROBLEM LIST
          </NavLink>
          <NavLink className={(state) => `${getMenuLinkClasses(state)} max-[760px]:px-3 max-[760px]:py-3`} to="/relay" onClick={closeMenus}>
            RELAY
          </NavLink>
          <NavLink className={(state) => `${getMenuLinkClasses(state)} max-[760px]:px-3 max-[760px]:py-3`} to="/ranking" onClick={closeMenus}>
            RANKING
          </NavLink>
        </div>

        <span className="order-3 text-[#555] max-[760px]:hidden" aria-hidden="true">|</span>

        <button
          aria-label={`헤더에서 ${colorMode === 'dark' ? '라이트' : '다크'} 모드로 전환`}
          aria-pressed={colorMode === 'light'}
          className={`${menuItemClasses} header-theme-toggle order-4 inline-flex cursor-pointer items-center gap-1.5 border-0 bg-transparent p-0 font-[inherit] tracking-[inherit] text-[#a3a3a3] max-[760px]:w-full max-[760px]:px-3 max-[760px]:py-3`}
          onClick={toggleColorMode}
          type="button"
        >
          {colorMode === 'dark' ? (
            <svg aria-hidden="true" className="size-4 shrink-0" fill="none" viewBox="0 0 18 18">
              <circle cx="9" cy="9" r="3.2" stroke="currentColor" strokeWidth="1.8" />
              <path d="M9 1.5v2M9 14.5v2M1.5 9h2M14.5 9h2M3.7 3.7l1.4 1.4M12.9 12.9l1.4 1.4M14.3 3.7l-1.4 1.4M5.1 12.9l-1.4 1.4" stroke="currentColor" strokeLinecap="round" strokeWidth="1.6" />
            </svg>
          ) : (
            <svg aria-hidden="true" className="size-4 shrink-0" fill="none" viewBox="0 0 18 18">
              <path d="M14.8 11.2A6.2 6.2 0 0 1 6.8 3.2a6.2 6.2 0 1 0 8 8Z" stroke="currentColor" strokeLinejoin="round" strokeWidth="1.8" />
            </svg>
          )}
          <b className="min-[761px]:sr-only">
            {colorMode === 'dark' ? 'LIGHT' : 'DARK'}
          </b>
        </button>

        {!loading && (
          <>
            <span className="order-1 text-[#555] max-[760px]:hidden" aria-hidden="true">|</span>

            <div className="header-account relative order-2 flex items-center max-[760px]:mt-1 max-[760px]:w-full max-[760px]:items-stretch max-[760px]:border-t max-[760px]:border-[#343434] max-[760px]:pt-1">
              <button
                aria-controls="header-account-menu"
                aria-expanded={isAccountMenuOpen}
                aria-label={user ? '계정 메뉴' : '로그인 메뉴'}
                className={`${menuItemClasses} header-account-toggle inline-flex cursor-pointer items-center border-0 bg-transparent p-0 font-[inherit] leading-none tracking-[inherit] text-[#a3a3a3]`}
                onClick={() => setIsAccountMenuOpen((isOpen) => !isOpen)}
                type="button"
              >
                {user ? (
                  <svg aria-hidden="true" className="size-5" fill="none" viewBox="0 0 20 20">
                    <circle cx="10" cy="6.5" r="3" stroke="currentColor" strokeWidth="1.7" />
                    <path d="M4.5 16c.5-3 2.5-4.5 5.5-4.5s5 1.5 5.5 4.5" stroke="currentColor" strokeLinecap="round" strokeWidth="1.7" />
                  </svg>
                ) : (
                  'LOGIN'
                )}
              </button>

              <div
                className="header-account-menu absolute top-[calc(100%+14px)] right-0 z-30 hidden min-w-[128px] flex-col gap-3 border border-[#343434] bg-[#090909] p-4 shadow-xl"
                data-open={isAccountMenuOpen}
                id="header-account-menu"
              >
                <span className="header-account-menu-label text-[10px] tracking-[0.16em] text-[#777]">
                  ACCOUNT
                </span>
                {accountLinks}
              </div>

              <div className="header-mobile-account hidden flex-col items-stretch gap-0">
                {accountLinks}
              </div>
            </div>
          </>
        )}
      </nav>
    </header>
  );
}
