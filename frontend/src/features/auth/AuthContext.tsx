import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';

import * as authApi from './api';
import type { LoginRequest, User } from './types';

/**
 * 로그인 상태를 앱 전체에 공유하는 Context.
 *
 * 세션 + HttpOnly 쿠키 방식이라 클라이언트에는 토큰이 없다. 따라서
 * "지금 로그인돼 있는가?"는 저장된 값이 아니라 서버에 GET /api/me로
 * 물어봐서 판단한다(마운트 시 1회). 401이면 비로그인으로 간주한다.
 *
 * 주의: 이 Provider는 RouterProvider 바깥(App.tsx)에서 렌더되므로
 * 내부에서 useNavigate 같은 라우터 훅을 사용하면 안 된다.
 * 라우팅이 필요한 화면 전환은 각 페이지/컴포넌트에서 처리한다.
 */
interface AuthContextValue {
  /** 현재 로그인 사용자. 비로그인이면 null. */
  user: User | null;
  /** 최초 세션 확인(GET /api/me)이 끝나기 전 true. 이 동안엔 로그인 여부를 판단하지 말 것. */
  loading: boolean;
  /** 로그인. 성공하면 user가 채워진다. 실패 시 ApiProblemError를 던진다(호출부에서 메시지 처리). */
  login: (request: LoginRequest) => Promise<void>;
  /** 로그아웃. 서버 세션을 무효화하고 user를 null로 만든다. */
  logout: () => Promise<void>;
  /** 서버에 세션 상태를 다시 물어봐 user를 갱신한다. */
  refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    try {
      setUser(await authApi.getMe());
    } catch {
      // 401(비로그인)을 포함해 확인 실패는 전부 "비로그인"으로 처리한다.
      setUser(null);
    }
  }, []);

  useEffect(() => {
    // 앱이 열릴 때(새로고침 포함) 세션 쿠키가 유효한지 서버에 확인한다.
    void (async () => {
      await refresh();
      setLoading(false);
    })();
  }, [refresh]);

  const login = useCallback(async (request: LoginRequest) => {
    // 로그인 응답 본문이 곧 사용자 정보라 추가 getMe() 호출이 필요 없다.
    const loggedIn = await authApi.login(request);
    setUser(loggedIn);
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch {
      // 세션이 이미 만료됐거나 서버 오류여도 로컬 상태는 비로그인으로 정리한다.
      // (서버 logout은 멱등 204라 실제로 실패할 일은 거의 없다)
    }
    setUser(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ loading, login, logout, refresh, user }),
    [loading, login, logout, refresh, user],
  );

  return <AuthContext value={value}>{children}</AuthContext>;
}

/** AuthProvider 아래에서만 사용할 수 있는 훅. */
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth는 AuthProvider 안에서만 사용할 수 있습니다.');
  }
  return context;
}
