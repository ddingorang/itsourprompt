import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { getProblems } from '../features/problem/api';
import type { ProblemSummary } from '../features/problem/types';
import { ApiProblemError } from '../shared/api/apiClient';

export default function ProblemListPage() {
  const [problems, setProblems] = useState<ProblemSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    getProblems()
      .then((response) => {
        if (isMounted) {
          setProblems(response.problems);
        }
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
        if (isMounted) {
          setIsLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, []);

  return (
    <div className="min-h-screen bg-[#090909] text-[#f5f5ef] [font-family:Arial,'Noto_Sans_KR',sans-serif]">
      <header className="sticky top-0 z-10 flex min-h-[66px] items-center justify-between border-b border-[#343434] bg-[rgba(9,9,9,0.94)] px-[5vw] font-mono text-[15px] tracking-[0.04em] backdrop-blur-[12px] max-[640px]:px-5">
        <Link
          className="text-xl leading-none font-black tracking-[-1.6px] [font-family:Arial,'Noto_Sans_KR',sans-serif]"
          to="/"
          aria-label="홈으로 이동"
        >
          prompt<i className="not-italic text-[#d6ff50]">.</i>practice
        </Link>
        <div className="text-[#a3a3a3] max-[640px]:hidden">
          ANONYMOUS SESSION / NO HISTORY
        </div>
      </header>

      <main className="mx-auto w-[calc(100%_-_10vw)] pt-[clamp(32px,5vw,56px)] pb-20 max-[640px]:w-[min(calc(100%_-_32px),1080px)] max-[640px]:pt-10">
        <header className="flex items-end justify-between gap-6 pb-[54px] max-[640px]:flex-col max-[640px]:items-start max-[640px]:gap-4">
          <h1 className="m-0 font-mono text-[clamp(36px,6vw,64px)] leading-[0.82] font-bold tracking-[-0.04em] text-[#d6ff50]">
            PROBLEM LIST
          </h1>
        </header>

        <section
          className="border-y border-t-[#f5f5ef] border-b-[#343434] py-[15px]"
          aria-label="문제 목록 정보"
        >
          <div className="whitespace-nowrap font-mono text-[11px] font-bold tracking-[0.06em] text-[#a3a3a3]">
            AVAILABLE PROBLEMS / {String(problems.length).padStart(2, '0')}
          </div>
        </section>

        {isLoading && (
          <div className="border-b border-[#343434] py-12 font-mono text-xs leading-[1.7] text-[#a3a3a3]">
            문제 목록을 불러오는 중입니다…
          </div>
        )}

        {errorMessage && (
          <div className="border-b border-[#343434] py-12 font-mono text-xs leading-[1.7] text-[#ff786b]">
            {errorMessage}
          </div>
        )}

        {!isLoading && !errorMessage && problems.length === 0 && (
          <div className="border-b border-[#343434] py-12 font-mono text-xs leading-[1.7] text-[#a3a3a3]">
            등록된 문제가 없습니다.
          </div>
        )}

        {!isLoading && !errorMessage && problems.length > 0 && (
          <section className="border-b border-[#343434]" aria-label="문제 목록">
            {problems.map((problem) => (
              <Link
                className="group grid min-h-24 grid-cols-[64px_minmax(0,1fr)_auto] items-center gap-5 border-t border-[#343434] py-5 text-inherit no-underline transition-[padding,background,color] duration-200 first:border-t-0 hover:bg-[#d6ff50] hover:px-3.5 hover:text-[#090909] focus-visible:bg-[#d6ff50] focus-visible:px-3.5 focus-visible:text-[#090909] focus-visible:outline-none max-[640px]:min-h-22 max-[640px]:grid-cols-[42px_minmax(0,1fr)_auto] max-[640px]:gap-3"
                key={problem.id}
                to={`/problems/${problem.id}`}
              >
                <span className="font-mono text-[17px] text-[#a3a3a3] transition-colors duration-200 group-hover:text-[#090909] group-focus-visible:text-[#090909]">
                  {String(problem.id).padStart(2, '0')}
                </span>
                <span className="min-w-0 text-[clamp(17px,2vw,23px)] font-bold tracking-[-0.03em] [word-break:keep-all]">
                  {problem.title}
                </span>
                <span
                  className="justify-self-end text-2xl text-[#d6ff50] transition-[color,transform] duration-200 group-hover:translate-x-[3px] group-hover:-translate-y-[3px] group-hover:text-[#090909] group-focus-visible:translate-x-[3px] group-focus-visible:-translate-y-[3px] group-focus-visible:text-[#090909]"
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
