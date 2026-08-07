/**
 * 인증 도메인 타입.
 * 백엔드의 MeResponse / SignupRequest / LoginRequest와 1:1로 대응한다.
 * (백엔드 명세: S15P11A505-backend/docs/auth-api.md 참고)
 */

/** 로그인한 사용자 정보. signup/login/GET /api/me 응답이 모두 이 형태다. */
export interface User {
  id: number;
  username: string;
  nickname: string;
  email: string;
  /** 가입 시각(ISO-8601 문자열). 마이페이지의 가입일 표기에 사용한다. */
  createdAt: string;
}

export interface SignupRequest {
  /** 아이디 (3~30자) */
  username: string;
  /** 비밀번호 (8~100자) — 서버에 BCrypt 해시로만 저장된다. */
  password: string;
  /** 닉네임 (2~30자) */
  nickname: string;
  /** 이메일 */
  email: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}
