import { apiRequest } from '../../shared/api/apiClient';
import type { LoginRequest, SignupRequest, User } from './types';

/**
 * 인증 API 호출 모음. 기존 features/problem/api.ts와 동일한 패턴이다.
 *
 * 세션 + HttpOnly 쿠키 방식이라 토큰을 저장/첨부하는 코드가 없다 —
 * 로그인 성공 시 서버가 내려주는 JSESSIONID 쿠키를 브라우저가 자동으로
 * 주고받는다(개발 환경은 Vite proxy로 same-origin이라 추가 설정 불필요).
 *
 * mock 분기(useMocks)는 적용하지 않았다: 인증은 세션 쿠키라는 서버 상태에
 * 의존해서 함수 반환만으로는 흉내낼 수 없고, 로그인 없이 개발할 때는
 * 그냥 비로그인 상태로 두면 되기 때문이다.
 */

/** 회원가입. 성공(201)해도 로그인 상태가 되지는 않는다 — 이어서 login()을 호출할 것. */
export async function signup(request: SignupRequest): Promise<User> {
  return apiRequest<User>('/auth/signup', {
    body: JSON.stringify(request),
    method: 'POST',
  });
}

/** 로그인. 성공 시 세션 쿠키가 발급되고, 응답 본문이 사용자 정보라 별도 getMe() 호출이 불필요하다. */
export async function login(request: LoginRequest): Promise<User> {
  return apiRequest<User>('/auth/login', {
    body: JSON.stringify(request),
    method: 'POST',
  });
}

/** 로그아웃. 서버가 204(빈 응답)를 반환하며, 세션이 이미 만료됐어도 성공(멱등)한다. */
export async function logout(): Promise<void> {
  return apiRequest<void>('/auth/logout', { method: 'POST' });
}

/**
 * 현재 로그인 사용자 조회. 비로그인이면 401로 실패한다.
 * 앱 시작 시 "지금 로그인돼 있는가?"를 판단하는 용도로 쓴다(AuthContext 참고).
 */
export async function getMe(): Promise<User> {
  return apiRequest<User>('/me');
}
