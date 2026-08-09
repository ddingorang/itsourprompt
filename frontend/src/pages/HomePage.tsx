import { useEffect, useLayoutEffect, useState, type CSSProperties } from 'react';
import { Link } from 'react-router-dom';

import { getProblems } from '../features/problem/api';
import type { ProblemSummary } from '../features/problem/types';
import { useTheme } from '../features/theme/ThemeContext';
import { ApiError } from '../shared/api/apiClient';
import Button from '../shared/components/Button';
import Footer from '../shared/components/Footer';
import Header from '../shared/components/Header';

const steps = [
  {
    number: '01',
    title: '문제를 고르세요',
    description: '연습하고 싶은 상황과 언어를 보고, 지금 내게 필요한 문제를 선택합니다.',
    visual: 'SELECT',
  },
  {
    number: '02',
    title: '프롬프트를 작성하세요',
    description: '단 한 번의 실행을 전제로, 원하는 결과를 얻기 위한 지시문을 직접 설계합니다.',
    visual: 'WRITE',
  },
  {
    number: '03',
    title: '피드백을 확인하세요',
    description: '실행 결과와 항목별 피드백을 비교하며 더 좋은 프롬프트의 기준을 익힙니다.',
    visual: 'FEEDBACK',
  },
];

/** 부모 Button의 `group` hover·focus를 따라 살짝 밀려나는 화살표. 마이페이지 버튼과 같은 동작. */
function ButtonArrow() {
  return (
    <span
      className="transition-transform duration-200 group-hover:translate-x-0.5 group-hover:-translate-y-0.5 group-focus-visible:translate-x-0.5 group-focus-visible:-translate-y-0.5"
      aria-hidden="true"
    >
      ↗
    </span>
  );
}

function useRevealOnScroll() {
  useEffect(() => {
    const elements = document.querySelectorAll<HTMLElement>('[data-reveal]');
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-visible');
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.16, rootMargin: '0px 0px -7% 0px' },
    );

    elements.forEach((element) => observer.observe(element));
    return () => observer.disconnect();
  }, []);
}

function useResetLandingScroll() {
  useLayoutEffect(() => {
    const previousScrollRestoration = window.history.scrollRestoration;
    let secondFrame = 0;

    const scrollToFirstSection = () => {
      window.scrollTo(0, 0);
    };

    window.history.scrollRestoration = 'manual';
    scrollToFirstSection();

    const firstFrame = window.requestAnimationFrame(() => {
      secondFrame = window.requestAnimationFrame(scrollToFirstSection);
    });

    window.addEventListener('pageshow', scrollToFirstSection);

    return () => {
      window.cancelAnimationFrame(firstFrame);
      window.cancelAnimationFrame(secondFrame);
      window.removeEventListener('pageshow', scrollToFirstSection);
      window.history.scrollRestoration = previousScrollRestoration;
    };
  }, []);
}

export default function HomePage() {
  const { colorMode } = useTheme();
  const [problems, setProblems] = useState<ProblemSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useResetLandingScroll();
  useRevealOnScroll();

  useEffect(() => {
    let isMounted = true;

    getProblems()
      .then((response) => {
        if (isMounted) setProblems(response.problems);
      })
      .catch((error: unknown) => {
        if (!isMounted) return;
        setErrorMessage(
          error instanceof ApiError ? error.message : '문제 목록을 불러오지 못했습니다.',
        );
      })
      .finally(() => {
        if (isMounted) setIsLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, []);

  return (
    <div className="landing-page flex min-h-screen min-w-80 flex-col bg-[var(--landing-bg)] text-[var(--landing-text)] [font-family:Arial,'Noto_Sans_KR',sans-serif]" data-color-mode={colorMode}>
      <Header
        mobileBreakpoint="760"
        onLogoClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}
      />

      <main className="flex-1 overflow-clip">
        {/* min-h로 둔다 — h로 고정하면 main의 overflow-clip과 만나 화면이 낮을 때
            스크롤 안내가 스크롤되지 못하고 잘려 나간다. */}
        <section className="landing-hero mx-auto flex min-h-[calc(100svh_-_var(--landing-header-h))] w-[80%] flex-col justify-between pt-[clamp(34px,5vw,64px)] pb-4 max-[1100px]:w-[90%] max-[760px]:w-[calc(100%_-_32px)]">
          <div className="flex flex-1 flex-col justify-center">
            <div className="flex items-center gap-4 font-mono text-[19px] font-bold tracking-[0.14em] text-[var(--acid)]">
              <span>PROMPT ENGINEERING PRACTICE</span>
            </div>
            <div className="pt-7 pb-6">
              <h1 className="landing-display m-0 max-w-[1100px] text-[clamp(52px,9vw,110px)] leading-[1.0] font-black tracking-[-0.05em]">
                더 명확한 요청
                <br />
                <span className="text-[var(--acid)]">더 정확한 코드</span>
              </h1>
              <p className="mt-7 mb-0 max-w-[650px] text-[clamp(14px,1.8vw,25px)] leading-[1.65] font-semibold [word-break:keep-all]">
                우리는 프롬프트를 단순한 문장이 아니라
                <br />
                ‘문제를 해결하는 논리적 사고’ 라고 믿습니다.
              </p>
            </div>
          </div>

          <div className="landing-hero-actions mb-5 ml-auto flex flex-wrap justify-end gap-3 max-[680px]:ml-0 max-[680px]:grid max-[680px]:grid-cols-1">
            <Button className="group min-h-14 px-7" to="/problems">
              <span>혼자 시작하기</span>
              <ButtonArrow />
            </Button>
            {/* mode=together를 읽는 쪽은 아직 없다 — 향후 함께 풀기 연동을 위한 자리표시자다. */}
            <Button
              className="group min-h-14 px-7"
              to="/relay"
              variant="secondary"
            >
              <span>친구와 함께 풀기</span>
              <ButtonArrow />
            </Button>
          </div>

          <div className="relative grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-end gap-8 border-t border-[var(--landing-border)] pt-6 max-[680px]:grid-cols-1">
            <span className="max-[680px]:hidden" aria-hidden="true" />
            <div className="landing-scroll-cue">
              <span className="landing-scroll-arrows" aria-hidden="true">
                <i />
                <i />
              </span>
              <span>스크롤하여 둘러보기</span>
            </div>
            <span className="max-[680px]:hidden" aria-hidden="true" />
          </div>
        </section>

        <section className="landing-how flex h-[calc(100svh_-_var(--landing-header-h))] min-h-[640px] items-start border-y border-[var(--landing-border)] bg-[var(--landing-surface)] pt-[clamp(34px,5vw,64px)] pb-8 max-[760px]:h-auto max-[760px]:min-h-0 max-[760px]:pb-16">
          <div className="mx-auto w-[80%] max-[1100px]:w-[90%] max-[760px]:w-[calc(100%_-_32px)]">
            <div className="mb-[clamp(48px,7vh,72px)] flex items-end justify-between gap-8 max-[680px]:items-start">
              <div data-reveal>
                <div className="mb-6 font-mono text-[16px] font-bold tracking-[0.16em] text-[var(--acid)]">HOW IT WORKS</div>
                <h2 className="m-0 text-[clamp(38px,5vw,70px)] leading-[1.1] font-black tracking-[-0.04em]">
                  세 단계면 충분합니다.
                </h2>
              </div>
            </div>

            <div className="landing-steps">
              {steps.map((step, index) => (
                <article className="landing-step" data-reveal key={step.number} style={{ '--reveal-delay': `${index * 100}ms` } as CSSProperties}>
                  <div className="font-mono text-xs tracking-[0.13em] text-[var(--landing-muted)]">{step.number}</div>
                  <div className="landing-step-visual" aria-hidden="true">{step.visual}</div>
                  <h3 className="mb-4 text-[clamp(22px,2vw,30px)] font-bold tracking-[-0.04em]">{step.title}</h3>
                  <p className="m-0 leading-[1.65] text-[var(--landing-muted)] [word-break:keep-all]">{step.description}</p>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section className="landing-guest min-h-[calc(100svh_-_var(--landing-header-h))] overflow-hidden border-b border-[var(--landing-border)] bg-[var(--landing-guest-bg)] text-[var(--landing-guest-text)]">
          <div className="mx-auto grid min-h-[calc(100svh_-_var(--landing-header-h))] w-[80%] grid-cols-[minmax(0,0.8fr)_minmax(0,1.2fr)] items-center gap-[6vw] pt-[clamp(34px,5vw,64px)] pb-8 max-[1100px]:w-[90%] max-[900px]:w-[calc(100%_-_32px)] max-[900px]:grid-cols-1 max-[900px]:gap-12 max-[900px]:pb-16">
            {/* 미리보기는 가운데 정렬로 두고 글 단만 위로 붙인다 — 다른 섹션은 모두
                섹션 위 패딩에서 바로 글이 시작하는데 여기만 가운데로 밀려 있었다. */}
            <div className="max-w-[560px] self-start" data-reveal>
              <div className="mb-7 font-mono text-[16px] font-bold tracking-[0.16em] text-[var(--landing-guest-eyebrow)]">NO LOGIN NEEDED</div>
              <h2 className="m-0 text-[clamp(42px,5.4vw,76px)] leading-[1.1] font-black tracking-[-0.04em] [word-break:keep-all]">
                로그인은
                <br />
                나중이어도
                <br />
                괜찮아요.
              </h2>
              <p className="mt-10 mb-0 max-w-[650px] text-[clamp(17px,1.5vw,21px)] leading-[1.7] text-[var(--landing-guest-copy)] [word-break:keep-all]">
                회원가입 없이 문제를 고르고, 프롬프트를 실행하고, 피드백까지 확인할 수 있습니다.
                <br />
                먼저 경험한 뒤 기록을 남기고 싶을 때 로그인하세요.
              </p>
            </div>

            <div className="landing-detail-preview-frame" data-reveal style={{ '--reveal-delay': '140ms' } as CSSProperties}>
              <div className="landing-detail-preview" aria-hidden="true">
                <div className="landing-detail-preview-body">
                  <div className="landing-detail-preview-problem">
                    <span className="landing-detail-preview-label">PROBLEM</span>
                    <strong>API 응답에서 필요한 값 찾기</strong>
                    <p>주어진 응답 구조를 분석하고 원하는 결과를 얻는 프롬프트를 작성하세요.</p>
                  </div>
                  <div className="landing-detail-preview-editor">
                    <div className="landing-detail-preview-tab">PROMPT</div>
                    <div className="landing-detail-preview-code">
                      <i />
                      <i />
                      <i />
                      <i />
                    </div>
                    <div className="landing-detail-preview-run">RUN PROMPT ↗</div>
                  </div>
                  <div className="landing-detail-preview-result">
                    <span className="landing-detail-preview-label">FEEDBACK</span>
                    <strong>실행 결과</strong>
                    <div className="landing-detail-preview-output">
                      <i />
                      <i />
                      <i />
                    </div>
                    <p>요청이 명확하고 출력 형식이 구체적입니다.</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section className="mx-auto flex h-[calc(100svh_-_var(--landing-header-h))] min-h-[620px] w-[80%] flex-col pt-[clamp(34px,5vw,64px)] pb-8 max-[1100px]:w-[90%] max-[760px]:h-auto max-[760px]:min-h-0 max-[760px]:w-[calc(100%_-_32px)] max-[760px]:pb-20">
          <div className="relative mb-8 flex items-end justify-between gap-8 pb-6" data-reveal>
            <div>
              <div className="mb-5 font-mono text-[16px] font-bold tracking-[0.16em] text-[var(--acid)]">PRACTICE NOW</div>
              <h2 className="m-0 text-[clamp(38px,5vw,70px)] leading-none font-black tracking-[-0.04em]">어떤 문제부터 풀어볼까요?</h2>
            </div>
            <Link
  className="landing-accent-link hidden items-center gap-4 text-base font-bold text-[var(--acid)] sm:flex"
  to="/problems"
>
  <span>전체 문제 보기</span>
  <span aria-hidden="true">↗</span>
</Link>
            <div
              className="absolute inset-x-0 bottom-0 h-px bg-[var(--acid)]"
              aria-hidden="true"
            />
          </div>

          {isLoading && <div className="border-b border-[var(--landing-border)] py-11 font-mono text-xs leading-[1.7] text-[var(--landing-muted)]">문제 목록을 불러오는 중입니다.</div>}
          {errorMessage && <div className="border-b border-[var(--landing-border)] py-11 font-mono text-xs leading-[1.7] text-[#d94336]">{errorMessage}</div>}

          {!isLoading && !errorMessage && (
            <div className="border-b border-[var(--landing-border)]">
              {problems.slice(0, 3).map((problem) => (
                <Link className="landing-problem group grid min-h-[102px] grid-cols-[76px_minmax(0,1fr)_42px] items-center gap-4 border-t border-[var(--landing-border)] py-[18px] max-[760px]:min-h-[100px] max-[760px]:grid-cols-[44px_minmax(0,1fr)_28px]" key={problem.id} to={`/problems/${problem.id}`}>
                  <span className="font-mono text-[17px] text-[var(--landing-muted)]">{String(problem.id).padStart(2, '0')}</span>
                  <div className="min-w-0 text-[clamp(19px,2.2vw,27px)] font-bold tracking-[-0.035em] [word-break:keep-all]">{problem.title}</div>
                  <span className="justify-self-end text-2xl text-[var(--acid)]" aria-hidden="true">↗</span>
                </Link>
              ))}
            </div>
          )}
        </section>

        <div className="landing-ending flex min-h-[calc(100svh_-_var(--landing-header-h))] flex-col">
          <section className="landing-final relative flex flex-1 items-center justify-center overflow-hidden border-t border-[var(--landing-border)] px-[5vw] py-[clamp(64px,8vh,100px)] text-center">
            <div className="landing-final-grid" aria-hidden="true" />
            <div className="relative z-[1]" data-reveal>
              <p className="mb-7 font-mono text-[20px] font-bold tracking-[0.18em] text-[var(--acid)]">READY WHEN YOU ARE</p>
              {/* 최소 폰트를 28px까지 낮춘다 — 50px 바닥에서는 좁은 창에서
                  "달라집니다."가 음절 단위로 꺾여 세 줄이 된다. 9vw면 어느 너비에서든
                  한 줄이 90vw 안에 들어와 두 줄이 유지된다. */}
              <h2 className="mx-auto mb-10 max-w-[1000px] text-[clamp(28px,9vw,100px)] leading-[1.0] font-black tracking-[-0.05em]">
                연습이 쌓이면
                <br />
                프롬프트가 달라집니다.
              </h2>
              <Button className="group min-h-14 gap-2.5 px-7" to="/problems">
                <span>문제 풀어보기</span>
                <ButtonArrow />
              </Button>
            </div>
          </section>
          <Footer />
        </div>
      </main>
    </div>
  );
}
