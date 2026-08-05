import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

import { getProblems } from '../features/problem/api';
import type { ProblemSummary } from '../features/problem/types';
import { ApiError } from '../shared/api/apiClient';
import Footer from '../shared/components/Footer';
import Header from '../shared/components/Header';
import Pagination from '../shared/components/Pagination';
import { usePagination } from '../shared/hooks/usePagination';

const PROBLEMS_PER_PAGE = 10;
const PAGES_PER_GROUP = 5;

export default function ProblemListPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [problems, setProblems] = useState<ProblemSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const pageParam = searchParams.get('page');
  const requestedPage = Number(pageParam);
  const {
    currentPage,
    pageGroupEnd,
    pageGroupStart,
    pageStart,
    totalPages,
  } = usePagination({
    itemCount: problems.length,
    itemsPerPage: PROBLEMS_PER_PAGE,
    pagesPerGroup: PAGES_PER_GROUP,
    requestedPage,
  });
  const visibleProblems = problems.slice(
    pageStart,
    pageStart + PROBLEMS_PER_PAGE,
  );

  const moveToPage = (page: number) => {
    const nextParams = new URLSearchParams(searchParams);

    nextParams.set('page', String(page));
    setSearchParams(nextParams);
    window.scrollTo({
      behavior: 'smooth',
      top: 0,
    });
  };

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
          error instanceof ApiError
            ? error.message
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

  useEffect(() => {
    if (pageParam === null) {
      return;
    }

    if (!Number.isInteger(requestedPage) || requestedPage < 1) {
      const nextParams = new URLSearchParams(searchParams);
      nextParams.set('page', '1');
      setSearchParams(nextParams, { replace: true });
      return;
    }

    if (
      isLoading ||
      problems.length === 0 ||
      requestedPage <= totalPages
    ) {
      return;
    }

    const nextParams = new URLSearchParams(searchParams);
    nextParams.set('page', String(totalPages));
    setSearchParams(nextParams, { replace: true });
  }, [
    isLoading,
    pageParam,
    problems.length,
    requestedPage,
    searchParams,
    setSearchParams,
    totalPages,
  ]);

  return (
    <div className="flex min-h-screen flex-col bg-[#090909] text-[#f5f5ef] [font-family:Arial,'Noto_Sans_KR',sans-serif]">
      <Header />

      <main className="mx-auto w-[min(calc(90%_-_360px),1040px)] flex-1 pt-[clamp(28px,4vw,44px)] pb-16 max-[900px]:w-[calc(100%_-_64px)] max-[640px]:w-[calc(100%_-_32px)] max-[640px]:pt-8">
        <header className="flex items-end justify-between gap-6 pb-10 max-[640px]:flex-col max-[640px]:items-start max-[640px]:gap-4 max-[640px]:pb-8">
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
          <>
            <section
              className="border-b border-[#343434]"
              aria-label="문제 목록"
            >
            {visibleProblems.map((problem) => (
              <Link
                className="group grid min-h-18 grid-cols-[56px_minmax(0,1fr)_auto] items-center gap-4 border-t border-[#343434] py-3 text-inherit no-underline transition-[padding,background,color] duration-200 first:border-t-0 hover:bg-[#d6ff50] hover:px-3.5 hover:text-[#090909] focus-visible:bg-[#d6ff50] focus-visible:px-3.5 focus-visible:text-[#090909] focus-visible:outline-none max-[640px]:grid-cols-[42px_minmax(0,1fr)_auto] max-[640px]:gap-3"
                key={problem.id}
                to={`/problems/${problem.id}`}
              >
                <span className="font-mono text-[17px] text-[#a3a3a3] transition-colors duration-200 group-hover:text-[#090909] group-focus-visible:text-[#090909]">
                  {String(problem.id).padStart(2, '0')}
                </span>
                <span className="min-w-0 text-[clamp(16px,1.5vw,20px)] font-bold tracking-[-0.03em] [word-break:keep-all]">
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

            {totalPages > 1 && (
              <>
                <div className="h-6" aria-hidden="true" />
                <Pagination
                  currentPage={currentPage}
                  pageGroupEnd={pageGroupEnd}
                  pageGroupStart={pageGroupStart}
                  totalPages={totalPages}
                  onPageChange={moveToPage}
                />
              </>
            )}
          </>
        )}
      </main>
      <Footer />
    </div>
  );
}
