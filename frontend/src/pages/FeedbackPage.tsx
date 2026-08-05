import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';

import { getAttempt, getAttemptFeedback } from '../features/attempt/api';
import type { Attempt, AttemptFeedback } from '../features/attempt/types';
import { useAuth } from '../features/auth/AuthContext';
import PromptFeedback from '../features/feedback/PromptFeedback';
import { useTheme } from '../features/theme/ThemeContext';
import CodeViewer from '../features/workspace/CodeViewer';
import { nextTabIndex } from '../shared/a11y/tabKeyboard';
import { ApiError, API_ERROR_CODES, isAbortError } from '../shared/api/apiClient';
import Button from '../shared/components/Button';
import Footer from '../shared/components/Footer';
import Header from '../shared/components/Header';

/**
 * 피드백을 열지 못했을 때 화면에 남길 안내. 아직 제출하지 않은 어템프트처럼
 * 오류가 아닌 경우도 있어, 색과 돌아갈 곳을 함께 담는다.
 */
interface LoadNotice {
  message: string;
  actionLabel: string;
  actionTo: string;
  isError: boolean;
}

/**
 * 같은 턴을 읽는 두 렌즈. 프롬프트 코치는 프롬프트가 무엇을 전달했는지 보고,
 * pattern은 그 턴에 일한 방식에 이름을 붙인다.
 */
type FeedbackLens = 'feedback' | 'pattern';

const LENS_TABS: { value: FeedbackLens; label: string }[] = [
  { value: 'feedback', label: 'FEEDBACK' },
  { value: 'pattern', label: 'PATTERN' },
];

/**
 * 화면이 쓰는 pattern 피드백 한 벌. 응답에서는 자리마다 null일 수 있지만 여기까지 온 것은
 * 총평과 모든 턴이 다 찬 것뿐이라, 렌더 자리에서 다시 null을 묻지 않는다.
 */
interface PatternFeedback {
  overallMd: string;
  /** 턴 번호 → 그 턴의 pattern 본문. 정규화가 모든 턴을 채웠으므로 빠진 번호가 없다. */
  turnMd: Record<number, string>;
}

/**
 * pattern은 전부 있거나 전부 없다 — 제출이 두 스타일을 함께 저장하거나 아예 실패하기 때문이다.
 * 그 계약을 화면 밖 경계에서 한 번 확인하고, 어긋나면 pattern을 통째로 없는 것으로 본다.
 *
 * <p>반쪽을 그리면 사용자는 설명 없는 빈 칸을 본다. 그 칸이 pattern 도입 전 제출이라 비어 있는
 * 것인지 생성이 반쯤 실패한 것인지 화면으로는 구분할 수 없어, 없는 쪽으로 모아 둔다.
 */
function toPatternFeedback(
  feedback: AttemptFeedback | null,
): PatternFeedback | null {
  const overallMd = feedback?.patternOverallMd ?? null;

  if (feedback === null || overallMd === null) {
    return null;
  }

  const turnMd: Record<number, string> = {};

  for (const turn of feedback.turns) {
    if (turn.patternMd === null) {
      // 계약이 깨진 자리다. 화면은 조용히 접지만 원인은 남긴다.
      console.warn(`턴 ${turn.turn}에 pattern 피드백이 없어 pattern 자리를 숨깁니다.`);
      return null;
    }

    turnMd[turn.turn] = turn.patternMd;
  }

  return { overallMd, turnMd };
}

/**
 * 세션 전체를 다루는 총평 한 벌. 렌즈마다 하나씩 쌓이므로 테두리와 제목 줄을 여기서만 그린다.
 */
function OverallPanel({
  markdown,
  title,
}: {
  markdown: string;
  title: string;
}) {
  return (
    <section className="mt-4 border border-[var(--feedback-border)] bg-transparent">
      <div className="flex min-h-[58px] items-center border-b border-[var(--feedback-border)] px-6 max-[760px]:px-5">
        <h2 className="m-0 font-mono text-xl leading-[1.4] font-bold tracking-[0.08em] text-[var(--feedback-acid)]">
          {title}
        </h2>
      </div>
      <div className="px-6 pb-7 [&>div>h2:first-child]:border-t-0 [&>div>h2:first-child]:pt-0 max-[760px]:px-5 max-[760px]:pb-5">
        <PromptFeedback feedback={markdown} />
      </div>
    </section>
  );
}

/**
 * 제출된 어템프트의 프롬프트 피드백 화면.
 *
 * 주소(/attempts/{id}/feedback)에 담긴 ID만 보고 서버에서
 * 피드백(GET /api/attempts/{id}/feedback)을 가져온다 — 브라우저 저장소에 기대지
 * 않으므로 새로고침해도 남고, 링크를 그대로 열어도 동작한다.
 * 프롬프트 원문은 피드백 응답에 없으므로 어템프트도 함께 조회해 짝지어 보여준다.
 */
export default function FeedbackPage() {
  const { colorMode } = useTheme();
  const { attemptId: attemptIdParam } = useParams();
  const attemptId = Number(attemptIdParam);
  const location = useLocation();
  const navigate = useNavigate();
  const { refresh } = useAuth();

  const [attempt, setAttempt] = useState<Attempt | null>(null);
  const [feedback, setFeedback] = useState<AttemptFeedback | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [notice, setNotice] = useState<LoadNotice | null>(null);
  const [selectedTurn, setSelectedTurn] = useState(1);
  // 렌즈는 턴 선택과 독립이다 — pattern을 읽던 사람이 턴을 넘겨도 pattern을 계속 읽는다.
  const [lens, setLens] = useState<FeedbackLens>('feedback');
  const turnNavRef = useRef<HTMLDivElement>(null);
  // 방향키가 옮긴 선택을 눈이 따라가려면 포커스도 같이 옮겨야 한다.
  const turnTabRefs = useRef<
    Partial<Record<number, HTMLButtonElement | null>>
  >({});
  const lensTabRefs = useRef<Record<FeedbackLens, HTMLButtonElement | null>>({
    feedback: null,
    pattern: null,
  });

  useEffect(() => {
    if (!Number.isInteger(attemptId) || attemptId <= 0) {
      setNotice({
        message: '잘못된 어템프트 주소입니다.',
        actionLabel: 'BACK TO PROBLEMS ↗',
        actionTo: '/problems',
        isError: true,
      });
      setIsLoading(false);
      return;
    }

    const controller = new AbortController();
    const { signal } = controller;

    void (async () => {
      try {
        const [loadedAttempt, loadedFeedback] = await Promise.all([
          getAttempt(attemptId, signal),
          getAttemptFeedback(attemptId, signal),
        ]);

        setAttempt(loadedAttempt);
        setFeedback(loadedFeedback);
        setSelectedTurn(loadedFeedback.turns[0]?.turn ?? 1);
      } catch (error: unknown) {
        if (isAbortError(error)) return;

        if (
          error instanceof ApiError &&
          error.code === API_ERROR_CODES.unauthenticated
        ) {
          await refresh();
          if (signal.aborted) return;

          navigate('/login', {
            replace: true,
            state: { from: location.pathname },
          });
          return;
        }

        // 제출 전이면 피드백이 아직 없다 — 오류가 아니라 작업장으로 돌아가라는 안내다.
        if (
          error instanceof ApiError &&
          error.code === API_ERROR_CODES.feedbackNotFound
        ) {
          setNotice({
            message: '아직 제출되지 않은 어템프트입니다.',
            actionLabel: 'BACK TO WORKSPACE ↗',
            actionTo: `/attempts/${attemptId}`,
            isError: false,
          });
          return;
        }

        setNotice({
          message:
            error instanceof ApiError
              ? error.message
              : '피드백을 불러오지 못했습니다.',
          actionLabel: 'BACK TO PROBLEMS ↗',
          actionTo: '/problems',
          isError: true,
        });
      } finally {
        // 뒤늦게 끊긴 요청이 새 로드의 로딩 표시를 끄지 않도록 한다.
        if (!signal.aborted) setIsLoading(false);
      }
    })();

    return () => {
      controller.abort();
    };
  }, [attemptId, location.pathname, navigate, refresh]);

  const turnSections = (feedback?.turns ?? []).map((turnFeedback) => ({
    ...turnFeedback,
    prompt: attempt?.turns[turnFeedback.turn - 1]?.prompt ?? '',
    changedFiles: attempt?.turns[turnFeedback.turn - 1]?.changedFiles ?? [],
  }));
  const selectedSection =
    turnSections.find((section) => section.turn === selectedTurn) ??
    turnSections[0];
  // 판정은 여기 한 번뿐이다. 아래로는 pattern이 있거나(모든 자리가 찼거나) 없거나 둘뿐이다.
  const pattern = toPatternFeedback(feedback);

  const scrollTurnNav = (direction: -1 | 1) => {
    turnNavRef.current?.scrollBy({
      left: direction * turnNavRef.current.clientWidth * 0.7,
      behavior: 'smooth',
    });
  };

  const handleTurnTabKeyDown = (
    event: KeyboardEvent<HTMLButtonElement>,
    currentTurn: number,
  ) => {
    const currentIndex = turnSections.findIndex(
      (section) => section.turn === currentTurn,
    );
    const targetIndex = nextTabIndex(
      event.key,
      currentIndex,
      turnSections.length,
    );

    if (targetIndex === null) return;

    event.preventDefault();
    const nextTurn = turnSections[targetIndex].turn;
    setSelectedTurn(nextTurn);
    // 가로로 스크롤되는 탭 줄이라, 포커스가 옮겨 가면 브라우저가 보이는 자리까지 끌어온다.
    turnTabRefs.current[nextTurn]?.focus();
  };

  const handleLensTabKeyDown = (
    event: KeyboardEvent<HTMLButtonElement>,
    currentLens: FeedbackLens,
  ) => {
    const currentIndex = LENS_TABS.findIndex((tab) => tab.value === currentLens);
    const targetIndex = nextTabIndex(event.key, currentIndex, LENS_TABS.length);

    if (targetIndex === null) return;

    event.preventDefault();
    const nextLens = LENS_TABS[targetIndex].value;
    setLens(nextLens);
    lensTabRefs.current[nextLens]?.focus();
  };

  return (
    <div
      className="feedback-page flex min-h-screen min-w-80 flex-col bg-[var(--feedback-bg)] text-[var(--feedback-text)] [font-family:Arial,'Noto_Sans_KR',sans-serif]"
      data-color-mode={colorMode}
    >
      <Header mobileBreakpoint="760" />

      <main className="mx-auto w-[calc(100%_-_10vw)] max-w-[1840px] flex-1 pt-[clamp(32px,5vw,56px)] pb-20 max-[760px]:w-[min(calc(100%_-_32px),680px)] max-[760px]:pt-8">
        <section className="flex items-center justify-between gap-[18px] bg-[var(--feedback-acid)] px-[22px] py-5 text-[#090909] max-[760px]:flex-col max-[760px]:items-start">
          <h1 className="m-0 font-mono text-[clamp(36px,6vw,64px)] leading-[0.82] font-bold tracking-[-0.04em]">
            SESSION FEEDBACK
          </h1>
          <span className="text-right font-mono text-sm leading-[1.5] font-bold tracking-[0.05em] max-[760px]:text-left">
            INTENT RECONSTRUCTION
            <br />+ WORK PATTERNS
          </span>
        </section>

        {isLoading && (
          <section className="border-b border-[var(--feedback-border)] py-12 font-mono text-xs leading-[1.7] text-[var(--feedback-muted)]">
            피드백을 불러오는 중입니다...
          </section>
        )}

        {!isLoading && notice && (
          <section
            className={[
              'mt-4 border p-8 text-sm leading-[1.7]',
              notice.isError
                ? 'border-[#ff786b] text-[#ff786b]'
                : 'border-[var(--feedback-border)] text-[var(--feedback-muted)]',
            ].join(' ')}
          >
            <div>{notice.message}</div>
            <Button className="feedback-page-primary-action mt-5" to={notice.actionTo}>
              {notice.actionLabel}
            </Button>
          </section>
        )}

        {!isLoading && !notice && feedback && (
          <>
            <OverallPanel markdown={feedback.overallMd} title="OVERALL.MD" />

            {pattern && (
              <OverallPanel markdown={pattern.overallMd} title="PATTERN.MD" />
            )}

            {selectedSection && (
              <section
                className="mt-4 border border-[var(--feedback-border)] bg-[var(--feedback-surface)]"
                aria-label="턴별 프롬프트 피드백"
              >
                <div className="relative border-b border-[var(--feedback-border)]">
                  <span className="absolute top-0 bottom-0 left-0 z-20 grid w-[220px] place-items-center border-r border-[var(--feedback-border)] bg-[var(--feedback-surface)] font-mono text-xl leading-[1.4] font-bold tracking-[0.08em] text-[var(--feedback-acid)] max-[760px]:hidden">
                    PROMPT HISTORY
                  </span>
                  <button
                    aria-label="이전 턴 보기"
                    className="absolute top-0 bottom-0 left-[220px] z-20 w-10 cursor-pointer border-0 border-r border-[var(--feedback-border)] bg-[var(--feedback-surface)] font-mono text-2xl font-bold text-[var(--feedback-acid)] hover:bg-[var(--feedback-surface-hover)] focus-visible:outline-2 focus-visible:outline-[var(--feedback-acid)] focus-visible:outline-offset-[-3px] max-[760px]:left-0"
                    onClick={() => scrollTurnNav(-1)}
                    type="button"
                  >
                    ‹
                  </button>
                  <div
                    aria-label="턴 선택"
                    className="turn-tab-scrollbar flex items-stretch overflow-x-auto pr-12 pl-[260px] scroll-smooth max-[760px]:px-[42px]"
                    ref={turnNavRef}
                    role="tablist"
                  >
                    {turnSections.map((section) => {
                      const isSelected = section.turn === selectedSection.turn;

                      return (
                        <button
                          aria-controls="feedback-turn-panel"
                          aria-selected={isSelected}
                          className={`relative min-h-[58px] min-w-[130px] shrink-0 cursor-pointer border-0 bg-transparent px-[22px] font-mono text-sm font-bold tracking-[0.06em] hover:text-[var(--feedback-text)] focus-visible:outline-2 focus-visible:outline-[var(--feedback-acid)] focus-visible:outline-offset-[-4px] after:absolute after:right-3.5 after:-bottom-px after:left-3.5 after:z-10 after:h-[3px] ${
                            isSelected
                              ? 'text-[var(--feedback-acid)] after:bg-[var(--feedback-acid)]'
                              : 'text-[var(--feedback-muted)] after:bg-transparent'
                          } max-[760px]:min-w-[110px] max-[760px]:px-3.5`}
                          id={`feedback-turn-tab-${section.turn}`}
                          key={section.turn}
                          onClick={() => setSelectedTurn(section.turn)}
                          onKeyDown={(event) =>
                            handleTurnTabKeyDown(event, section.turn)
                          }
                          ref={(node) => {
                            turnTabRefs.current[section.turn] = node;
                          }}
                          role="tab"
                          tabIndex={isSelected ? 0 : -1}
                          type="button"
                        >
                          TURN {String(section.turn).padStart(2, '0')}
                        </button>
                      );
                    })}
                  </div>
                  <button
                    aria-label="다음 턴 보기"
                    className={[
                      'absolute top-0 right-0 bottom-0 z-20 w-10 border-0 border-l border-[var(--feedback-border)] bg-[var(--feedback-surface)] font-mono text-2xl font-bold focus-visible:outline-2 focus-visible:outline-[var(--feedback-acid)] focus-visible:outline-offset-[-3px]',
                      turnSections.length >= 8
                        ? 'cursor-pointer text-[var(--feedback-acid)] hover:bg-[var(--feedback-surface-hover)]'
                        : 'cursor-not-allowed text-[var(--feedback-subtle)]',
                    ].join(' ')}
                    disabled={turnSections.length < 8}
                    onClick={() => scrollTurnNav(1)}
                    type="button"
                  >
                    ›
                  </button>
                </div>

                <div
                  aria-labelledby={`feedback-turn-tab-${selectedSection.turn}`}
                  className="grid grid-cols-[minmax(0,1fr)_minmax(360px,1fr)] divide-x divide-[var(--feedback-border)] max-[760px]:grid-cols-1 max-[760px]:divide-x-0 max-[760px]:divide-y"
                  id="feedback-turn-panel"
                  role="tabpanel"
                  tabIndex={0}
                >
                  <article className="min-w-0 p-[22px]">
                    <h2 className="m-0 font-mono text-lg leading-[1.4] font-bold tracking-[0.08em] text-[var(--feedback-acid)]">
                      TURN {String(selectedSection.turn).padStart(2, '0')} GENERATED CODE
                    </h2>
                    {selectedSection.changedFiles.length > 0 ? (
                      <div className="mt-[18px] grid gap-3">
                        {selectedSection.changedFiles.map((file) => (
                          <div
                            className="overflow-hidden border border-[var(--feedback-border)] bg-[var(--feedback-code-bg)]"
                            key={file.path}
                          >
                            <div className="border-b border-[var(--feedback-border)] px-4 py-3 font-mono text-xs font-bold text-[var(--feedback-muted)]">
                              {file.path}
                            </div>
                            <div className="workspace-scrollbar min-h-[280px] max-w-full overflow-auto max-[760px]:min-h-[220px]">
                              <CodeViewer
                                code={file.content ?? '(deleted)'}
                                path={file.path}
                              />
                            </div>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <div className="mt-[18px] grid min-h-[280px] place-items-center border border-[var(--feedback-border)] bg-[var(--feedback-code-bg)] p-[18px] font-mono text-xs text-[var(--feedback-subtle)]">
                        NO CHANGED FILES
                      </div>
                    )}
                  </article>

                  <div className="grid min-w-0 grid-rows-[auto_1fr] divide-y divide-[var(--feedback-border)]">
                    <article className="min-w-0 p-[22px]">
                      <h2 className="m-0 font-mono text-lg leading-[1.4] font-bold tracking-[0.08em] text-[var(--feedback-acid)]">
                        TURN {String(selectedSection.turn).padStart(2, '0')} USER PROMPT
                      </h2>
                      <p className="mt-[18px] min-h-[110px] whitespace-pre-wrap border border-[var(--feedback-border)] bg-[var(--feedback-prompt-bg)] p-[18px] font-mono text-[15px] leading-[1.9] text-[var(--feedback-code-text)] [word-break:keep-all] max-[760px]:min-h-40">
                        {selectedSection.prompt || '(프롬프트 원문을 불러오지 못했습니다.)'}
                      </p>
                    </article>

                    <article className="min-w-0 p-[22px]">
                      {pattern ? (
                        // 제목 둘이 이미 턴 번호를 말하므로 탭에는 넣지 않는다.
                        <div
                          aria-label="피드백 렌즈"
                          className="flex items-center gap-6"
                          role="tablist"
                        >
                          {LENS_TABS.map((tab) => {
                            const isSelected = tab.value === lens;

                            return (
                              <button
                                aria-controls="feedback-lens-panel"
                                aria-selected={isSelected}
                                className={`relative cursor-pointer border-0 bg-transparent p-0 pb-2 font-mono text-lg leading-[1.4] font-bold tracking-[0.08em] hover:text-[var(--feedback-text)] focus-visible:outline-2 focus-visible:outline-[var(--feedback-acid)] focus-visible:outline-offset-[-4px] after:absolute after:right-0 after:bottom-0 after:left-0 after:h-[3px] ${
                                  isSelected
                                    ? 'text-[var(--feedback-acid)] after:bg-[var(--feedback-acid)]'
                                    : 'text-[var(--feedback-muted)] after:bg-transparent'
                                }`}
                                id={`feedback-lens-${tab.value}`}
                                key={tab.value}
                                onClick={() => setLens(tab.value)}
                                onKeyDown={(event) =>
                                  handleLensTabKeyDown(event, tab.value)
                                }
                                ref={(node) => {
                                  lensTabRefs.current[tab.value] = node;
                                }}
                                role="tab"
                                tabIndex={isSelected ? 0 : -1}
                                type="button"
                              >
                                {tab.label}
                              </button>
                            );
                          })}
                        </div>
                      ) : (
                        <h2 className="m-0 font-mono text-lg leading-[1.4] font-bold tracking-[0.08em] text-[var(--feedback-acid)]">
                          TURN {String(selectedSection.turn).padStart(2, '0')} FEEDBACK
                        </h2>
                      )}
                      {/* 탭 줄이 라벨하는 패널이므로 탭 줄 밖에 둔다 — 패널이 자기 탭을 품으면
                          라벨 참조가 자기 안을 가리킨다. */}
                      <div
                        aria-labelledby={
                          pattern ? `feedback-lens-${lens}` : undefined
                        }
                        id={pattern ? 'feedback-lens-panel' : undefined}
                        role={pattern ? 'tabpanel' : undefined}
                        tabIndex={pattern ? 0 : undefined}
                      >
                        <PromptFeedback
                          feedback={
                            // 탭 바가 없으면 lens는 feedback에 머문다.
                            pattern && lens === 'pattern'
                              ? pattern.turnMd[selectedSection.turn]
                              : selectedSection.feedbackMd
                          }
                        />
                      </div>
                    </article>
                  </div>
                </div>
              </section>
            )}

            <div className="mt-5 flex justify-end gap-3 max-[760px]:flex-col max-[760px]:justify-stretch">
              {attempt && (
                <Button

                  className="max-[760px]:w-full"
                  to={`/ranking?problem=${attempt.problemId}`}
                  variant="secondary"
                >
                  <span className="text-[14px]">이 문제 랭킹 보기 ↗</span>
                </Button>
              )}
              {attempt && (
                <Button
                  className="max-[760px]:w-full"
                  to={`/problems/${attempt.problemId}`}
                >
                  <span className="text-[14px]">이전 문제로 돌아가기 ↗</span>
                </Button>
              )}
            </div>
          </>
        )}
      </main>
      <Footer />
    </div>
  );
}
