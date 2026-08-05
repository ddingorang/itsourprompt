import { Navigate, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';

import { useAuth } from './AuthContext';
import { useTheme } from '../theme/ThemeContext';

/**
 * 로그인이 필요한 라우트를 감싸는 문지기.
 *
 * - 최초 세션 확인(loading)이 끝나기 전에는 판단을 보류한다.
 *   (바로 판단하면 새로고침 직후 로그인 상태인데도 /login으로 튕기는 오동작이 난다)
 * - 비로그인이면 /login으로 보내되, 원래 가려던 경로를 state.from으로 전달해
 *   로그인 성공 후 그 위치로 복귀할 수 있게 한다(LoginPage 참고).
 *
 * 사용 예 (router.tsx):
 *   { path: '/my', element: <ProtectedRoute><MyPage /></ProtectedRoute> }
 */
export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const { loading, user } = useAuth();
  const { colorMode } = useTheme();
  const location = useLocation();

  if (loading) {
    // 세션 확인 중에는 빈 화면 대신 페이지 배경만 유지한다(깜빡임 최소화).
    return (
      <div
        className="protected-route-loading flex min-h-screen min-w-80 flex-col"
        data-color-mode={colorMode}
      />
    );
  }

  if (!user) {
    return <Navigate replace state={{ from: location.pathname }} to="/login" />;
  }

  return <>{children}</>;
}
