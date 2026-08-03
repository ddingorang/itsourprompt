import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthContext';
import { getProblems } from '../features/problem/api';
import Button from '../shared/components/Button';
import Footer from '../shared/components/Footer';
import Header from '../shared/components/Header';
import Pagination from '../shared/components/Pagination';
import { usePagination } from '../shared/hooks/usePagination';

const ACTIVITY_ITEMS_PER_PAGE = 5;
const PAGES_PER_GROUP = 5;

// [임시 데이터] 풀이 수와 전체 턴 수는 아직 백엔드 API가 없어 더미 값이다.
// 전체 문제 수는 기존 문제 목록 API에서 조회하고, 사용자 통계 연동에는
// 예: GET /api/me/stats { solved, totalTurns } 같은
// 신규 API가 필요하다 (S15P11A505-backend/docs/auth-api.md §6 후속 과제 참고).
// [임시 데이터] 활동 내역도 더미다. 실데이터 연동에는 어템프트에 소유자(userId)를
// 붙인 뒤 GET /api/me/attempts 로 조회하는 후속 작업이 필요하다.
// 주의: 아래 Link가 activity.id를 problemId로 그대로 쓰고 있으므로,
// 실데이터 연결 시 problemId를 별도 필드로 분리해야 한다.
// [정렬·페이지네이션 확인용 임시 데이터] 실제 API 연동 전에 제거한다.
const recentActivity = Array.from({ length: 15 }, (_, index) => {
  const day = 13 + index;

  return {
    attemptId: index + 1,
    date: `2026.07.${String(day).padStart(2, '0')}`,
    problemId: (index % 3) + 1,
    title: `연습 문제 ${String(index + 1).padStart(2, '0')}`,
  };
});

/** 가입 시각(ISO 문자열)을 "YYYY.MM" 형태로 바꾼다. (MEMBER SINCE 표기용) */
function formatMemberSince(createdAt: string): string {
  const date = new Date(createdAt);
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}`;
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
  const { user } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const [totalProblemCount, setTotalProblemCount] = useState<number | null>(null);
  const activitySectionRef = useRef<HTMLElement>(null);
  const activityPageParam = searchParams.get('solvedPage');
  const sortParam = searchParams.get('sort');
  const sortOrder = sortParam === 'oldest' ? 'oldest' : 'latest';
  const requestedActivityPage = Number(activityPageParam);
  const activityPagination = usePagination({
    itemCount: recentActivity.length,
    itemsPerPage: ACTIVITY_ITEMS_PER_PAGE,
    pagesPerGroup: PAGES_PER_GROUP,
    requestedPage: requestedActivityPage,
  });
  const sortedActivities = [...recentActivity].sort((activityA, activityB) =>
    sortOrder === 'latest'
      ? activityB.date.localeCompare(activityA.date)
      : activityA.date.localeCompare(activityB.date),
  );
  const visibleActivities = sortedActivities.slice(
    activityPagination.pageStart,
    activityPagination.pageStart + ACTIVITY_ITEMS_PER_PAGE,
  );

  const moveToPage = (page: number) => {
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set('solvedPage', String(page));
    setSearchParams(nextParams);
    activitySectionRef.current?.scrollIntoView({
      behavior: 'smooth',
      block: 'start',
    });
  };

  const changeSortOrder = (nextSortOrder: 'latest' | 'oldest') => {
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set('sort', nextSortOrder);
    nextParams.set('solvedPage', '1');
    setSearchParams(nextParams);
  };

  useEffect(() => {
    let isMounted = true;

    getProblems()
      .then((response) => {
        if (isMounted) {
          setTotalProblemCount(response.problems.length);
        }
      })
      .catch(() => {
        // 문제 목록을 불러오지 못하면 대체값을 유지한다.
      });

    return () => {
      isMounted = false;
    };
  }, []);

  useEffect(() => {
    const normalizedActivityPage = normalizePageParam(
      activityPageParam,
      activityPagination.totalPages,
    );
    const nextParams = new URLSearchParams(searchParams);
    let shouldReplace = false;

    if (
      normalizedActivityPage !== null &&
      activityPageParam !== String(normalizedActivityPage)
    ) {
      nextParams.set('solvedPage', String(normalizedActivityPage));
      shouldReplace = true;
    }

    if (nextParams.has('feedbackPage')) {
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
    activityPageParam,
    activityPagination.totalPages,
    searchParams,
    setSearchParams,
    sortParam,
  ]);

  if (!user) {
    return null;
  }

  const stats = [
    {
      label: 'SOLVED',
      value: '3',
      suffix: `/${totalProblemCount ?? '--'}`,
    },
    { label: 'TOTAL TURNS', value: '28', suffix: null },
  ];

  return (
    <div className="flex min-h-screen min-w-80 flex-col bg-[#090909] text-[#f5f5ef] [font-family:Arial,'Noto_Sans_KR',sans-serif]">
      <Header />

      <main className="mx-auto w-[min(calc(90%_-_360px),1040px)] flex-1 pt-[clamp(28px,4vw,44px)] pb-24 max-[1200px]:w-[calc(100%_-_64px)] max-[640px]:w-[calc(100%_-_32px)] max-[640px]:pt-8">
        <section className="flex items-center bg-[#d6ff50] px-[22px] py-5 text-[#090909]">
          <h1 className="m-0 font-mono text-[clamp(26px,4vw,48px)] leading-[0.82] font-bold tracking-[-0.04em]">
            USER PROFILE
          </h1>
        </section>

        <section className="mt-4 grid grid-cols-[minmax(280px,0.8fr)_minmax(0,1.2fr)] border-y border-[#f5f5ef] max-[1200px]:grid-cols-1">
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
                <h1 className="text-[clamp(28px,4vw,42px)] leading-none font-black tracking-[-0.05em]">
                  {user.nickname}
                </h1>
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
          ref={activitySectionRef}
        >
          <div className="flex items-end justify-between gap-6 pb-5 max-[640px]:flex-col max-[640px]:items-start">
            <div className="font-mono text-[clamp(26px,4vw,48px)] leading-[0.82] font-bold tracking-[-0.04em] text-[#d6ff50]">
              SOLVED PROBLEMS
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
            {visibleActivities.map((activity, index) => (
              <div
                className="grid min-h-18 grid-cols-[52px_110px_minmax(0,1fr)_auto] items-center gap-4 border-b border-[#343434] px-2 py-3 last:border-b-0 max-[680px]:grid-cols-[38px_minmax(0,1fr)] max-[680px]:gap-3"
                key={activity.attemptId}
              >
                <span className="font-mono text-[17px] text-[#777]">
                  {String(activityPagination.pageStart + index + 1).padStart(2, '0')}
                </span>
                <span className="font-mono text-[13px] text-[#777] max-[680px]:hidden">
                  {activity.date}
                </span>
                <strong className="truncate text-[clamp(15px,2vw,20px)] tracking-[-0.02em]">
                  {activity.title}
                </strong>
                <div className="flex justify-self-end gap-2 max-[680px]:col-span-2 max-[680px]:justify-self-stretch">
                  <Button
                    className="group hover:!border-[#d6ff50] hover:!bg-[#090909] hover:!text-[#d6ff50] focus-visible:!border-[#d6ff50] focus-visible:!bg-[#090909] focus-visible:!text-[#d6ff50] max-[680px]:flex-1"
                    to={`/problems/${activity.problemId}`}
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
                    to={`/attempts/${activity.attemptId}/feedback`}
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

          {activityPagination.totalPages > 1 && (
            <Pagination
              currentPage={activityPagination.currentPage}
              pageGroupEnd={activityPagination.pageGroupEnd}
              pageGroupStart={activityPagination.pageGroupStart}
              totalPages={activityPagination.totalPages}
              onPageChange={moveToPage}
            />
          )}
        </section>

        <p className="mt-5 font-mono text-[10px] leading-5 tracking-[0.04em] text-[#555]">
          * 통계·활동 데이터는 추후 연동 예정입니다. (사용자 정보는 실제 데이터)
        </p>
      </main>
      <Footer />
    </div>
  );
}
