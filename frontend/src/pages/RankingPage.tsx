import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

import { useAuth } from '../features/auth/AuthContext';
import { getProblemDetail, getProblems } from '../features/problem/api';
import type { ProblemSummary } from '../features/problem/types';
import { getProblemRanking } from '../features/ranking/api';
import type { ProblemRanking } from '../features/ranking/types';
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

const ENTRIES_PER_PAGE = 10;
const PAGES_PER_GROUP = 5;
/** 백엔드가 허용하는 limit 상한. offset이 없어 51위 이하는 어떤 방법으로도 볼 수 없다. */
const RANKING_LIMIT = 50;

/**
 * 랭킹을 열지 못했을 때 화면에 남길 안내. 없는 문제(404)처럼 오류가 아닌 경우도
 * 있어 색과 돌아갈 곳을 함께 담는다.
 */
interface RankingNotice {
  message: string;
  actionLabel: string | null;
  actionTo: string | null;
  isError: boolean;
}

/**
 * 머리글이 한글이라 font-mono와 넓은 자간을 뺀다 — 등폭 글꼴에는 한글 자형이 없어
 * 낱자마다 대체 글꼴로 떨어지고, 0.06em은 그렇게 벌어진 낱자를 더 벌린다. 페이지
 * 루트의 Noto Sans KR을 상속시켜 이름 셀·탭 제목과 같은 결로 둔다.
 */
const headCellClasses =
  'border-b border-[var(--ranking-border)] px-2 py-3 text-left text-[13px] font-bold tracking-[-0.01em] text-[var(--ranking-subtle)]';
/** MY BEST 항목 제목. 표 머리글과 같은 글자 스타일에서 셀 테두리·여백만 뺀 것.
    감싼 밴드가 font-mono라 상속만으로는 벗어날 수 없어 글꼴을 직접 적는다. */
const myBestLabelClasses =
  "[font-family:Arial,'Noto_Sans_KR',sans-serif] text-[13px] font-bold tracking-[-0.01em] text-[var(--ranking-subtle)]";
const cellClasses =
  'border-b border-[var(--ranking-border)] px-2 py-3 font-mono text-[13px] max-[860px]:border-b-0 max-[860px]:py-1';

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

/** 소요 시간 표기 단위. 큰 것부터 늘어놓아 앞에서부터 첫 0 아닌 칸을 찾는다. */
const DURATION_UNITS = [
  { seconds: 86400, label: '일' },
  { seconds: 3600, label: '시간' },
  { seconds: 60, label: '분' },
  { seconds: 1, label: '초' },
];

/**
 * 소요 시간을 한글 단위로, 큰 쪽 두 칸까지만 적는다. 42초 / 4분 12초 / 1시간 23분 /
 * 2일 5시간. 아래 칸이 0이면 생략한다(정확히 2시간이면 "2시간") — 등수를 가르는 숫자가
 * 아니라 얼마나 걸렸는지 훑는 값이라, 두 칸이면 크기를 읽기에 충분하다. 값이 없으면 '--'.
 */
function formatDuration(durationSeconds: number | null): string {
  // 타입은 null만 말하지만 실제로는 undefined도 온다 — durationSeconds를 싣지 않는 옛 백엔드가
  // 붙어 있으면 그렇다(apiRequest는 응답을 검증 없이 캐스팅한다). null만 걸러내면 그때 표
  // 전체가 "NaN초"가 된다. 유한한 수가 아니면 전부 모름으로 본다.
  if (typeof durationSeconds !== 'number' || !Number.isFinite(durationSeconds)) {
    return '--';
  }
  if (durationSeconds < 0) return '--';

  // 0초는 어느 단위에도 못 미쳐 -1이 온다 — 마지막 칸(초)으로 떨어뜨린다.
  const found = DURATION_UNITS.findIndex(
    (unit) => durationSeconds >= unit.seconds,
  );
  const headIndex = found === -1 ? DURATION_UNITS.length - 1 : found;
  const head = DURATION_UNITS[headIndex];
  const parts = [`${Math.floor(durationSeconds / head.seconds)}${head.label}`];

  if (headIndex + 1 < DURATION_UNITS.length) {
    const next = DURATION_UNITS[headIndex + 1];
    const rest = Math.floor((durationSeconds % head.seconds) / next.seconds);

    if (rest > 0) parts.push(`${rest}${next.label}`);
  }

  return parts.join(' ');
}

/**
 * 비용을 소수점 5자리까지만 보이고(그 아래는 반올림해 아예 표시하지 않는다)
 * 뒤쪽 0을 떼어낸다 — 자릿수는 그대로 두되 의미 있는 숫자까지만 밝게 남기기
 * 위함이다. 유효 숫자가 없으면(0.00000) 매치가 실패해 전체가 밝게 남는다 —
 * 온통 흐린 숫자를 피한다.
 */
function splitCost(cost: number): [string, string] {
  const fixed = cost.toFixed(5);
  const match = /^(.*?[1-9])(0+)$/.exec(fixed);

  return match ? [match[1], match[2]] : [fixed, ''];
}

function formatTokens(tokens: number): string {
  return tokens.toLocaleString('en-US');
}

/**
 * 턴 수. 단위를 값에 붙인다 — 860px 미만에서는 thead가 숨어 머리글이 사라지고,
 * MY BEST 밴드에는 머리글이 애초에 없다. 숫자만 두면 무엇을 센 것인지 알 수 없다.
 */
function TurnCount({ turns }: { turns: number }) {
  return (
    <>
      {turns}
      <span className="text-[var(--ranking-subtle)]">턴</span>
    </>
  );
}

/**
 * 문제 하나를 누가 가장 적은 비용으로 풀었는지 보여주는 화면.
 *
 * 백엔드에는 문제별 랭킹(GET /api/problems/{id}/ranking) 하나뿐이라, 이 화면은
 * "문제 탭 + 선택한 문제의 랭킹"으로 짜여 있다. 선택은 주소(?problem=N)에 담아
 * 링크를 그대로 열어도, 새로고침해도 같은 표가 나오게 한다.
 */
export default function RankingPage() {
  const { user } = useAuth();
  const { colorMode } = useTheme();
  const [searchParams, setSearchParams] = useSearchParams();

  const [problems, setProblems] = useState<ProblemSummary[]>([]);
  const [isLoadingProblems, setIsLoadingProblems] = useState(true);
  const [problemsError, setProblemsError] = useState<string | null>(null);
  const [ranking, setRanking] = useState<ProblemRanking | null>(null);
  const [notice, setNotice] = useState<RankingNotice | null>(null);
  /** 목록에 없는 문제(비활성)를 골랐을 때 단건 조회로 채운 제목. 탭 이름에는
      id·title만 쓰이므로 요약의 나머지(타입·언어)는 요구하지 않는다. */
  const [unlistedProblem, setUnlistedProblem] = useState<Pick<
    ProblemSummary,
    'id' | 'title'
  > | null>(null);
  const [hasTabOverflow, setHasTabOverflow] = useState(false);
  const tabNavRef = useRef<HTMLDivElement>(null);
  const selectedTabRef = useRef<HTMLAnchorElement>(null);

  const problemParam = searchParams.get('problem');
  const requestedProblemId = Number(problemParam);
  const isProblemParamUsable =
    Number.isInteger(requestedProblemId) && requestedProblemId >= 1;
  // 없는 ID라도 그대로 쓴다 — 비활성 문제(200)와 없는 문제(404)를 호출 전에 가릴
  // 수 없어 판정을 백엔드에 맡긴다. 999를 무조건 1로 되돌리면 비활성 문제 랭킹으로
  // 가는 길이 함께 막힌다.
  const selectedProblemId = isProblemParamUsable
    ? requestedProblemId
    : (problems[0]?.id ?? null);
  const isUnlisted =
    selectedProblemId !== null &&
    !problems.some((problem) => problem.id === selectedProblemId);
  const tabProblems =
    isUnlisted && unlistedProblem?.id === selectedProblemId
      ? [...problems, unlistedProblem]
      : problems;
  const selectedProblem =
    tabProblems.find((problem) => problem.id === selectedProblemId) ?? null;

  // 결과(ranking)나 안내(notice) 중 하나가 자리에 앉을 때까지가 로딩이다 — 별도
  // 플래그를 두면 목록 로딩이 끝난 직후 한 프레임 동안 표가 사라진다.
  const isLoading =
    isLoadingProblems ||
    (selectedProblemId !== null && ranking === null && notice === null);

  const pageParam = searchParams.get('page');
  const requestedPage = Number(pageParam);
  const {
    currentPage,
    pageGroupEnd,
    pageGroupStart,
    pageStart,
    totalPages,
  } = usePagination({
    itemCount: ranking?.entries.length ?? 0,
    itemsPerPage: ENTRIES_PER_PAGE,
    pagesPerGroup: PAGES_PER_GROUP,
    requestedPage,
  });
  const visibleEntries = (ranking?.entries ?? []).slice(
    pageStart,
    pageStart + ENTRIES_PER_PAGE,
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

  const scrollTabNav = (direction: -1 | 1) => {
    tabNavRef.current?.scrollBy({
      left: direction * tabNavRef.current.clientWidth * 0.7,
      behavior: 'smooth',
    });
  };

  useEffect(() => {
    const controller = new AbortController();

    void (async () => {
      try {
        setProblems((await getProblems(controller.signal)).problems);
      } catch (error: unknown) {
        if (isAbortError(error)) return;

        setProblemsError(
          error instanceof ApiError
            ? error.message
            : '문제 목록을 불러오지 못했습니다.',
        );
      } finally {
        if (!controller.signal.aborted) setIsLoadingProblems(false);
      }
    })();

    return () => {
      controller.abort();
    };
  }, []);

  useEffect(() => {
    if (selectedProblemId === null) return;

    const controller = new AbortController();

    void (async () => {
      setRanking(null);
      setNotice(null);

      try {
        setRanking(
          await getProblemRanking(
            selectedProblemId,
            RANKING_LIMIT,
            controller.signal,
          ),
        );
      } catch (error: unknown) {
        if (isAbortError(error)) return;

        // 없는 문제는 오류가 아니라 주소가 가리키는 곳이 사라졌다는 안내다.
        if (
          error instanceof ApiError &&
          error.code === API_ERROR_CODES.problemNotFound
        ) {
          setNotice({
            message: '요청한 문제를 찾을 수 없습니다.',
            actionLabel: '문제 목록으로 ↗',
            actionTo: '/problems',
            isError: false,
          });
          return;
        }

        setNotice({
          message:
            error instanceof ApiError
              ? error.message
              : '랭킹을 불러오지 못했습니다.',
          actionLabel: null,
          actionTo: null,
          isError: true,
        });
      }
    })();

    return () => {
      controller.abort();
    };
  }, [selectedProblemId]);

  useEffect(() => {
    // 목록을 아직 모르면 "목록에 없다"고 판단할 수 없다.
    if (isLoadingProblems || !isUnlisted || selectedProblemId === null) return;
    if (unlistedProblem?.id === selectedProblemId) return;

    const controller = new AbortController();

    void (async () => {
      try {
        const detail = await getProblemDetail(
          selectedProblemId,
          controller.signal,
        );
        setUnlistedProblem({ id: detail.id, title: detail.title });
      } catch {
        // 제목은 탭 이름일 뿐이라 조용히 포기한다 — 랭킹 쪽이 이미 안내를 내고 있다.
      }
    })();

    return () => {
      controller.abort();
    };
  }, [isLoadingProblems, isUnlisted, selectedProblemId, unlistedProblem]);

  useEffect(() => {
    // 정수가 아니거나 1 미만인 값만 첫 문제로 되돌린다. 파라미터가 아예 없으면
    // 주소를 건드리지 않고 첫 문제를 고른다.
    if (problemParam === null || isProblemParamUsable || isLoadingProblems) {
      return;
    }

    const firstProblemId = problems[0]?.id;
    if (firstProblemId === undefined) return;

    const nextParams = new URLSearchParams(searchParams);
    nextParams.set('problem', String(firstProblemId));
    setSearchParams(nextParams, { replace: true });
  }, [
    isLoadingProblems,
    isProblemParamUsable,
    problemParam,
    problems,
    searchParams,
    setSearchParams,
  ]);

  useEffect(() => {
    const tabNav = tabNavRef.current;
    if (!tabNav) return;

    // 탭이 실제로 넘칠 때만 화살표를 그린다 — 개수로 어림잡으면 문제 제목 길이나
    // 화면 폭이 바뀌는 순간 어긋난다.
    const observer = new ResizeObserver(() => {
      setHasTabOverflow(tabNav.scrollWidth > tabNav.clientWidth);
    });

    observer.observe(tabNav);
    return () => {
      observer.disconnect();
    };
  }, [tabProblems.length]);

  useEffect(() => {
    // 주소로 바로 들어오면 선택된 탭이 스크롤 밖에 있을 수 있다 — 어느 문제를 보고
    // 있는지 탭에서 확인할 수 없으면 선택 표시가 없는 것과 같다.
    selectedTabRef.current?.scrollIntoView({
      block: 'nearest',
      inline: 'center',
    });
  }, [selectedProblemId, tabProblems.length]);

  return (
    <div
      className="ranking-page flex min-h-screen min-w-80 flex-col bg-[var(--ranking-bg)] text-[var(--ranking-text)] [font-family:Arial,'Noto_Sans_KR',sans-serif]"
      data-color-mode={colorMode}
    >
      <Header />

      <main className="mx-auto w-[min(calc(90%_-_360px),1040px)] flex-1 pt-[clamp(28px,4vw,44px)] pb-16 max-[1200px]:w-[calc(100%_-_64px)] max-[640px]:w-[calc(100%_-_32px)] max-[640px]:pt-8">
        <header className="pb-10 max-[640px]:pb-8">
          <h1 className="m-0 font-mono text-[clamp(36px,6vw,64px)] leading-[0.82] font-bold tracking-[-0.04em] text-[var(--ranking-acid)]">
            RANKING
          </h1>
          <p className="mt-2 mb-0 text-[13px] leading-[1.7] text-[var(--ranking-muted)]">
            가장 적은 비용으로 푼 순서
          </p>
        </header>

        {problemsError && (
          <div className="border-b border-[var(--ranking-border)] py-12 font-mono text-xs leading-[1.7] text-[#ff786b]">
            {problemsError}
          </div>
        )}

        {tabProblems.length > 0 && (
          // 표를 한참 내려다보다가도 문제를 갈아탈 수 있게 헤더(66px) 아래 붙인다.
          // 배경이 이미 불투명해 밑줄이 비쳐 오르지는 않는다.
          <section
            className="sticky top-[66px] z-40 border border-[var(--ranking-surface-border)] bg-[var(--ranking-surface)]"
            aria-label="문제 선택"
          >
            <div className="relative">
              <span className="absolute top-0 bottom-0 left-0 z-20 grid w-[180px] place-items-center border-r border-[var(--ranking-surface-border)] bg-[var(--ranking-surface)] font-mono text-lg leading-[1.4] font-bold tracking-[0.08em] text-[var(--ranking-acid)] max-[760px]:hidden">
                PROBLEMS
              </span>
              {hasTabOverflow && (
                <button
                  aria-label="이전 문제 보기"
                  className="absolute top-0 bottom-0 left-[180px] z-20 w-10 cursor-pointer border-0 border-r border-[var(--ranking-surface-border)] bg-[var(--ranking-surface)] font-mono text-2xl font-bold text-[var(--ranking-acid)] hover:bg-[var(--ranking-surface-hover)] focus-visible:outline-2 focus-visible:outline-[var(--ranking-acid)] focus-visible:outline-offset-[-3px] max-[760px]:left-0"
                  onClick={() => scrollTabNav(-1)}
                  type="button"
                >
                  ‹
                </button>
              )}
              <div
                className={[
                  'turn-tab-scrollbar flex items-stretch overflow-x-auto scroll-smooth',
                  hasTabOverflow
                    ? 'pr-12 pl-[220px] max-[760px]:px-[42px]'
                    : 'pl-[180px] max-[760px]:pl-0',
                ].join(' ')}
                ref={tabNavRef}
              >
                {tabProblems.map((problem) => {
                  const isSelected = problem.id === selectedProblemId;

                  return (
                    <Link
                      aria-current={isSelected ? 'page' : undefined}
                      className={`relative flex min-h-[58px] shrink-0 items-center gap-2.5 px-[22px] font-mono text-sm font-bold tracking-[0.06em] whitespace-nowrap hover:text-[var(--ranking-text)] focus-visible:outline-2 focus-visible:outline-[var(--ranking-acid)] focus-visible:outline-offset-[-4px] after:absolute after:right-3.5 after:-bottom-px after:left-3.5 after:z-10 after:h-[3px] ${
                        isSelected
                          ? 'text-[var(--ranking-acid)] after:bg-[var(--ranking-acid)]'
                          : 'text-[var(--ranking-tab-idle)] after:bg-transparent'
                      } max-[760px]:px-3.5`}
                      key={problem.id}
                      ref={isSelected ? selectedTabRef : undefined}
                      to={`/ranking?problem=${problem.id}`}
                    >
                      <span>{String(problem.id).padStart(2, '0')}</span>
                      <span className="max-w-[200px] truncate [font-family:Arial,'Noto_Sans_KR',sans-serif] font-bold tracking-[-0.01em]">
                        {problem.title}
                      </span>
                    </Link>
                  );
                })}
              </div>
              {hasTabOverflow && (
                <button
                  aria-label="다음 문제 보기"
                  className="absolute top-0 right-0 bottom-0 z-20 w-10 cursor-pointer border-0 border-l border-[var(--ranking-surface-border)] bg-[var(--ranking-surface)] font-mono text-2xl font-bold text-[var(--ranking-acid)] hover:bg-[var(--ranking-surface-hover)] focus-visible:outline-2 focus-visible:outline-[var(--ranking-acid)] focus-visible:outline-offset-[-3px]"
                  onClick={() => scrollTabNav(1)}
                  type="button"
                >
                  ›
                </button>
              )}
            </div>
          </section>
        )}

        <section
          className="mt-6 border-y border-t-[var(--ranking-text)] border-b-[var(--ranking-border)] py-[15px]"
          aria-label="랭킹 정보"
        >
          <div className="font-mono text-[13px] font-bold tracking-[0.06em] text-[var(--ranking-muted)]">
            RANKED SUBMISSIONS /{' '}
            {String(ranking?.totalCount ?? 0).padStart(2, '0')}
            {ranking && ranking.totalCount > RANKING_LIMIT && (
              <span> · 상위 {RANKING_LIMIT}위까지 표시</span>
            )}
          </div>
        </section>

        {isLoading && (
          <div className="border-b border-[var(--ranking-border)] py-12 font-mono text-xs leading-[1.7] text-[var(--ranking-muted)]">
            랭킹을 불러오는 중입니다…
          </div>
        )}

        {!isLoading && notice && (
          <div
            className={[
              'flex flex-col items-start gap-5 border-b py-12 font-mono text-xs leading-[1.7]',
              notice.isError
                ? 'border-[#ff786b] text-[#ff786b]'
                : 'border-[var(--ranking-border)] text-[var(--ranking-muted)]',
            ].join(' ')}
            role={notice.isError ? 'alert' : undefined}
          >
            <div>{notice.message}</div>
            {notice.actionLabel && notice.actionTo && (
              <Button className="ranking-primary-action" to={notice.actionTo}>
                {notice.actionLabel}
              </Button>
            )}
          </div>
        )}

        {!isLoading && !notice && selectedProblemId === null && (
          <div className="border-b border-[var(--ranking-border)] py-12 font-mono text-xs leading-[1.7] text-[var(--ranking-muted)]">
            등록된 문제가 없습니다.
          </div>
        )}

        {!isLoading && !notice && ranking && ranking.entries.length === 0 && (
          <div className="flex flex-col items-start gap-5 border-b border-[var(--ranking-border)] py-12">
            <p className="m-0 font-mono text-xs leading-[1.7] text-[var(--ranking-muted)]">
              아직 이 문제의 랭킹에 오른 풀이가 없습니다.
            </p>
            <Button
              className="ranking-primary-action"
              to={`/problems/${ranking.problemId}`}
            >
              문제 풀러 가기 ↗
            </Button>
          </div>
        )}

        {!isLoading && !notice && ranking && ranking.entries.length > 0 && (
          <>
            {/*
              모바일에서 <tr>을 그리드로 접으면 표 시맨틱이 사라진다. role을 전부
              명시해 두는 것이 그때 의미를 되살리는 유일한 수단이다.
            */}
            <table
              className="w-full table-fixed border-collapse max-[860px]:block"
              role="table"
            >
              <caption className="sr-only">
                {selectedProblem
                  ? `${selectedProblem.title} 비용 랭킹`
                  : '문제 비용 랭킹'}
              </caption>
              <colgroup>
                <col className="w-16" />
                <col />
                <col className="w-[132px]" />
                <col className="w-[168px]" />
                <col className="w-[84px]" />
                <col className="w-[104px]" />
                <col className="w-[100px]" />
              </colgroup>
              <thead className="max-[860px]:hidden" role="rowgroup">
                <tr role="row">
                  <th className={headCellClasses} role="columnheader" scope="col">
                    등수
                  </th>
                  <th className={headCellClasses} role="columnheader" scope="col">
                    이름
                  </th>
                  <th className={headCellClasses} role="columnheader" scope="col">
                    비용
                  </th>
                  <th className={headCellClasses} role="columnheader" scope="col">
                    입력·캐시·출력
                  </th>
                  <th className={headCellClasses} role="columnheader" scope="col">
                    턴
                  </th>
                  <th className={headCellClasses} role="columnheader" scope="col">
                    소요 시간
                  </th>
                  <th className={headCellClasses} role="columnheader" scope="col">
                    제출일
                  </th>
                </tr>
              </thead>
              <tbody className="max-[860px]:block" role="rowgroup">
                {visibleEntries.map((entry, index) => {
                  const [significantCost, trailingZeros] = splitCost(entry.cost);

                  return (
                    <tr
                      className="max-[860px]:grid max-[860px]:grid-cols-[52px_minmax(0,1fr)_auto_auto] max-[860px]:items-center max-[860px]:gap-x-2 max-[860px]:border-b max-[860px]:border-[var(--ranking-border)] max-[860px]:py-2"
                      key={pageStart + index}
                      role="row"
                    >
                      <td
                        className={`${cellClasses} text-[var(--ranking-muted)] max-[860px]:col-start-1 max-[860px]:row-start-1`}
                        role="cell"
                      >
                        {String(entry.rank).padStart(2, '0')}
                      </td>
                      <td
                        className={`${cellClasses} max-[860px]:col-start-2 max-[860px]:row-start-1`}
                        role="cell"
                      >
                        <div className="flex min-w-0 items-center gap-2">
                          {/*
                            이름은 사람이 아니라 이 줄의 제출 한 건으로 간다 —
                            프로필로 읽히지 않게 접근 이름에 "이 제출"을 박아 둔다.
                          */}
                          <Link
                            aria-label={`${entry.ownerLabel}의 이 제출 피드백 보기`}
                            className="truncate [font-family:Arial,'Noto_Sans_KR',sans-serif] text-[15px] tracking-[-0.02em] underline decoration-[var(--ranking-faint)] underline-offset-[3px] hover:text-[var(--ranking-acid)] hover:decoration-[var(--ranking-acid)] focus-visible:outline-2 focus-visible:outline-[var(--ranking-acid)] focus-visible:outline-offset-2"
                            title={`${entry.ownerLabel}의 이 제출 피드백 보기`}
                            to={`/attempts/${entry.attemptId}/feedback`}
                          >
                            {entry.ownerLabel}
                          </Link>
                          {entry.mine && (
                            <span className="shrink-0 border border-[var(--ranking-acid)] px-1.5 py-0.5 font-mono text-[10px] leading-none font-bold tracking-[0.08em] text-[var(--ranking-acid)]">
                              YOU
                            </span>
                          )}
                        </div>
                      </td>
                      <td
                        className={`${cellClasses} whitespace-nowrap max-[860px]:col-start-3 max-[860px]:row-start-1`}
                        role="cell"
                      >
                        <span className="text-[var(--ranking-subtle)]">$</span>
                        {significantCost}
                        <span className="text-[var(--ranking-faint)]">{trailingZeros}</span>
                      </td>
                      {/* 접힌 줄은 "등수·이름·비용·턴"만 남긴다 — 토큰·소요 시간·날짜는 숨긴다. */}
                      <td
                        className={`${cellClasses} text-[12px] whitespace-nowrap text-[var(--ranking-muted)] max-[860px]:hidden`}
                        role="cell"
                      >
                        {formatTokens(entry.uncachedInputTokens)}
                        <span className="text-[var(--ranking-faint)]">/</span>
                        {formatTokens(entry.cachedInputTokens)}
                        <span className="text-[var(--ranking-faint)]">/</span>
                        {formatTokens(entry.outputTokens)}
                      </td>
                      <td
                        className={`${cellClasses} whitespace-nowrap text-[var(--ranking-muted)] max-[860px]:col-start-4 max-[860px]:row-start-1`}
                        role="cell"
                      >
                        <TurnCount turns={entry.turns} />
                      </td>
                      {/*
                        값이 한글이라 머리글과 같은 이유로 등폭 글꼴을 벗긴다 — 모노에는 한글
                        자형이 없어 "4분 12초"의 숫자와 낱자가 서로 다른 글꼴로 갈린다.
                      */}
                      <td
                        className={`${cellClasses} whitespace-nowrap [font-family:Arial,'Noto_Sans_KR',sans-serif] text-[var(--ranking-muted)] max-[860px]:hidden`}
                        role="cell"
                      >
                        {formatDuration(entry.durationSeconds)}
                      </td>
                      <td
                        className={`${cellClasses} whitespace-nowrap text-[var(--ranking-subtle)] max-[860px]:hidden`}
                        role="cell"
                      >
                        {formatSubmittedAt(entry.submittedAt)}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>

            {totalPages > 1 && (
              <Pagination
                currentPage={currentPage}
                pageGroupEnd={pageGroupEnd}
                pageGroupStart={pageGroupStart}
                totalPages={totalPages}
                onPageChange={moveToPage}
              />
            )}
          </>
        )}

        {!isLoading && !notice && ranking && (
          <section
            className="mt-6 border border-[var(--ranking-border)] p-5 max-[640px]:p-4"
            aria-label="내 최고 기록"
          >
            <div className="font-mono text-lg leading-[1.4] font-bold tracking-[0.08em] text-[var(--ranking-acid)]">
              MY BEST
            </div>
            {ranking.myBest ? (
              <div className="mt-3.5 flex flex-wrap items-end justify-between gap-4">
                <div className="flex flex-wrap gap-x-8 gap-y-3 font-mono text-[15px]">
                  <div>
                    <div className={myBestLabelClasses}>등수</div>
                    <div className="mt-1.5">
                      {String(ranking.myBest.rank).padStart(2, '0')}
                    </div>
                  </div>
                  <div>
                    <div className={myBestLabelClasses}>비용</div>
                    <div className="mt-1.5">
                      <span className="text-[var(--ranking-subtle)]">$</span>
                      {splitCost(ranking.myBest.cost)[0]}
                      <span className="text-[var(--ranking-faint)]">
                        {splitCost(ranking.myBest.cost)[1]}
                      </span>
                    </div>
                  </div>
                  <div>
                    <div className={myBestLabelClasses}>입력·캐시·출력</div>
                    <div className="mt-1.5 text-[14px] leading-[1.6] text-[var(--ranking-muted)]">
                      {formatTokens(ranking.myBest.uncachedInputTokens)}
                      <span className="text-[var(--ranking-faint)]">/</span>
                      {formatTokens(ranking.myBest.cachedInputTokens)}
                      <span className="text-[var(--ranking-faint)]">/</span>
                      {formatTokens(ranking.myBest.outputTokens)}
                    </div>
                  </div>
                  <div>
                    <div className={myBestLabelClasses}>턴</div>
                    <div className="mt-1.5 text-[var(--ranking-muted)]">
                      <TurnCount turns={ranking.myBest.turns} />
                    </div>
                  </div>
                  <div>
                    <div className={myBestLabelClasses}>소요 시간</div>
                    <div className="mt-1.5 [font-family:Arial,'Noto_Sans_KR',sans-serif] text-[var(--ranking-muted)]">
                      {formatDuration(ranking.myBest.durationSeconds)}
                    </div>
                  </div>
                  <div>
                    <div className={myBestLabelClasses}>제출일</div>
                    <div className="mt-1.5 text-[var(--ranking-subtle)]">
                      {formatSubmittedAt(ranking.myBest.submittedAt)}
                    </div>
                  </div>
                </div>
                {/* 표의 이름 링크와 같은 목적지다 — 여긴 이름 대신 버튼으로 놓는다. */}
                <Button
                  className="ranking-primary-action"
                  to={`/attempts/${ranking.myBest.attemptId}/feedback`}
                >
                  <span className="text-[14px]">피드백 보기 ↗</span>
                </Button>
              </div>
            ) : (
              <p className="m-0 mt-3.5 font-mono text-xs leading-[1.7] text-[var(--ranking-muted)]">
                {/*
                  로그인 여부로 문장을 갈라 쓴다 — 로그인 사용자에게 사실은 "아직 없다"이고,
                  게스트에게 사실은 "자격이 없다"이다. 게스트에게 "아직 없다"고만 하면 풀면
                  오를 수 있다고 읽힌다.
                */}
                {user
                  ? '아직 이 문제의 랭킹에 오른 내 풀이가 없습니다.'
                  : '게스트 기록은 랭킹에 오르지 않습니다. 로그인하면 지금까지 푼 기록도 함께 등록됩니다.'}
              </p>
            )}
          </section>
        )}
      </main>
      <Footer />
    </div>
  );
}
