import { useLocation } from 'react-router-dom';

import Button from '../shared/components/Button';
import Footer from '../shared/components/Footer';
import Header from '../shared/components/Header';

interface ErrorPageState {
  title?: string;
  message?: string;
  problemTitle?: string;
  status?: number;
  returnPath?: string;
  returnLabel?: string;
}

export default function ErrorPage() {
  const { state } = useLocation();
  const error = (state ?? {}) as ErrorPageState;

  return (
    <div className="flex min-h-screen min-w-80 flex-col bg-[#090909] text-[#f5f5ef] [font-family:Arial,'Noto_Sans_KR',sans-serif]">
      <Header mobileBreakpoint="760" />

      <main className="mx-auto flex w-[calc(100%_-_10vw)] flex-1 items-center justify-center py-16 max-[760px]:w-[calc(100%_-_32px)]">
        <section className="w-full max-w-2xl border-y border-[#343434] py-12 text-center">
          <p className="font-mono text-xs font-bold tracking-[0.12em] text-[#ff786b]">
            REQUEST FAILED
          </p>

          <h1 className="mt-4 font-mono text-[clamp(44px,8vw,72px)] leading-none font-bold tracking-[-0.05em]">
            ERROR
          </h1>

          {error.problemTitle && (
            <p className="mt-6 font-mono text-xs tracking-[0.08em] text-[#d6ff50]">
              {error.problemTitle}
            </p>
          )}

          <div
            className="mx-auto mt-6 max-w-xl text-sm leading-7 text-[#a3a3a3]"
            role="alert"
          >
            <p className="font-bold text-[#f5f5ef]">
              {error.title ?? '요청 처리 중 오류가 발생했습니다.'}
            </p>
            <p>
              {error.message ??
                '잠시 후 다시 시도해주세요. 문제가 계속되면 이전 페이지로 돌아가주세요.'}
            </p>
            {error.status && (
              <p className="mt-2 font-mono text-xs tracking-[0.08em] text-[#777]">
                ERROR CODE / {error.status}
              </p>
            )}
          </div>

          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <Button to={error.returnPath ?? '/'}>
              {error.returnLabel ?? '이전 화면으로'}
            </Button>
            <Button to="/problems" variant="secondary">
              문제 목록으로
            </Button>
          </div>
        </section>
      </main>

      <Footer />
    </div>
  );
}
