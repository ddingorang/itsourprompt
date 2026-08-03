import { useEffect, useState, type CSSProperties } from 'react';
import { Link } from 'react-router-dom';

import { getProblems } from '../features/problem/api';
import type { ProblemSummary } from '../features/problem/types';
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
    visual: 'TEST',
  },
];

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

export default function HomePage() {
  const [problems, setProblems] = useState<ProblemSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

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
    <div className="landing-page flex min-h-screen min-w-80 flex-col bg-[#090909] text-[#f5f5ef] [font-family:Arial,'Noto_Sans_KR',sans-serif]">
      <Header mobileBreakpoint="760" />

      <main className="flex-1 overflow-clip">
        <section className="landing-hero mx-auto flex min-h-[calc(100svh-66px)] w-[80%] flex-col justify-between pt-[clamp(34px,5vw,64px)] pb-8 max-[1100px]:w-[90%] max-[760px]:w-[calc(100%_-_32px)]">
          <div className="flex flex-1 flex-col justify-center">
            <div className="flex items-center justify-between gap-4 font-mono text-[17px] font-bold tracking-[0.14em] text-[#d6ff50]">
              <span>PROMPT ENGINEERING PRACTICE</span>
            </div>
            <div className="pt-7 pb-14">
              <h1 className="landing-display m-0 max-w-[1100px] text-[clamp(52px,9vw,110px)] leading-[1.0] font-black tracking-[-0.05em]">
                더 명확한 요청
                <br />
                <span className="text-[#d6ff50]">더 정확한 코드</span>
              </h1>
              <p className="mt-7 mb-0 max-w-[650px] text-[clamp(14px,1.8vw,25px)] leading-[1.65] font-semibold [word-break:keep-all]">
                우리는 프롬프트를 단순한 문장이 아니라
                <br />
                ‘문제를 해결하는 논리적 사고’ 라고 믿습니다.
              </p>
            </div>
          </div>

          <div className="relative grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-end gap-8 border-t border-[#343434] pt-7 max-[680px]:grid-cols-1">
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

        <section className="landing-guest min-h-[calc(100svh-66px)] border-b border-[#090909] bg-[#d6ff50] text-[#090909]">
          <div className="mx-auto grid min-h-[calc(100svh-66px)] w-[80%] grid-cols-2 items-center gap-[8vw] pt-[clamp(34px,5vw,64px)] pb-8 max-[1100px]:w-[90%] max-[760px]:min-h-[90svh] max-[760px]:w-[calc(100%_-_32px)] max-[760px]:grid-cols-1">
          <div className="order-2 max-[760px]:order-1" data-reveal>
            <div className="mb-7 font-mono text-sm font-bold tracking-[0.16em]">NO SIGN-IN NEEDED</div>
            <h2 className="m-0 text-[clamp(46px,6.5vw,94px)] leading-[1.1] font-black tracking-[-0.04em] [word-break:keep-all]">
              로그인은
              <br />
              나중이어도
              <br />
              괜찮아요.
            </h2>
            <p className="mt-9 max-w-[520px] text-[clamp(17px,1.5vw,21px)] leading-[1.7] text-[#282828] [word-break:keep-all]">
              회원가입 없이 문제를 고르고, 프롬프트를 실행하고, 
              <br />
              피드백까지 확인할 수 있습니다. 
              <br />
              먼저 경험한 뒤 기록을 남기고 싶을 때 로그인하세요.
            </p>
          </div>
          <div className="landing-pass order-1 self-center max-[760px]:order-2" data-reveal style={{ '--reveal-delay': '140ms' } as CSSProperties}>
            <div className="landing-pass-center">
              <span className="landing-pass-check" aria-hidden="true">✓</span>
              <strong>NO LOGIN</strong>
              <span>REQUIRED</span>
            </div>
          </div>
          </div>
        </section>

        <section className="flex h-[calc(100svh-66px)] min-h-[640px] items-start border-y border-[#343434] bg-[#111] pt-[clamp(34px,5vw,64px)] pb-8 max-[760px]:h-auto max-[760px]:min-h-0 max-[760px]:pb-16">
          <div className="mx-auto w-[80%] max-[1100px]:w-[90%] max-[760px]:w-[calc(100%_-_32px)]">
            <div className="mb-[clamp(36px,5vh,52px)] flex items-end justify-between gap-8 max-[680px]:items-start">
              <div data-reveal>
                <div className="mb-6 font-mono text-sm font-bold tracking-[0.16em] text-[#d6ff50]">HOW IT WORKS</div>
                <h2 className="m-0 text-[clamp(38px,5vw,70px)] leading-[1.1] font-black tracking-[-0.04em]">
                  세 단계면 충분합니다.
                </h2>
              </div>
            </div>

            <div className="landing-steps">
              {steps.map((step, index) => (
                <article className="landing-step" data-reveal key={step.number} style={{ '--reveal-delay': `${index * 100}ms` } as CSSProperties}>
                  <div className="font-mono text-xs tracking-[0.13em] text-[#a3a3a3]">{step.number}</div>
                  <div className="landing-step-visual" aria-hidden="true">{step.visual}</div>
                  <h3 className="mb-4 text-[clamp(22px,2vw,30px)] font-bold tracking-[-0.04em]">{step.title}</h3>
                  <p className="m-0 leading-[1.65] text-[#a3a3a3] [word-break:keep-all]">{step.description}</p>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section className="mx-auto flex h-[calc(100svh-66px)] min-h-[620px] w-[80%] flex-col pt-[clamp(34px,5vw,64px)] pb-8 max-[1100px]:w-[90%] max-[760px]:h-auto max-[760px]:min-h-0 max-[760px]:w-[calc(100%_-_32px)] max-[760px]:pb-20">
          <div className="relative mb-8 flex items-end justify-between gap-8 pb-6" data-reveal>
            <div>
              <div className="mb-5 font-mono text-sm font-bold tracking-[0.16em] text-[#d6ff50]">PRACTICE NOW</div>
              <h2 className="m-0 text-[clamp(38px,5vw,70px)] leading-none font-black tracking-[-0.04em]">어떤 문제부터 풀어볼까요?</h2>
            </div>
            <Link
  className="hidden items-center gap-4 text-base font-bold text-[#d6ff50] sm:flex"
  to="/problems"
>
  <span>전체 문제 보기</span>
  <span aria-hidden="true">↗</span>
</Link>
            <div
              className="absolute inset-x-0 bottom-0 h-px bg-[#d6ff50]"
              aria-hidden="true"
            />
          </div>

          {isLoading && <div className="border-b border-[#343434] py-11 font-mono text-xs leading-[1.7] text-[#a3a3a3]">문제 목록을 불러오는 중입니다.</div>}
          {errorMessage && <div className="border-b border-[#343434] py-11 font-mono text-xs leading-[1.7] text-[#ff786b]">{errorMessage}</div>}

          {!isLoading && !errorMessage && (
            <div className="border-b border-[#343434]">
              {problems.slice(0, 3).map((problem) => (
                <Link className="landing-problem group grid min-h-[102px] grid-cols-[76px_minmax(0,1fr)_42px] items-center gap-4 border-t border-[#343434] py-[18px] first:border-t-0 max-[760px]:min-h-[100px] max-[760px]:grid-cols-[44px_minmax(0,1fr)_28px]" key={problem.id} to={`/problems/${problem.id}`}>
                  <span className="font-mono text-[17px] text-[#777]">{String(problem.id).padStart(2, '0')}</span>
                  <div className="min-w-0 text-[clamp(19px,2.2vw,27px)] font-bold tracking-[-0.035em] [word-break:keep-all]">{problem.title}</div>
                  <span className="justify-self-end text-2xl text-[#d6ff50]" aria-hidden="true">↗</span>
                </Link>
              ))}
            </div>
          )}
        </section>

        <div className="landing-ending flex min-h-[calc(100svh-66px)] flex-col">
          <section className="landing-final relative flex flex-1 items-center justify-center overflow-hidden border-t border-[#343434] px-[5vw] py-[clamp(64px,8vh,100px)] text-center">
            <div className="landing-final-grid" aria-hidden="true" />
            <div className="relative z-[1]" data-reveal>
              <p className="mb-7 font-mono text-lg font-bold tracking-[0.18em] text-[#d6ff50]">READY WHEN YOU ARE</p>
              <h2 className="mx-auto mb-10 max-w-[1000px] text-[clamp(50px,9vw,100px)] leading-[1.0] font-black tracking-[-0.05em]">
                연습이 쌓이면
                <br />
                프롬프트가 달라집니다.
              </h2>
              <Button className="min-h-14 gap-2.5 px-7" to="/problems">
                <span className="text-[14px]">문제 풀어보기</span>
                <span className="text-[14px]" aria-hidden="true">↗</span>
              </Button>
            </div>
          </section>
          <Footer />
        </div>
      </main>
    </div>
  );
}
