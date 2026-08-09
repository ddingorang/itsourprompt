import { useEffect, useState, type ComponentType, type SVGProps } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

import { getProblems } from '../features/problem/api';
import { GameIcon, JavaIcon, PythonIcon } from '../features/problem/ProblemIcons';
import type { ProblemSummary } from '../features/problem/types';
import { useTheme } from '../features/theme/ThemeContext';
import { ApiError } from '../shared/api/apiClient';
import Footer from '../shared/components/Footer';
import Header from '../shared/components/Header';
import Pagination from '../shared/components/Pagination';
import { usePagination } from '../shared/hooks/usePagination';

const PROBLEMS_PER_PAGE = 10;
const PAGES_PER_GROUP = 5;

/**
 * 모아보기 분류 키. game 문제는 언어 대신 game으로 묶는다 — 배지와 같은 규칙이라
 * 행에 뜬 아이콘과 탭이 항상 짝이 맞는다. type·language를 싣지 않는 BE 응답이면
 * 분류할 근거가 없어 null이고, 그런 문제는 전체에만 나온다.
 */
function getCategoryKey(problem: ProblemSummary): string | null {
  if (problem.type === 'game') return 'game';
  return problem.language ? problem.language.toLowerCase() : null;
}

/** 탭에 먼저 세울 언어. 제목 옆 아이콘 범례와 같은 순서다. */
const CATEGORY_PRIORITY = ['java', 'python'];

/**
 * 탭 순서. 아는 언어를 범례 순으로 놓고, 나중에 늘어난 언어는 그 뒤에 가나다순으로
 * 붙는다. 게임은 언어가 아니라 문제의 성격이라 맨 끝에 둔다.
 */
function categoryRank(key: string): number {
  if (key === 'game') return 900;
  const index = CATEGORY_PRIORITY.indexOf(key);
  return index === -1 ? 500 : index;
}

export default function ProblemListPage() {
  const { colorMode } = useTheme();
  const [searchParams, setSearchParams] = useSearchParams();
  const [problems, setProblems] = useState<ProblemSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const pageParam = searchParams.get('page');
  const requestedPage = Number(pageParam);

  const categoryCounts = new Map<string, number>();
  problems.forEach((problem) => {
    const key = getCategoryKey(problem);
    if (key) categoryCounts.set(key, (categoryCounts.get(key) ?? 0) + 1);
  });
  const categories = [...categoryCounts.keys()].sort(
    (a, b) => categoryRank(a) - categoryRank(b) || a.localeCompare(b),
  );

  // 목록에 없는 분류가 주소에 남아 있으면(문제가 지워졌거나 손으로 고쳤거나) 전체로
  // 돌린다 — 빈 목록에 탭도 안 눌린 상태보다 낫다.
  const categoryParam = searchParams.get('category');
  const activeCategory =
    categoryParam && categoryCounts.has(categoryParam) ? categoryParam : null;
  const filteredProblems = activeCategory
    ? problems.filter((problem) => getCategoryKey(problem) === activeCategory)
    : problems;

  const {
    currentPage,
    pageGroupEnd,
    pageGroupStart,
    pageStart,
    totalPages,
  } = usePagination({
    itemCount: filteredProblems.length,
    itemsPerPage: PROBLEMS_PER_PAGE,
    pagesPerGroup: PAGES_PER_GROUP,
    requestedPage,
  });
  const visibleProblems = filteredProblems.slice(
    pageStart,
    pageStart + PROBLEMS_PER_PAGE,
  );

  /** 분류를 바꾸면 항상 1페이지로 돌아간다 — 3페이지를 보던 중 2페이지뿐인 분류를
      고르면 빈 화면이 뜬다. */
  const selectCategory = (key: string | null) => {
    const nextParams = new URLSearchParams(searchParams);

    if (key) nextParams.set('category', key);
    else nextParams.delete('category');

    nextParams.set('page', '1');
    setSearchParams(nextParams);
  };

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
      filteredProblems.length === 0 ||
      requestedPage <= totalPages
    ) {
      return;
    }

    const nextParams = new URLSearchParams(searchParams);
    nextParams.set('page', String(totalPages));
    setSearchParams(nextParams, { replace: true });
  }, [
    filteredProblems.length,
    isLoading,
    pageParam,
    requestedPage,
    searchParams,
    setSearchParams,
    totalPages,
  ]);

  return (
    <div
      className="problem-list-page flex min-h-screen flex-col bg-[var(--problem-list-bg)] text-[var(--problem-list-text)] [font-family:Arial,'Noto_Sans_KR',sans-serif]"
      data-color-mode={colorMode}
    >
      <Header />

      {/* 좌우 여백은 릴레이 로비(RelayLobbyPage)와 같은 규칙을 쓴다 — 최대 880px 폭에
          양옆 24px. 화면마다 본문 시작선이 달라 보이던 것을 맞춘다. */}
      <main className="mx-auto w-full max-w-[880px] flex-1 px-6 pt-[clamp(28px,4vw,44px)] pb-16 max-[640px]:pt-8">
        <header className="flex items-end justify-between gap-6 pb-10 max-[640px]:flex-col max-[640px]:items-start max-[640px]:gap-4 max-[640px]:pb-8">
          <h1 className="page-title m-0 text-[clamp(36px,6vw,64px)] leading-[0.82] font-bold tracking-[-0.04em] text-[var(--problem-list-acid)]">
            문제 목록
          </h1>
          <IconLegend />
        </header>

        <section
          className="border-y border-t-[var(--problem-list-text)] border-b-[var(--problem-list-border)] py-[15px]"
          aria-label="문제 목록 정보"
        >
          {/* 랭킹 페이지의 "RANKED SUBMISSIONS / 00" 줄과 같은 글자 크기·간격을 쓴다. */}
          {categories.length === 0 ? (
            <div className="whitespace-nowrap text-[13px] font-bold tracking-[0.06em] text-[var(--problem-list-muted)]">
              전체 문제 /{' '}
              {String(problems.length).padStart(2, '0')}
            </div>
          ) : (
            /* 분류가 하나도 없는 응답(type·language 미포함)이면 위의 개수 줄로 물러난다.
               탭은 실제로 존재하는 분류만 세운다 — 결과가 0건인 탭은 만들지 않는다. */
            <div
              aria-label="문제 분류"
              className="flex flex-wrap items-center gap-x-6 gap-y-2"
              role="group"
            >
              <CategoryTab
                count={problems.length}
                label="전체"
                selected={activeCategory === null}
                onSelect={() => selectCategory(null)}
              />
              {categories.map((key) => (
                <CategoryTab
                  count={categoryCounts.get(key) ?? 0}
                  key={key}
                  label={key.toUpperCase()}
                  selected={activeCategory === key}
                  onSelect={() => selectCategory(key)}
                />
              ))}
            </div>
          )}
        </section>

        {isLoading && (
          <div className="border-b border-[var(--problem-list-border)] py-12 font-mono text-xs leading-[1.7] text-[var(--problem-list-muted)]">
            문제 목록을 불러오는 중입니다…
          </div>
        )}

        {errorMessage && (
          <div className="border-b border-[var(--problem-list-border)] py-12 font-mono text-xs leading-[1.7] text-[#ff786b]">
            {errorMessage}
          </div>
        )}

        {!isLoading && !errorMessage && problems.length === 0 && (
          <div className="border-b border-[var(--problem-list-border)] py-12 font-mono text-xs leading-[1.7] text-[var(--problem-list-muted)]">
            등록된 문제가 없습니다.
          </div>
        )}

        {!isLoading && !errorMessage && problems.length > 0 && (
          <>
            <section
              className="border-b border-[var(--problem-list-border)]"
              aria-label="문제 목록"
            >
            {visibleProblems.map((problem) => (
              <Link
                className="group grid min-h-18 grid-cols-[56px_minmax(0,1fr)_auto_auto] items-center gap-4 border-t border-[var(--problem-list-border)] py-3 text-inherit no-underline transition-[padding,background,color] duration-200 first:border-t-0 hover:bg-[var(--problem-list-hover-bg)] hover:px-3.5 hover:text-[#090909] focus-visible:bg-[var(--problem-list-hover-bg)] focus-visible:px-3.5 focus-visible:text-[#090909] focus-visible:outline-none max-[640px]:grid-cols-[42px_minmax(0,1fr)_auto_auto] max-[640px]:gap-3"
                key={problem.id}
                to={`/problems/${problem.id}`}
              >
                <span className="font-mono text-[16px] font-normal text-[var(--problem-list-muted)] transition-colors duration-200 group-hover:text-[#090909] group-focus-visible:text-[#090909]">
                  {String(problem.id).padStart(2, '0')}
                </span>
                <span className="min-w-0 text-[clamp(16px,1.5vw,20px)] font-bold tracking-[-0.03em] [word-break:keep-all]">
                  {problem.title}
                </span>
                <ProblemBadge problem={problem} />
                <span
                  className="justify-self-end text-2xl text-[var(--problem-list-acid)] transition-[color,transform] duration-200 group-hover:translate-x-[3px] group-hover:-translate-y-[3px] group-hover:text-[#090909] group-focus-visible:translate-x-[3px] group-focus-visible:-translate-y-[3px] group-focus-visible:text-[#090909]"
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

/**
 * 모아보기 탭 하나. 고른 상태를 밑줄로 표시한다 — 릴레이 결과 화면의 턴 탭 바와 같은
 * 문법이다. 안 고른 탭도 같은 두께의 투명 밑줄을 깔아, 고를 때 줄 높이가 안 흔들린다.
 */
function CategoryTab({
  count,
  label,
  onSelect,
  selected,
}: {
  count: number;
  label: string;
  onSelect: () => void;
  selected: boolean;
}) {
  return (
    <button
      aria-pressed={selected}
      className={`cursor-pointer border-0 border-b-2 bg-transparent p-0 pb-1 font-mono text-[15px] font-bold tracking-[0.06em] whitespace-nowrap transition-colors duration-200 focus-visible:outline-none ${
        selected
          ? 'border-b-[var(--problem-list-acid)] text-[var(--problem-list-acid)]'
          : 'border-b-transparent text-[var(--problem-list-muted)] hover:text-[var(--problem-list-acid)] focus-visible:text-[var(--problem-list-acid)]'
      }`}
      onClick={onSelect}
      type="button"
    >
      {label} {String(count).padStart(2, '0')}
    </button>
  );
}

/**
 * 문제의 성격 아이콘. game 문제는 게임 아이콘 하나로(게임이라는 사실이 언어보다
 * 중요하다), 그 외에는 풀이 언어 아이콘으로 표시한다. 아이콘이 없는 언어는 텍스트
 * 칩으로 물러난다 — 새 언어가 추가됐을 때 빈칸보다 낫다.
 * 목록 요약에 type·language가 아예 없으면(두 필드를 싣지 않는 BE 응답) 배지 자리를 비운다.
 *
 * 아이콘은 currentColor를 따르는 인라인 SVG(ProblemIcons)라, 글자색만 바꾸면
 * 라이트/다크 모드와 행 hover 반전(라임 배경 → 검정 아이콘)이 전부 함께 맞는다.
 */
const PROBLEM_ICONS: Record<
  string,
  { Icon: ComponentType<SVGProps<SVGSVGElement>>; label: string }
> = {
  game: { Icon: GameIcon, label: '게임 문제' },
  java: { Icon: JavaIcon, label: 'Java 문제' },
  python: { Icon: PythonIcon, label: 'Python 문제' },
};

/**
 * 아이콘 범례. 목록 구분선 위 오른쪽 끝에 앉아 각 행의 아이콘이 무엇을 뜻하는지
 * 알려준다. 라벨은 리스트의 대문자 모노 표기를 따른다.
 */
function IconLegend() {
  return (
    <div
      aria-label="아이콘 범례"
      className="flex shrink-0 items-center gap-4 font-mono text-[12px] font-bold tracking-[0.08em] text-[var(--problem-list-muted)]"
    >
      {[
        { Icon: JavaIcon, label: 'JAVA' },
        { Icon: PythonIcon, label: 'PYTHON' },
        { Icon: GameIcon, label: 'GAME' },
      ].map(({ Icon, label }) => (
        <span className="inline-flex items-center gap-1.5" key={label}>
          <Icon className="h-5 w-5" />
          {label}
        </span>
      ))}
    </div>
  );
}

function ProblemBadge({ problem }: { problem: ProblemSummary }) {
  // game 문제는 언어 대신 게임 아이콘 키로 흘린다 — 게임이라는 사실이 언어보다 중요하다.
  const iconKey = problem.type === 'game' ? 'game' : problem.language;

  // type·language를 싣지 않는 BE 응답이면 둘 다 없다. 모르는 문제에 배지를
  // 지어내는 대신 자리를 비운다 — 아래 칩 폴백은 "아는데 아이콘만 없는 언어"용이다.
  if (iconKey === undefined) {
    return null;
  }

  const known = PROBLEM_ICONS[iconKey.toLowerCase()];

  if (!known) {
    return (
      <span className="justify-self-end border border-[var(--problem-list-border)] px-1.5 py-0.5 font-mono text-[10px] font-bold tracking-[0.08em] whitespace-nowrap text-[var(--problem-list-muted)] transition-colors duration-200 group-hover:border-[#090909] group-hover:text-[#090909] group-focus-visible:border-[#090909] group-focus-visible:text-[#090909]">
        {iconKey.toUpperCase()}
      </span>
    );
  }

  return (
    <span
      aria-label={known.label}
      className="justify-self-end text-[var(--problem-list-muted)] transition-colors duration-200 group-hover:text-[#090909] group-focus-visible:text-[#090909]"
      role="img"
      title={known.label}
    >
      <known.Icon className="block h-5 w-5" />
    </span>
  );
}
