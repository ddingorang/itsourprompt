import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthContext';
import { getMySubmittedAttempts } from '../features/me/api';
import type { SubmittedAttempt } from '../features/me/types';
import { getProblems } from '../features/problem/api';
import { useTheme } from '../features/theme/ThemeContext';
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

/**
 * 칸 너비에 맞을 때까지 글자 크기를 줄여 한 줄로 담는다. 글자 수로 크기를 정하면
 * 한글 10자와 숫자 10자의 폭이 두 배 넘게 차이 나 어느 한쪽이 늘 어긋나므로,
 * 실제로 그려진 폭(scrollWidth)을 재서 맞춘다. 최소 크기로도 넘치면 줄임표로 접는다.
 */
function FitText({
  children,
  maxPx,
  minPx,
}: {
  children: string;
  maxPx: number;
  minPx: number;
}) {
  const boxRef = useRef<HTMLDivElement>(null);
  const textRef = useRef<HTMLSpanElement>(null);

  useLayoutEffect(() => {
    const box = boxRef.current;
    const text = textRef.current;
    if (!box || !text) return;

    const fit = () => {
      const available = box.clientWidth;
      if (available <= 0) return;

      text.style.fontSize = `${maxPx}px`;
      if (text.scrollWidth <= available) return;

      // 폭은 글자 크기에 거의 비례한다 — 비율로 한 번에 줄인 뒤 1px씩 다듬는다.
      let size = Math.max(
        minPx,
        Math.floor((maxPx * available) / text.scrollWidth),
      );
      text.style.fontSize = `${size}px`;

      while (size > minPx && text.scrollWidth > available) {
        size -= 1;
        text.style.fontSize = `${size}px`;
      }
    };

    fit();

    // 창 크기가 바뀌면 칸 너비도 바뀐다 — 그때마다 다시 맞춘다.
    const observer = new ResizeObserver(fit);
    observer.observe(box);

    return () => {
      observer.disconnect();
    };
  }, [children, maxPx, minPx]);

  return (
    <div className="min-w-0 overflow-hidden" ref={boxRef}>
      <span
        className="block overflow-hidden text-ellipsis whitespace-nowrap"
        ref={textRef}
        style={{ fontSize: `${maxPx}px` }}
        title={children}
      >
        {children}
      </span>
    </div>
  );
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
  const { colorMode } = useTheme();
  // 이 페이지는 ProtectedRoute로 감싸져 있어 user가 항상 존재한다(비로그인은 /login으로 이동됨).
  const { refresh, user } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [submittedAttempts, setSubmittedAttempts] = useState<SubmittedAttempt[]>(
    [],
  );
  const [problemCount, setProblemCount] = useState<number | null>(null);
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

  // 진도(푼 문제 / 전체 문제)의 분모. 실패해도 진도만 '--'로 두고 나머지는 그대로 보인다.
  useEffect(() => {
    const controller = new AbortController();

    void (async () => {
      try {
        const { problems } = await getProblems(controller.signal);
        setProblemCount(problems.length);
      } catch {
        // 진도는 부가 정보라 화면 전체를 오류로 덮지 않는다.
      }
    })();

    return () => {
      controller.abort();
    };
  }, []);

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
  /** 진도 비율(%). 분모를 아직 모르거나 0이면 막대와 수치를 그리지 않는다. */
  const progressPercent =
    isLoadingAttempts || problemCount === null || problemCount === 0
      ? null
      : Math.round((solvedCount / problemCount) * 100);
  /** 같은 문제를 몇 번 고쳐 냈는지 — 제출 횟수만으로는 알 수 없는 값이다. */
  const averagePerProblem =
    isLoadingAttempts || solvedCount === 0
      ? null
      : `한 문제당 평균 ${(submittedAttempts.length / solvedCount).toFixed(1)}회`;

  return (
    <div
      className="my-page flex min-h-screen min-w-80 flex-col bg-[var(--my-page-bg)] text-[var(--my-page-text)] [font-family:Arial,'Noto_Sans_KR',sans-serif]"
      data-color-mode={colorMode}
    >
      <Header />

      <main className="mx-auto w-[min(calc(90%_-_360px),1040px)] flex-1 pt-[clamp(28px,4vw,44px)] pb-24 max-[1200px]:w-[calc(100%_-_64px)] max-[640px]:w-[calc(100%_-_32px)] max-[640px]:pt-8">
        <h1 className="pb-5 text-[clamp(26px,4vw,48px)] leading-[0.82] font-bold tracking-[-0.04em] text-[var(--my-page-acid)]">
          내 정보
        </h1>

        {/* 닉네임·진도·제출 횟수를 한 줄에 나란히 둔다. 1200px 아래에서는 세 칸이
            위아래로 쌓이고, 그때는 칸 사이 구분선이 오른쪽에서 아래로 옮겨간다. */}
        <section className="grid grid-cols-3 border-y border-[var(--my-page-text)] max-[1200px]:grid-cols-1">
          <div className="flex min-h-[150px] min-w-0 flex-col justify-between gap-3 border-r border-[var(--my-page-border)] p-[clamp(16px,2vw,24px)] max-[1200px]:border-r-0 max-[1200px]:border-b">
            <span className="text-[15px] text-[var(--my-page-muted)]">
              닉네임
            </span>
            {/* 칸이 늘어나는 대신 글자가 줄어든다 — 닉네임 길이와 무관하게 한 줄이다. */}
            <h2 className="m-0 text-right leading-[1.1] font-black tracking-[-0.05em]">
              <FitText maxPx={34} minPx={14}>
                {user.nickname}
              </FitText>
            </h2>
          </div>

          {/* 퍼센트는 막대가 어디까지 찼는지 읽는 값이라 막대 바로 위 시작점에 붙인다. */}
          <div className="flex min-h-[150px] min-w-0 flex-col justify-between gap-3 border-r border-[var(--my-page-border)] p-[clamp(16px,2vw,24px)] max-[1200px]:border-r-0 max-[1200px]:border-b">
            {/* 라벨을 칸 맨 위에 붙여 양옆 칸의 제목과 같은 줄에 오게 한다 —
                가운데 정렬이면 옆의 큰 숫자 높이만큼 아래로 내려간다. */}
            <div className="flex items-start justify-between gap-4">
              <span className="text-[15px] text-[var(--my-page-muted)]">
                전체 문제 진도
              </span>
              <span className="text-right font-mono leading-none tracking-[-0.08em]">
                <strong className="text-[clamp(28px,4vw,44px)]">
                  {isLoadingAttempts ? '--' : solvedCount}
                </strong>
                <span className="text-[clamp(16px,2vw,22px)] text-[var(--my-page-muted)]">
                  {' / '}
                  {problemCount ?? '--'}
                </span>
              </span>
            </div>
            <div>
              <span className="font-mono text-[15px] text-[var(--my-page-acid)]">
                {progressPercent === null ? '--' : `${progressPercent}%`}
              </span>
              {/* 막대는 수치를 한 번 더 말하는 장식이라 화면 낭독에서는 뺀다. */}
              <div
                className="mt-1.5 h-1.5 bg-[var(--my-page-border)]"
                aria-hidden="true"
              >
                <div
                  className="h-full bg-[var(--my-page-acid)]"
                  style={{ width: `${progressPercent ?? 0}%` }}
                />
              </div>
            </div>
          </div>

          {/* 라벨은 왼쪽 위, 값은 오른쪽 아래. 평균은 값에 딸린 설명이라 같은 줄
              왼쪽에 붙이고 숫자와 밑선을 맞춘다. */}
          <div className="flex min-h-[150px] min-w-0 flex-col justify-between gap-3 p-[clamp(16px,2vw,24px)]">
            <span className="text-[15px] text-[var(--my-page-muted)]">
              제출 횟수
            </span>
            <div className="flex items-baseline justify-end gap-3">
              {averagePerProblem && (
                <p className="m-0 min-w-0 text-right text-[12px] text-[var(--my-page-muted)]">
                  {averagePerProblem}
                </p>
              )}
              <strong className="font-mono text-[clamp(28px,4vw,44px)] leading-none tracking-[-0.08em]">
                {isLoadingAttempts ? '--' : submittedAttempts.length}
              </strong>
            </div>
          </div>
        </section>

        <section
          className="mt-[clamp(52px,8vw,96px)]"
          ref={submissionHistorySectionRef}
        >
          <div className="flex items-end justify-between gap-6 pb-5 max-[640px]:flex-col max-[640px]:items-start">
            <div className="text-[clamp(26px,4vw,48px)] leading-[0.82] font-bold tracking-[-0.04em] text-[var(--my-page-acid)]">
              제출 기록
            </div>
            <div
              className="flex items-center gap-3 pr-2 whitespace-nowrap text-[14px] font-normal tracking-[-0.01em] [font-family:Arial,'Noto_Sans_KR',sans-serif]"
              aria-label="문제 정렬 기준"
            >
              <button
                className={`cursor-pointer border-0 bg-transparent p-0 font-[inherit] tracking-[inherit] transition-colors hover:text-[var(--my-page-acid)] focus-visible:text-[var(--my-page-acid)] focus-visible:outline-none ${
                  sortOrder === 'latest'
                    ? 'text-[var(--my-page-acid)]'
                    : 'text-[var(--my-page-muted)]'
                }`}
                type="button"
                aria-pressed={sortOrder === 'latest'}
                onClick={() => changeSortOrder('latest')}
              >
                최신순
              </button>
              <span className="text-[var(--my-page-border)]" aria-hidden="true">
                |
              </span>
              <button
                className={`cursor-pointer border-0 bg-transparent p-0 font-[inherit] tracking-[inherit] transition-colors hover:text-[var(--my-page-acid)] focus-visible:text-[var(--my-page-acid)] focus-visible:outline-none ${
                  sortOrder === 'oldest'
                    ? 'text-[var(--my-page-acid)]'
                    : 'text-[var(--my-page-muted)]'
                }`}
                type="button"
                aria-pressed={sortOrder === 'oldest'}
                onClick={() => changeSortOrder('oldest')}
              >
                오래된순
              </button>
            </div>
          </div>

          <div className="border-y border-[var(--my-page-text)]">
            {isLoadingAttempts && (
              <div className="py-11 font-mono text-xs leading-[1.7] text-[var(--my-page-muted)]">
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
                  <p className="font-mono text-xs leading-[1.7] text-[var(--my-page-muted)]">
                    아직 제출한 문제가 없습니다.
                  </p>
                  <Button className="my-page-primary-action" to="/problems">
                    문제 목록으로 이동 ↗
                  </Button>
                </div>
              )}

            {!isLoadingAttempts &&
              !attemptsError &&
              visibleSubmittedAttempts.map((submittedAttempt, index) => (
              <div
                className="grid min-h-18 grid-cols-[52px_110px_minmax(0,1fr)_auto] items-center gap-4 border-b border-[var(--my-page-border)] px-2 py-3 last:border-b-0 max-[680px]:grid-cols-[38px_minmax(0,1fr)] max-[680px]:gap-3"
                key={submittedAttempt.attemptId}
              >
                <span className="font-mono text-[17px] text-[var(--my-page-muted)]">
                  {String(submissionPagination.pageStart + index + 1).padStart(
                    2,
                    '0',
                  )}
                </span>
                <span className="font-mono text-[13px] text-[var(--my-page-muted)] max-[680px]:hidden">
                  {formatSubmittedAt(submittedAttempt.submittedAt)}
                </span>
                <strong className="truncate text-[clamp(15px,2vw,20px)] tracking-[-0.02em]">
                  {submittedAttempt.problemTitle}
                </strong>
                <div className="flex justify-self-end gap-2 max-[680px]:col-span-2 max-[680px]:justify-self-stretch">
                  <Button
                    className="my-page-secondary-action group max-[680px]:flex-1"
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
                    className="my-page-primary-action group max-[680px]:flex-1"
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
