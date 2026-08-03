import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import { useAuth } from '../features/auth/AuthContext';
import { ApiError, API_ERROR_CODES } from '../shared/api/apiClient';
import Button from '../shared/components/Button';
import Footer from '../shared/components/Footer';
import Header from '../shared/components/Header';

/**
 * 로그인 페이지. (기존 AuthPlaceholderPage "COMING SOON" 자리를 대체)
 *
 * - 레이아웃/스타일은 기존 페이지 관례를 그대로 따랐다:
 *   페이지 셸·중앙 카드(AuthPlaceholderPage), 입력창·라벨·에러 박스(ProblemDetailPage).
 * - 로그인 성공 시 원래 가려던 경로(state.from — ProtectedRoute가 넣어줌)로 복귀하고,
 *   직접 /login으로 들어온 경우에는 /my로 이동한다.
 *
 * 에러 문구는 백엔드 오류 코드(bad-credentials 등)로 분기한다. 공용 apiClient가
 * 백엔드의 {code,message}를 그대로 ApiError로 옮겨 주므로 코드로 판별할 수 있다.
 */

const labelClasses =
  'font-mono text-sm leading-[1.5] font-bold tracking-[0.08em] text-[#d6ff50]';

const inputClasses =
  'mt-2.5 w-full border border-[#555] bg-[#131313] p-3.5 text-[13px] leading-[1.6] text-[#f5f5ef] outline-0 focus:border-[#d6ff50] disabled:cursor-not-allowed disabled:opacity-60';

/** 로그인 실패 응답을 사용자에게 보여줄 한국어 문구로 바꾼다. */
function toLoginErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (
      error.code === API_ERROR_CODES.badCredentials ||
      error.status === 401
    ) {
      return '아이디 또는 비밀번호가 올바르지 않습니다.';
    }
    if (error.status === 400) {
      return '입력 값을 확인해주세요.';
    }
  }
  return '로그인에 실패했습니다. 잠시 후 다시 시도해주세요.';
}

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  // ProtectedRoute가 넘겨준 "원래 가려던 경로". 없으면 마이페이지로.
  const from = (location.state as { from?: string } | null)?.from ?? '/my';

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await login({ password, username });
      navigate(from, { replace: true });
    } catch (loginError) {
      setError(toLoginErrorMessage(loginError));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-screen min-w-80 flex-col bg-[#090909] text-[#f5f5ef] [font-family:Arial,'Noto_Sans_KR',sans-serif]">
      <Header />

      <main className="mx-auto flex w-[calc(100%_-_10vw)] flex-1 items-center justify-center py-16 max-[640px]:w-[calc(100%_-_40px)]">
        <section className="w-full max-w-2xl border-y border-[#343434] py-12">
          <p className="text-center font-mono text-xs tracking-[0.12em] text-[#d6ff50]">
            SESSION / COOKIE AUTH
          </p>
          <h1 className="mt-4 text-center font-mono text-[clamp(44px,8vw,72px)] leading-none font-bold tracking-[-0.05em]">
            LOGIN
          </h1>

          <form
            className="mx-auto mt-10 w-full max-w-md px-6"
            onSubmit={handleSubmit}
          >
            <div>
              <label className={labelClasses} htmlFor="login-username">
                ID
              </label>
              <input
                autoComplete="username"
                className={inputClasses}
                disabled={submitting}
                id="login-username"
                onChange={(event) => setUsername(event.target.value)}
                placeholder="아이디를 입력하세요."
                value={username}
              />
            </div>

            <div className="mt-5">
              <label className={labelClasses} htmlFor="login-password">
                PASSWORD
              </label>
              <input
                autoComplete="current-password"
                className={inputClasses}
                disabled={submitting}
                id="login-password"
                onChange={(event) => setPassword(event.target.value)}
                placeholder="비밀번호를 입력하세요."
                type="password"
                value={password}
              />
            </div>

            {error && (
              <div
                className="mt-3.5 border border-[#ff786b] p-3 font-mono text-[10px] leading-[1.6] text-[#ff786b]"
                role="status"
              >
                {error}
              </div>
            )}

            <Button
              className="mt-6"
              disabled={submitting || !username || !password}
              fullWidth
              type="submit"
            >
              {submitting ? 'LOGGING IN…' : 'LOG IN ↗'}
            </Button>

            <p className="mt-6 text-center font-mono text-xs text-[#a3a3a3]">
              계정이 없나요?{' '}
              <Link className="text-[#d6ff50] hover:underline" to="/signup">
                회원가입 →
              </Link>
            </p>
          </form>
        </section>
      </main>

      <Footer />
    </div>
  );
}
