import { Link, NavLink, useNavigate } from 'react-router-dom';

import { useAuth } from '../../features/auth/AuthContext';

/**
 * 공통 헤더. 로그인 상태(useAuth)를 스스로 읽어 메뉴를 전환한다.
 *
 * - 비로그인: LOGIN / SIGN UP
 * - 로그인:   MY PAGE / LOGOUT
 * - 세션 확인 중(loading): 어느 쪽도 그리지 않는다 —
 *   새로고침 직후 게스트 메뉴가 잠깐 보였다가 바뀌는 깜빡임을 막기 위함.
 *
 * 이전에는 각 페이지가 mode='guest'|'authenticated' prop으로 상태를 넘겼지만,
 * 실제 로그인 도입과 함께 헤더가 직접 판단하도록 바꿨다(호출부에서 prop 제거됨).
 */
type HeaderProps = {
  variant?: 'default' | 'workspace';
  mobileBreakpoint?: '640' | '760';
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
}: HeaderProps) {
  const { loading, logout, user } = useAuth();
  const navigate = useNavigate();

  const headerClasses =
    variant === 'workspace'
      ? workspaceHeaderClasses
      : `${defaultHeaderBaseClasses} ${mobilePaddingClasses[mobileBreakpoint]}`;

  const handleLogout = async () => {
    // 홈 이동을 먼저 한다 — /my(보호 페이지)에서 로그아웃하면 user가 null이 되는 순간
    // ProtectedRoute가 /login으로 리다이렉트해 버려서, 이동을 나중에 하면 경합이 생긴다.
    navigate('/');
    await logout();
  };

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
        <NavLink className={getMenuLinkClasses} end to="/problems">
          PROBLEM LIST
        </NavLink>
        {/* 세션 확인이 끝나기 전에는 로그인/게스트 메뉴를 그리지 않는다(깜빡임 방지). */}
        {!loading && (
          <>
            <span className="text-[#555]" aria-hidden="true">
              |
            </span>
            {user ? (
              <>
                <NavLink className={getMenuLinkClasses} to="/my">
                  MY PAGE
                </NavLink>
                <span className="text-[#555]" aria-hidden="true">
                  |
                </span>
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
            )}
          </>
        )}
      </nav>
    </header>
  );
}
