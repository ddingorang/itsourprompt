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
  'sticky top-0 z-50 flex min-h-[66px] shrink-0 items-center justify-between gap-6 border-b border-[var(--theme-border,#343434)] bg-[color-mix(in_srgb,var(--theme-bg,#090909)_94%,transparent)] px-[5vw] text-[15px] backdrop-blur-[12px] max-[760px]:px-5';

const mobilePaddingClasses = {
  '640': 'max-[640px]:px-5',
  '760': 'max-[760px]:px-5',
};

const workspaceHeaderClasses =
  'relative flex min-h-[66px] items-center justify-between gap-6 border-b border-[var(--theme-border,#343434)] bg-[var(--theme-bg,#090909)] px-6 text-[15px] max-[760px]:px-4';

const menuItemClasses =
  'transition-colors hover:text-[var(--acid,#d6ff50)] focus-visible:text-[var(--acid,#d6ff50)] focus-visible:outline-none';

const getMenuLinkClasses = ({ isActive }: { isActive: boolean }) =>
  `${menuItemClasses} ${isActive ? 'text-[var(--acid,#d6ff50)]' : 'text-[var(--theme-muted,#a3a3a3)]'}`;

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
        마이페이지
      </NavLink>
      <button
        className={`${menuItemClasses} cursor-pointer border-0 bg-transparent p-0 font-[inherit] tracking-[inherit] text-[var(--theme-muted,#a3a3a3)]`}
        type="button"
        onClick={() => void handleLogout()}
      >
        로그아웃
      </button>
    </>
  ) : (
    <>
      <NavLink className={getMenuLinkClasses} to="/login" onClick={closeMenus}>
        로그인
      </NavLink>
      <NavLink className={getMenuLinkClasses} to="/signup" onClick={closeMenus}>
        회원가입
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
        모두의 <i className="not-italic text-[var(--acid,#d6ff50)]">프롬프트</i>
      </Link>

      <button
        aria-controls="header-navigation"
        aria-expanded={isMenuOpen}
        aria-label={isMenuOpen ? '메뉴 닫기' : '메뉴 열기'}
        className={`${menuItemClasses} header-menu-toggle hidden cursor-pointer border-0 bg-transparent p-1 font-[inherit] text-2xl leading-none text-[var(--header-menu-muted,#a3a3a3)]`}
        onClick={() => setIsMenuOpen((isOpen) => !isOpen)}
        type="button"
      >
        {isMenuOpen ? '×' : '☰'}
      </button>

      <nav
        aria-label="주요 메뉴"
        className="header-navigation flex items-center gap-3 whitespace-nowrap max-[760px]:flex-col max-[760px]:items-stretch max-[760px]:gap-0 max-[760px]:border max-[760px]:border-[var(--header-menu-border,#343434)] max-[760px]:bg-[var(--header-menu-bg,#090909)] max-[760px]:p-3 max-[760px]:shadow-xl"
        data-open={isMenuOpen}
        id="header-navigation"
      >
        <div className="flex items-center gap-7 max-[760px]:flex-col max-[760px]:items-stretch max-[760px]:gap-0">
          <NavLink className={(state) => `${getMenuLinkClasses(state)} max-[760px]:px-3 max-[760px]:py-3`} end to="/problems" onClick={closeMenus}>
            문제 목록
          </NavLink>
          <NavLink className={(state) => `${getMenuLinkClasses(state)} max-[760px]:px-3 max-[760px]:py-3`} to="/relay" onClick={closeMenus}>
            릴레이
          </NavLink>
          <NavLink className={(state) => `${getMenuLinkClasses(state)} max-[760px]:px-3 max-[760px]:py-3`} to="/ranking" onClick={closeMenus}>
            랭킹
          </NavLink>
        </div>

        <span className="order-3 text-[var(--theme-border,#555)] max-[760px]:hidden" aria-hidden="true">|</span>

        <button
          aria-label={`헤더에서 ${colorMode === 'dark' ? '라이트' : '다크'} 모드로 전환`}
          aria-pressed={colorMode === 'light'}
          className={`${menuItemClasses} header-theme-toggle order-4 inline-flex cursor-pointer items-center gap-1.5 border-0 bg-transparent p-0 font-[inherit] tracking-[inherit] text-[var(--theme-muted,#a3a3a3)] max-[760px]:w-full max-[760px]:px-3 max-[760px]:py-3`}
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
          {/* span이면 페이지별 `nav span`(구분선 색) 규칙에 걸려 색이 흐려지고
              버튼 hover의 강조색도 덮인다. b를 두고 굵기만 없앤다. */}
          <b className="font-normal min-[761px]:sr-only">
            {colorMode === 'dark' ? 'LIGHT' : 'DARK'}
          </b>
        </button>

        {!loading && (
          <>
            <span className="order-1 text-[var(--theme-border,#555)] max-[760px]:hidden" aria-hidden="true">|</span>

            <div className="header-account relative order-2 flex items-center max-[760px]:mt-1 max-[760px]:w-full max-[760px]:items-stretch max-[760px]:border-t max-[760px]:border-[var(--theme-border,#343434)] max-[760px]:pt-1">
              <button
                aria-controls="header-account-menu"
                aria-expanded={isAccountMenuOpen}
                aria-label={user ? '계정 메뉴' : '로그인 메뉴'}
                className={`${menuItemClasses} header-account-toggle inline-flex cursor-pointer items-center border-0 bg-transparent p-0 font-[inherit] leading-none tracking-[inherit] text-[var(--theme-muted,#a3a3a3)]`}
                onClick={() => setIsAccountMenuOpen((isOpen) => !isOpen)}
                type="button"
              >
                {user ? (
                  <svg aria-hidden="true" className="size-5" fill="none" viewBox="0 0 20 20">
                    <circle cx="10" cy="6.5" r="3" stroke="currentColor" strokeWidth="1.7" />
                    <path d="M4.5 16c.5-3 2.5-4.5 5.5-4.5s5 1.5 5.5 4.5" stroke="currentColor" strokeLinecap="round" strokeWidth="1.7" />
                  </svg>
                ) : (
                  '로그인'
                )}
              </button>

              <div
                className="header-account-menu absolute top-[calc(100%+14px)] right-0 z-30 hidden min-w-[128px] flex-col gap-3 border border-[var(--header-menu-border,#343434)] bg-[var(--header-menu-bg,#090909)] p-4 shadow-xl"
                data-open={isAccountMenuOpen}
                id="header-account-menu"
              >
                {/* span이면 페이지별 `nav span`(구분선 색) 규칙에 걸려 라벨이 흐려진다. */}
                <div className="header-account-menu-label text-[10px] text-[var(--header-menu-muted,#777)]">
                  계정
                </div>
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
