import { useLocation } from 'react-router-dom';

import { useTheme } from '../features/theme/ThemeContext';
import Button from '../shared/components/Button';
import Footer from '../shared/components/Footer';
import Header from '../shared/components/Header';
import type { ErrorPageState } from '../shared/types/error';

export default function ErrorPage() {
  const { colorMode } = useTheme();
  const { state } = useLocation();
  const errorState = (state ?? {}) as ErrorPageState;

  return (
    <div
      className="error-page flex min-h-dvh min-w-80 flex-col bg-[var(--error-page-bg)] text-[var(--error-page-text)] [font-family:Arial,'Noto_Sans_KR',sans-serif]"
      data-color-mode={colorMode}
    >
      <Header mobileBreakpoint="760" />

      <main className="mx-auto flex w-[calc(100%_-_10vw)] flex-1 items-center justify-center py-16 max-[760px]:w-[calc(100%_-_32px)]">
        <section className="w-full max-w-2xl border-y border-[var(--error-page-border)] py-12 text-center">
          <p className="font-mono text-xs font-bold tracking-[0.12em] text-[#ff786b]">
            REQUEST FAILED
          </p>

          <h1 className="mt-4 font-mono text-[clamp(44px,8vw,72px)] leading-none font-bold tracking-[-0.05em]">
            ERROR
          </h1>

          {errorState.problemTitle && (
            <p className="mt-6 font-mono text-xs tracking-[0.08em] text-[var(--error-page-acid)]">
              {errorState.problemTitle}
            </p>
          )}

          <div
            className="mx-auto mt-6 max-w-xl text-sm leading-7 text-[var(--error-page-muted)]"
            role="alert"
          >
            <p className="font-bold text-[var(--error-page-text)]">
              {errorState.title ?? '요청 처리 중 오류가 발생했습니다.'}
            </p>
            <p>
              {errorState.message ??
                '잠시 후 다시 시도해주세요. 문제가 계속되면 이전 페이지로 돌아가주세요.'}
            </p>
            {errorState.status && (
              <p className="mt-2 font-mono text-xs tracking-[0.08em] text-[var(--error-page-subtle)]">
                ERROR CODE / {errorState.status}
              </p>
            )}
          </div>

          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <Button
              className="error-page-primary-action"
              to={errorState.returnPath ?? '/'}
            >
              {errorState.returnLabel ?? '이전 페이지로'}
            </Button>
            <Button
              className="error-page-secondary-action"
              to="/problems"
              variant="secondary"
            >
              문제 목록으로
            </Button>
          </div>
        </section>
      </main>

      <Footer />
    </div>
  );
}
