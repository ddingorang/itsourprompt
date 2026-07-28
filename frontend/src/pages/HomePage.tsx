import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { getProblems } from '../features/problem/api';
import type { ProblemSummary } from '../features/problem/types';
import { ApiProblemError } from '../shared/api/apiClient';
import Header from '../shared/components/Header';

export default function HomePage() {
  const [problems, setProblems] = useState<ProblemSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    getProblems()
      .then((response) => {
        if (isMounted) setProblems(response.problems);
      })
      .catch((error: unknown) => {
        if (!isMounted) return;
        setErrorMessage(
          error instanceof ApiProblemError
            ? error.problem.detail
            : '문제 목록을 불러오지 못했습니다.',
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
    <div className="min-h-screen min-w-80 bg-[#090909] text-[#f5f5ef] [font-family:Arial,'Noto_Sans_KR',sans-serif]">
      <Header mobileBreakpoint="760" />

      <main className="mx-auto w-[calc(100%_-_10vw)] pt-[clamp(32px,5vw,56px)] pb-20 max-[760px]:w-[min(calc(100%_-_32px),680px)] max-[760px]:pt-8">
        <div className="font-mono text-xl leading-[1.4] font-bold tracking-[0.08em] text-[#d6ff50]">
          PROMPT ENGINEERING PRACTICE
        </div>

        <section className="border-b border-[#d6ff50]">
          <div className="mt-3.5 mb-[54px] flex items-end justify-between gap-10 max-[760px]:mb-[34px] max-[760px]:flex-col max-[760px]:items-start max-[760px]:gap-[30px]">
            <h1 className="m-0 max-w-[820px] text-[clamp(48px,8vw,88px)] leading-[0.78] font-bold tracking-[-0.075em]">
              ONE PROMPT
              <br />
              ONE RUN
            </h1>
            <Link
              className="inline-flex shrink-0 items-center gap-7 border border-[#d6ff50] bg-[#d6ff50] px-5 py-[15px] text-xs leading-none font-extrabold tracking-[-0.01em] text-[#090909] transition-[gap,background,color] duration-200 hover:gap-9 hover:bg-transparent hover:text-[#d6ff50] focus-visible:gap-9 focus-visible:bg-transparent focus-visible:text-[#d6ff50] focus-visible:outline-none"
              to="/problems"
            >
              VIEW PROBLEMS
              <span aria-hidden="true">↗</span>
            </Link>
          </div>
        </section>

        {isLoading && (
          <div className="border-b border-[#343434] py-11 font-mono text-xs leading-[1.7] text-[#a3a3a3]">
            문제 목록을 불러오는 중입니다…
          </div>
        )}

        {errorMessage && (
          <div className="border-b border-[#343434] py-11 font-mono text-xs leading-[1.7] text-[#ff786b]">
            {errorMessage}
          </div>
        )}

        {!isLoading && !errorMessage && (
          <section className="border-b border-[#343434]">
            {problems.slice(0, 3).map((problem) => (
              <Link
                className="group grid min-h-[108px] grid-cols-[76px_minmax(0,1fr)_42px] items-center gap-4 border-t border-[#343434] py-[22px] transition-[background,padding] duration-200 first:border-t-0 hover:bg-[#171717] hover:px-3.5 focus-visible:bg-[#171717] focus-visible:px-3.5 focus-visible:outline-none max-[760px]:min-h-[100px] max-[760px]:grid-cols-[44px_minmax(0,1fr)_28px]"
                key={problem.id}
                to={`/problems/${problem.id}`}
              >
                <span className="font-mono text-[17px] text-[#a3a3a3]">
                  {String(problem.id).padStart(2, '0')}
                </span>
                <div className="min-w-0 text-[clamp(18px,2.2vw,25px)] font-bold tracking-[-0.035em] [word-break:keep-all]">
                  {problem.title}
                </div>
                <span
                  className="justify-self-end text-2xl text-[#d6ff50] transition-transform duration-200 group-hover:translate-x-[3px] group-hover:-translate-y-[3px] group-focus-visible:translate-x-[3px] group-focus-visible:-translate-y-[3px]"
                  aria-hidden="true"
                >
                  ↗
                </span>
              </Link>
            ))}
          </section>
        )}
      </main>
    </div>
  );
}
