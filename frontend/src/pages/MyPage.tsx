import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthContext';
import { getMySubmittedAttempts } from '../features/me/api';
import type { SubmittedAttempt } from '../features/me/types';
import {
  ApiError,
  API_ERROR_CODES,
  isAbortError,
} from '../shared/api/apiClient';
import Button from '../shared/components/Button';
import Footer from '../shared/components/Footer';
import Header from '../shared/components/Header';
import Pagination from '../shared/components/Pagination';
import { usePagination } from '../shared/hooks/usePagination';

const SUBMISSIONS_PER_PAGE = 5;
const PAGES_PER_GROUP = 5;

/** 가입 시각(ISO 문자열)을 "YYYY.MM" 형태로 바꾼다. (MEMBER SINCE 표기용) */
function formatMemberSince(createdAt: string): string {
  const date = new Date(createdAt);
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}`;
}

/** 제출 시각(ISO 문자열)을 "YYYY.MM.DD" 형태로 바꾼다. */
function formatSubmittedAt(submittedAt: string | null): string {
  if (!submittedAt) return '--';

  const date = new Date(submittedAt);
  if (Number.isNaN(date.getTime())) return '--';

  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
  ].join('.');
}

function normalizePageParam(
  pageParam: string | null,
  totalPages: number,
): number | null {
  if (pageParam === null) return null;

  const page = Number(pageParam);
  if (!Number.isInteger(page) || page < 1) return 1;
  return Math.min(page, totalPages);
}

export default function MyPage() {
  // 이 페이지는 ProtectedRoute로 감싸져 있어 user가 항상 존재한다(비로그인은 /login으로 이동됨).
  const { refresh, user } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [submittedAttempts, setSubmittedAttempts] = useState<SubmittedAttempt[]>(
    [],
  );
  const [isLoadingAttempts, setIsLoadingAttempts] = useState(true);
  const [attemptsError, setAttemptsError] = useState<string | null>(null);
  const submissionHistorySectionRef = useRef<HTMLElement>(null);
  const submissionPageParam = searchParams.get('submissionPage');
  const sortParam = searchParams.get('sort');
  const sortOrder = sortParam === 'oldest' ? 'oldest' : 'latest';
  const requestedSubmissionPage = Number(submissionPageParam);
  const submissionPagination = usePagination({
    itemCount: submittedAttempts.length,
    itemsPerPage: SUBMISSIONS_PER_PAGE,
    pagesPerGroup: PAGES_PER_GROUP,
    requestedPage: requestedSubmissionPage,
  });
  const sortedSubmittedAttempts = [...submittedAttempts].sort(
    (attemptA, attemptB) => {
      if (attemptA.submittedAt === null && attemptB.submittedAt === null) return 0;
      if (attemptA.submittedAt === null) return 1;
      if (attemptB.submittedAt === null) return -1;

      return sortOrder === 'latest'
        ? attemptB.submittedAt.localeCompare(attemptA.submittedAt)
        : attemptA.submittedAt.localeCompare(attemptB.submittedAt);
    },
  );
  const visibleSubmittedAttempts = sortedSubmittedAttempts.slice(
    submissionPagination.pageStart,
    submissionPagination.pageStart + SUBMISSIONS_PER_PAGE,
  );

  const moveToPage = (page: number) => {
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set('submissionPage', String(page));
    setSearchParams(nextParams);
    submissionHistorySectionRef.current?.scrollIntoView({
      behavior: 'smooth',
      block: 'start',
    });
  };

  const changeSortOrder = (nextSortOrder: 'latest' | 'oldest') => {
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set('sort', nextSortOrder);
    nextParams.set('submissionPage', '1');
    setSearchParams(nextParams);
  };

  useEffect(() => {
    const controller = new AbortController();

    void (async () => {
      try {
        setIsLoadingAttempts(true);
        setAttemptsError(null);
        setSubmittedAttempts(await getMySubmittedAttempts(controller.signal));
      } catch (error: unknown) {
        if (isAbortError(error)) return;

        if (
          error instanceof ApiError &&
          error.code === API_ERROR_CODES.unauthenticated
        ) {
          await refresh();
          if (controller.signal.aborted) return;

          navigate('/login', {
            replace: true,
            state: { from: location.pathname },
          });
          return;
        }

        setAttemptsError(
          error instanceof ApiError
            ? error.message
            : '제출 내역을 불러오지 못했습니다.',
        );
      } finally {
        if (!controller.signal.aborted) setIsLoadingAttempts(false);
      }
    })();

    return () => {
      controller.abort();
    };
  }, [location.pathname, navigate, refresh]);

  useEffect(() => {
    const normalizedSubmissionPage =
      !isLoadingAttempts && !attemptsError
        ? normalizePageParam(
            submissionPageParam,
            submissionPagination.totalPages,
          )
        : null;
    const nextParams = new URLSearchParams(searchParams);
    let shouldReplace = false;

    if (
      normalizedSubmissionPage !== null &&
      submissionPageParam !== String(normalizedSubmissionPage)
    ) {
      nextParams.set('submissionPage', String(normalizedSubmissionPage));
      shouldReplace = true;
    }

    if (nextParams.has('solvedPage') || nextParams.has('feedbackPage')) {
      nextParams.delete('solvedPage');
      nextParams.delete('feedbackPage');
      shouldReplace = true;
    }

    if (
      sortParam !== null &&
      sortParam !== 'latest' &&
      sortParam !== 'oldest'
    ) {
      nextParams.set('sort', 'latest');
      shouldReplace = true;
    }

    if (shouldReplace) {
      setSearchParams(nextParams, { replace: true });
    }
  }, [
    attemptsError,
    isLoadingAttempts,
    submissionPageParam,
    submissionPagination.totalPages,
    searchParams,
    setSearchParams,
    sortParam,
  ]);

  if (!user) {
    return null;
  }

  const solvedCount = new Set(
    submittedAttempts.map((attempt) => attempt.problemId),
  ).size;
  const stats = [
    {
      label: 'SOLVED',
      value: isLoadingAttempts ? '--' : String(solvedCount),
      suffix: null,
    },
    {
      label: 'SUBMISSIONS',
      value: isLoadingAttempts ? '--' : String(submittedAttempts.length),
      suffix: null,
    },
  ];

  return (
    <div className="flex min-h-screen min-w-80 flex-col bg-[#090909] text-[#f5f5ef] [font-family:Arial,'Noto_Sans_KR',sans-serif]">
      <Header />

      <main className="mx-auto w-[min(calc(90%_-_360px),1040px)] flex-1 pt-[clamp(28px,4vw,44px)] pb-24 max-[1200px]:w-[calc(100%_-_64px)] max-[640px]:w-[calc(100%_-_32px)] max-[640px]:pt-8">
        <h1 className="pb-5 font-mono text-[clamp(26px,4vw,48px)] leading-[0.82] font-bold tracking-[-0.04em] text-[#d6ff50]">
          USER PROFILE
        </h1>

        <section className="grid grid-cols-[minmax(280px,0.8fr)_minmax(0,1.2fr)] border-y border-[#f5f5ef] max-[1200px]:grid-cols-1">
          <div className="flex min-h-[180px] flex-col justify-between border-r border-[#343434] p-[clamp(20px,3vw,32px)] max-[1200px]:min-h-[170px] max-[1200px]:border-r-0 max-[1200px]:border-b">
            <div className="flex items-center gap-5">
              <div
                className="grid size-16 shrink-0 place-items-center rounded-full bg-[#d6ff50] text-2xl font-black text-[#090909]"
                aria-hidden="true"
              >
                {/* 아바타 이니셜: 로그인 사용자 닉네임의 첫 글자 */}
                {user.nickname.charAt(0).toUpperCase()}
              </div>
              <div>
                <p className="mb-1 font-mono text-[13px] tracking-[0.12em] text-[#777]">
                  USER NAME
                </p>
                <h2 className="text-[clamp(28px,4vw,42px)] leading-none font-black tracking-[-0.05em]">
                  {user.nickname}
                </h2>
              </div>
            </div>

            <div>
              <span className="inline-flex items-center gap-2 font-mono text-[13px] tracking-[0.08em] text-[#777]">
                <span className="size-1.5 rounded-full bg-[#d6ff50]" />
                MEMBER SINCE {formatMemberSince(user.createdAt)}
              </span>
            </div>
          </div>

          <div className="grid grid-cols-2 max-[520px]:grid-cols-1">
            {stats.map((stat, index) => (
              <div
                className="flex min-h-[130px] flex-col justify-between border-r border-[#343434] p-[clamp(16px,2vw,24px)] last:border-r-0 max-[520px]:min-h-[110px] max-[520px]:border-r-0 max-[520px]:border-b max-[520px]:last:border-b-0"
                key={stat.label}
              >
                <span className="font-mono text-[13px] tracking-[0.1em] text-[#777]">
                  0{index + 1} / {stat.label}
                </span>
                <div className="flex items-baseline gap-2">
                  <strong className="font-mono text-[clamp(32px,5vw,60px)] leading-none tracking-[-0.08em]">
                    {stat.value}
                  </strong>
                  {stat.suffix && (
                    <span className="font-mono text-[clamp(15px,2vw,22px)] leading-none tracking-[-0.04em] text-[#777]">
                      {stat.suffix}
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>
        </section>

        <section
          className="mt-[clamp(52px,8vw,96px)]"
          ref={submissionHistorySectionRef}
        >
          <div className="flex items-end justify-between gap-6 pb-5 max-[640px]:flex-col max-[640px]:items-start">
            <div className="font-mono text-[clamp(26px,4vw,48px)] leading-[0.82] font-bold tracking-[-0.04em] text-[#d6ff50]">
              SUBMISSION HISTORY
            </div>
            <div
              className="flex items-center gap-3 pr-2 whitespace-nowrap text-[14px] font-normal tracking-[-0.01em] [font-family:Arial,'Noto_Sans_KR',sans-serif]"
              aria-label="문제 정렬 기준"
            >
              <button
                className={`cursor-pointer border-0 bg-transparent p-0 font-[inherit] tracking-[inherit] transition-colors hover:text-[#d6ff50] focus-visible:text-[#d6ff50] focus-visible:outline-none ${
                  sortOrder === 'latest'
                    ? 'text-[#d6ff50]'
                    : 'text-[#a3a3a3]'
                }`}
                type="button"
                aria-pressed={sortOrder === 'latest'}
                onClick={() => changeSortOrder('latest')}
              >
                최신순
              </button>
              <span className="text-[#555]" aria-hidden="true">
                |
              </span>
              <button
                className={`cursor-pointer border-0 bg-transparent p-0 font-[inherit] tracking-[inherit] transition-colors hover:text-[#d6ff50] focus-visible:text-[#d6ff50] focus-visible:outline-none ${
                  sortOrder === 'oldest'
                    ? 'text-[#d6ff50]'
                    : 'text-[#a3a3a3]'
                }`}
                type="button"
                aria-pressed={sortOrder === 'oldest'}
                onClick={() => changeSortOrder('oldest')}
              >
                오래된순
              </button>
            </div>
          </div>

          <div className="border-y border-[#f5f5ef]">
            {isLoadingAttempts && (
              <div className="py-11 font-mono text-xs leading-[1.7] text-[#a3a3a3]">
                제출 내역을 불러오는 중입니다.
              </div>
            )}

            {!isLoadingAttempts && attemptsError && (
              <div
                className="py-11 font-mono text-xs leading-[1.7] text-[#ff786b]"
                role="alert"
              >
                {attemptsError}
              </div>
            )}

            {!isLoadingAttempts &&
              !attemptsError &&
              submittedAttempts.length === 0 && (
                <div className="flex flex-col items-start gap-5 py-11">
                  <p className="font-mono text-xs leading-[1.7] text-[#a3a3a3]">
                    아직 제출한 문제가 없습니다.
                  </p>
                  <Button to="/problems">문제 목록으로 이동 ↗</Button>
                </div>
              )}

            {!isLoadingAttempts &&
              !attemptsError &&
              visibleSubmittedAttempts.map((submittedAttempt, index) => (
              <div
                className="grid min-h-18 grid-cols-[52px_110px_minmax(0,1fr)_auto] items-center gap-4 border-b border-[#343434] px-2 py-3 last:border-b-0 max-[680px]:grid-cols-[38px_minmax(0,1fr)] max-[680px]:gap-3"
                key={submittedAttempt.attemptId}
              >
                <span className="font-mono text-[17px] text-[#777]">
                  {String(submissionPagination.pageStart + index + 1).padStart(
                    2,
                    '0',
                  )}
                </span>
                <span className="font-mono text-[13px] text-[#777] max-[680px]:hidden">
                  {formatSubmittedAt(submittedAttempt.submittedAt)}
                </span>
                <strong className="truncate text-[clamp(15px,2vw,20px)] tracking-[-0.02em]">
                  {submittedAttempt.problemTitle}
                </strong>
                <div className="flex justify-self-end gap-2 max-[680px]:col-span-2 max-[680px]:justify-self-stretch">
                  <Button
                    className="group hover:!border-[#d6ff50] hover:!bg-[#090909] hover:!text-[#d6ff50] focus-visible:!border-[#d6ff50] focus-visible:!bg-[#090909] focus-visible:!text-[#d6ff50] max-[680px]:flex-1"
                    to={`/problems/${submittedAttempt.problemId}`}
                    variant="secondary"
                  >
                    <span className="text-[14px]">문제 풀기</span>
                    <span
                      className="text-[14px] transition-transform duration-200 group-hover:translate-x-0.5 group-hover:-translate-y-0.5 group-focus-visible:translate-x-0.5 group-focus-visible:-translate-y-0.5"
                      aria-hidden="true"
                    >
                      ↗
                    </span>
                  </Button>
                  <Button
                    className="group max-[680px]:flex-1"
                    to={`/attempts/${submittedAttempt.attemptId}/feedback`}
                  >
                    <span className="text-[14px]">피드백 보기</span>
                    <span
                      className="text-[14px] transition-transform duration-200 group-hover:translate-x-0.5 group-hover:-translate-y-0.5 group-focus-visible:translate-x-0.5 group-focus-visible:-translate-y-0.5"
                      aria-hidden="true"
                    >
                      ↗
                    </span>
                  </Button>
                </div>
              </div>
              ))}
          </div>

          {!isLoadingAttempts &&
            !attemptsError &&
            submissionPagination.totalPages > 1 && (
            <Pagination
              currentPage={submissionPagination.currentPage}
              pageGroupEnd={submissionPagination.pageGroupEnd}
              pageGroupStart={submissionPagination.pageGroupStart}
              totalPages={submissionPagination.totalPages}
              onPageChange={moveToPage}
            />
            )}
        </section>
      </main>
      <Footer />
    </div>
  );
}
