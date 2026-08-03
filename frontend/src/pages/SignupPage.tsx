import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { signup } from '../features/auth/api';
import { useAuth } from '../features/auth/AuthContext';
import { ApiError, API_ERROR_CODES } from '../shared/api/apiClient';
import Button from '../shared/components/Button';
import Footer from '../shared/components/Footer';
import Header from '../shared/components/Header';

/**
 * 회원가입 페이지. (기존 AuthPlaceholderPage "COMING SOON" 자리를 대체)
 *
 * 가입 성공(201) 후에는 서버가 세션을 만들지 않으므로, 이어서 login()을 호출해
 * 자동 로그인시킨 뒤 마이페이지로 보낸다.
 *
 * 에러 문구는 백엔드 오류 코드로 분기하고, 코드가 없는 예외 상황만 상태코드로
 * 처리한다. 아이디/이메일 중복은 코드가 나뉘어 있어 어느 값이 문제인지 알려준다.
 */

const labelClasses =
  'font-mono text-sm leading-[1.5] font-bold tracking-[0.08em] text-[#d6ff50]';

const inputClasses =
  'mt-2.5 w-full border border-[#555] bg-[#131313] p-3.5 text-[13px] leading-[1.6] text-[#f5f5ef] outline-0 focus:border-[#d6ff50] disabled:cursor-not-allowed disabled:opacity-60';

const fieldErrorClasses = 'mt-2 text-[11px] leading-[1.6] text-[#ff786b]';

/**
 * 가입 실패 응답을 사용자에게 보여줄 한국어 문구로 바꾼다.
 * 백엔드가 중복 항목을 코드로 구분해 주므로(duplicate-username / duplicate-email)
 * 어느 값을 고쳐야 하는지까지 알려준다.
 */
function toSignupErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === API_ERROR_CODES.duplicateUsername) {
      return '이미 사용 중인 아이디입니다.';
    }
    if (error.code === API_ERROR_CODES.duplicateEmail) {
      return '이미 사용 중인 이메일입니다.';
    }
    if (error.status === 409) {
      return '이미 사용 중인 아이디 또는 이메일입니다.';
    }
    if (error.status === 400) {
      return '입력 값을 확인해주세요. (아이디 3~30자 / 비밀번호 8자 이상 / 닉네임 2~30자 / 올바른 이메일)';
    }
  }
  return '회원가입에 실패했습니다. 잠시 후 다시 시도해주세요.';
}

export default function SignupPage() {
  const { login } = useAuth();
  const navigate = useNavigate();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [nickname, setNickname] = useState('');
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const usernameError = username && username.length < 3 ? '아이디는 3~30자로 입력해주세요.' : '';
  const passwordError = password && password.length < 8 ? '비밀번호는 8자 이상 입력해주세요.' : '';
  const passwordConfirmError =
    passwordConfirm && password !== passwordConfirm ? '비밀번호가 일치하지 않습니다.' : '';
  const nicknameError = nickname && nickname.length < 2 ? '닉네임은 2~30자로 입력해주세요.' : '';
  const emailError =
    email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
      ? '올바른 이메일 형식으로 입력해주세요.'
      : '';
  const canSubmit = Boolean(
    username &&
      password &&
      passwordConfirm &&
      nickname &&
      email &&
      !usernameError &&
      !passwordError &&
      !passwordConfirmError &&
      !nicknameError &&
      !emailError &&
      !submitting,
  );

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!canSubmit) return;

    setError('');
    setSubmitting(true);
    try {
      await signup({ email, nickname, password, username });
      // 가입만으로는 세션이 생기지 않으므로 곧바로 로그인해 세션을 발급받는다.
      await login({ password, username });
      navigate('/my', { replace: true });
    } catch (signupError) {
      setError(toSignupErrorMessage(signupError));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-screen min-w-80 flex-col bg-[#090909] text-[#f5f5ef] [font-family:Arial,'Noto_Sans_KR',sans-serif]">
      <Header />

      <main className="mx-auto flex w-[calc(100%_-_10vw)] flex-1 items-center justify-center py-16 max-[640px]:w-[calc(100%_-_40px)]">
        <section className="w-full max-w-2xl border-y border-[#343434] py-8">
          <h1 className="text-center font-mono text-[clamp(44px,8vw,72px)] leading-none font-bold tracking-[-0.05em]">
            SIGN UP
          </h1>

          <form
            className="mx-auto mt-10 w-full max-w-md px-6"
            onSubmit={handleSubmit}
          >
            <div>
              <label className={labelClasses} htmlFor="signup-username">
                ID
              </label>
              <input
                aria-describedby={usernameError ? 'signup-username-error' : undefined}
                aria-invalid={Boolean(usernameError)}
                autoComplete="username"
                className={inputClasses}
                disabled={submitting}
                id="signup-username"
                maxLength={30}
                onChange={(event) => setUsername(event.target.value)}
                placeholder="아이디 (3~30자)"
                value={username}
              />
              {usernameError && (
                <p className={fieldErrorClasses} id="signup-username-error" role="alert">
                  {usernameError}
                </p>
              )}
            </div>

            <div className="mt-5">
              <label className={labelClasses} htmlFor="signup-password">
                PASSWORD
              </label>
              <input
                aria-describedby={passwordError ? 'signup-password-error' : undefined}
                aria-invalid={Boolean(passwordError)}
                autoComplete="new-password"
                className={inputClasses}
                disabled={submitting}
                id="signup-password"
                onChange={(event) => setPassword(event.target.value)}
                placeholder="비밀번호 (8자 이상)"
                type="password"
                value={password}
              />
              {passwordError && (
                <p className={fieldErrorClasses} id="signup-password-error" role="alert">
                  {passwordError}
                </p>
              )}
            </div>

            <div className="mt-5">
              <label className={labelClasses} htmlFor="signup-password-confirm">
                PASSWORD CONFIRM
              </label>
              <input
                aria-describedby={passwordConfirmError ? 'signup-password-confirm-error' : undefined}
                aria-invalid={Boolean(passwordConfirmError)}
                autoComplete="new-password"
                className={inputClasses}
                disabled={submitting}
                id="signup-password-confirm"
                onChange={(event) => setPasswordConfirm(event.target.value)}
                placeholder="비밀번호를 다시 입력하세요."
                type="password"
                value={passwordConfirm}
              />
              {passwordConfirmError && (
                <p
                  className={fieldErrorClasses}
                  id="signup-password-confirm-error"
                  role="alert"
                >
                  {passwordConfirmError}
                </p>
              )}
            </div>

            <div className="mt-5">
              <label className={labelClasses} htmlFor="signup-nickname">
                NICKNAME
              </label>
              <input
                aria-describedby={nicknameError ? 'signup-nickname-error' : undefined}
                aria-invalid={Boolean(nicknameError)}
                className={inputClasses}
                disabled={submitting}
                id="signup-nickname"
                maxLength={30}
                onChange={(event) => setNickname(event.target.value)}
                placeholder="닉네임 (2~30자) — 마이페이지에 표시됩니다."
                value={nickname}
              />
              {nicknameError && (
                <p className={fieldErrorClasses} id="signup-nickname-error" role="alert">
                  {nicknameError}
                </p>
              )}
            </div>

            <div className="mt-5">
              <label className={labelClasses} htmlFor="signup-email">
                EMAIL
              </label>
              <input
                aria-describedby={emailError ? 'signup-email-error' : undefined}
                aria-invalid={Boolean(emailError)}
                autoComplete="email"
                className={inputClasses}
                disabled={submitting}
                id="signup-email"
                onChange={(event) => setEmail(event.target.value)}
                placeholder="example@email.com"
                type="email"
                value={email}
              />
              {emailError && (
                <p className={fieldErrorClasses} id="signup-email-error" role="alert">
                  {emailError}
                </p>
              )}
            </div>

            {error && (
              <div
                className="mt-3.5 border border-[#ff786b] p-3 font-mono text-[10px] leading-[1.6] text-[#ff786b]"
                role="status"
              >
                {error}
              </div>
            )}

            <Button className="mt-6" disabled={!canSubmit} fullWidth type="submit">
              {submitting ? 'CREATING…' : 'CREATE ACCOUNT ↗'}
            </Button>

            <p className="mt-6 text-center text-xs text-[#a3a3a3]">
              이미 계정이 있나요?{' '}
              <Link className="ml-2 text-[#d6ff50] hover:underline" to="/login">
                로그인 ↗
              </Link>
            </p>
          </form>
        </section>
      </main>

      <Footer />
    </div>
  );
}
