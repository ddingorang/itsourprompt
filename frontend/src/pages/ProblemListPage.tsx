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

export default function ProblemListPage() {
  const { colorMode } = useTheme();
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
    <div
      className="problem-list-page flex min-h-screen flex-col bg-[var(--problem-list-bg)] text-[var(--problem-list-text)] [font-family:Arial,'Noto_Sans_KR',sans-serif]"
      data-color-mode={colorMode}
    >
      <Header />

      <main className="mx-auto w-[min(calc(90%_-_360px),1040px)] flex-1 pt-[clamp(28px,4vw,44px)] pb-16 max-[900px]:w-[calc(100%_-_64px)] max-[640px]:w-[calc(100%_-_32px)] max-[640px]:pt-8">
        <header className="flex items-end justify-between gap-6 pb-10 max-[640px]:flex-col max-[640px]:items-start max-[640px]:gap-4 max-[640px]:pb-8">
          <h1 className="m-0 text-[clamp(36px,6vw,64px)] leading-[0.82] font-bold tracking-[-0.04em] text-[var(--problem-list-acid)]">
            문제 목록
          </h1>
          <IconLegend />
        </header>

        <section
          className="border-y border-t-[var(--problem-list-text)] border-b-[var(--problem-list-border)] py-[15px]"
          aria-label="문제 목록 정보"
        >
          <div className="whitespace-nowrap text-[11px] font-bold text-[var(--problem-list-muted)]">
            전체 문제 / {String(problems.length).padStart(2, '0')}
          </div>
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
                className="group grid min-h-18 grid-cols-[56px_minmax(0,1fr)_auto_auto] items-center gap-4 border-t border-[var(--problem-list-border)] py-3 text-inherit no-underline transition-[padding,background,color] duration-200 first:border-t-0 hover:bg-[var(--problem-list-acid)] hover:px-3.5 hover:text-[#090909] focus-visible:bg-[var(--problem-list-acid)] focus-visible:px-3.5 focus-visible:text-[#090909] focus-visible:outline-none max-[640px]:grid-cols-[42px_minmax(0,1fr)_auto_auto] max-[640px]:gap-3"
                key={problem.id}
                to={`/problems/${problem.id}`}
              >
                <span className="font-mono text-[17px] text-[var(--problem-list-muted)] transition-colors duration-200 group-hover:text-[#090909] group-focus-visible:text-[#090909]">
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
