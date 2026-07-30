# 로그인 기능 수동 검증 체크리스트 (FE/login)

> 로그인 이식 후 프론트엔드 동작을 손으로 확인하는 절차.
> 백엔드 API 명세와 설계 배경은 `S15P11A505-backend/docs/auth-api.md` 참고.

## 사전 준비

1. `S15P11A505-backend`에서 `docker compose up -d` (postgres 기동)
2. 백엔드: `cd backend && ./gradlew bootRun` → `http://localhost:9090` (콘솔에 Started 확인)
3. 프론트: `cd frontend && npm run dev` → 터미널에 표시된 주소(기본 `http://localhost:5173`)로 접속
   - Vite proxy가 `/api`를 9090으로 넘기므로 별도 설정 불필요

## 체크리스트

### A. 회원가입
- [ ] 1. 헤더의 **SIGN UP** 클릭 → `/signup` 페이지가 기존 디자인 톤(다크+라임)으로 표시된다
- [ ] 2. 비밀번호를 7자 이하로 입력 후 제출 → 빨간 에러 박스에 입력 형식 안내가 표시된다 (400)
- [ ] 3. 올바른 값(아이디 3자↑/비밀번호 8자↑/닉네임 2자↑/이메일)으로 제출 → **자동 로그인되어 `/my`로 이동**한다
- [ ] 4. 같은 아이디로 다시 가입 시도 → "이미 사용 중인 아이디 또는 이메일입니다." 표시 (409)
   - ⚠️ 아이디/이메일 중 어느 쪽 중복인지는 구분되지 않음 — 공용 apiClient가 백엔드 message를 살리지 못하는 현황 유지 결정 때문 (auth-api.md §3)

### B. 마이페이지 (로그인 상태)
- [ ] 5. USER NAME에 **가입 시 입력한 닉네임**, 아바타에 닉네임 첫 글자가 표시된다
- [ ] 6. MEMBER SINCE에 **실제 가입 연월**(YYYY.MM)이 표시된다
- [ ] 7. 통계 3종(SOLVED/SUBMISSIONS/STREAK)과 YOUR PROGRESS는 더미 데이터이며, 하단에 "추후 연동 예정" 문구가 보인다

### C. 세션 유지
- [ ] 8. **F5 새로고침** 후에도 로그인 상태가 유지된다 (헤더가 PROFILE/LOGOUT 유지)
- [ ] 9. 새로고침 직후 헤더 메뉴가 게스트로 깜빡였다가 바뀌지 않는다 (세션 확인 중에는 메뉴 미표시)
- [ ] 10. DevTools → Application → Cookies에 `JSESSIONID`가 있고 **HttpOnly 체크**되어 있다
- [ ] 11. DevTools → Network → login 요청의 Response Headers에 `Set-Cookie: JSESSIONID=...; HttpOnly`

### D. 헤더 상태 전환
- [ ] 12. 비로그인: 로고 | PROBLEM LIST | LOGIN | SIGN UP
- [ ] 13. 로그인: 로고 | PROBLEM LIST | PROFILE | LOGOUT
- [ ] 14. 모든 페이지(홈/문제목록/문제상세/피드백)에서 로그인 상태가 동일하게 반영된다

### E. 로그인 / 로그아웃
- [ ] 15. 틀린 비밀번호로 로그인 → "아이디 또는 비밀번호가 올바르지 않습니다." (401)
- [ ] 16. 없는 아이디로 로그인 → 같은 문구 (계정 존재 여부 비노출)
- [ ] 17. 로그인 성공 → `/my`(또는 원래 가려던 페이지)로 이동
- [ ] 18. **LOGOUT** 클릭 → 홈(`/`)으로 이동하고 헤더가 게스트 메뉴로 바뀐다
- [ ] 19. 로그아웃 후 주소창에 `/my` 직접 입력 → `/login`으로 리다이렉트된다
- [ ] 20. `/login`에서 로그인하면 다시 `/my`로 복귀한다 (원래 목적지 복원)

### F. 기존 기능 무손상 (회귀)
- [ ] 21. **비로그인 상태**로 문제 목록 → 문제 상세 → 프롬프트 실행(RUN) → 제출까지 기존 흐름이 전부 동작한다
- [ ] 22. 피드백 페이지 표시 정상

## 알려진 주의사항

- **백엔드 재시작(devtools 핫리스타트 포함) 후에는 세션이 사라져 재로그인이 필요**하다 (세션이 서버 메모리에 있음 — 개발 환경 한정).
- 운영 배포 시 FE/BE가 다른 오리진이면 세션 쿠키가 전송되지 않는다 — nginx `/api` 프록시 또는 CORS credentials 전환 필요 (auth-api.md §6).

## 신규/수정 파일 요약 (FE 담당자용)

| 구분 | 파일 | 내용 |
|---|---|---|
| 신규 | `src/features/auth/types.ts` | User/요청 타입 |
| 신규 | `src/features/auth/api.ts` | signup/login/logout/getMe |
| 신규 | `src/features/auth/AuthContext.tsx` | 로그인 상태 전역 공유 (GET /api/me로 판단) |
| 신규 | `src/features/auth/ProtectedRoute.tsx` | 보호 라우트 |
| 신규 | `src/pages/LoginPage.tsx`, `SignupPage.tsx` | 인증 페이지 (기존 스타일 재사용) |
| 수정 | `src/app/App.tsx` | AuthProvider 래핑 (2줄) |
| 수정 | `src/app/router.tsx` | 플레이스홀더 → 실제 페이지, /my 보호 |
| 수정 | `src/shared/components/Header.tsx` | mode prop 제거 → useAuth로 자동 전환 |
| 수정 | `src/pages/MyPage.tsx` | 닉네임/가입일만 실데이터 연결 (디자인 무변경) |
| 삭제 | `src/pages/AuthPlaceholderPage.tsx` | 실제 페이지로 대체됨 |
| 무수정 | `src/shared/api/apiClient.ts` | 팀 결정(에러 포맷 현황 유지)에 따라 손대지 않음 |
