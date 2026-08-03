import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';

import { getAttempt, getAttemptFeedback } from '../features/attempt/api';
import type { Attempt, AttemptFeedback } from '../features/attempt/types';
import { useAuth } from '../features/auth/AuthContext';
import PromptFeedback from '../features/feedback/PromptFeedback';
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
 * 제출된 어템프트의 프롬프트 피드백 화면.
 *
 * 주소(/attempts/{id}/feedback)에 담긴 ID만 보고 서버에서
 * 피드백(GET /api/attempts/{id}/feedback)을 가져온다 — 브라우저 저장소에 기대지
 * 않으므로 새로고침해도 남고, 링크를 그대로 열어도 동작한다.
 * 프롬프트 원문은 피드백 응답에 없으므로 어템프트도 함께 조회해 짝지어 보여준다.
 */
export default function FeedbackPage() {
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
  const turnNavRef = useRef<HTMLDivElement>(null);

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

  const scrollTurnNav = (direction: -1 | 1) => {
    turnNavRef.current?.scrollBy({
      left: direction * turnNavRef.current.clientWidth * 0.7,
      behavior: 'smooth',
    });
  };

  return (
    <div className="flex min-h-screen min-w-80 flex-col bg-[#090909] text-[#f5f5ef] [font-family:Arial,'Noto_Sans_KR',sans-serif]">
      <Header mobileBreakpoint="760" />

      <main className="mx-auto w-[calc(100%_-_10vw)] max-w-[1840px] flex-1 pt-[clamp(32px,5vw,56px)] pb-20 max-[760px]:w-[min(calc(100%_-_32px),680px)] max-[760px]:pt-8">
        <section className="flex items-center justify-between gap-[18px] bg-[#d6ff50] px-[22px] py-5 text-[#090909] max-[760px]:flex-col max-[760px]:items-start">
          <h1 className="m-0 font-mono text-[clamp(36px,6vw,64px)] leading-[0.82] font-bold tracking-[-0.04em]">
            PROMPT FEEDBACK
          </h1>
          <span className="text-right font-mono text-sm leading-[1.5] font-bold tracking-[0.05em] max-[760px]:text-left">
            INTENT RECONSTRUCTION
            <br />+ SUGGESTIONS
          </span>
        </section>

        {isLoading && (
          <section className="border-b border-[#343434] py-12 font-mono text-xs leading-[1.7] text-[#a3a3a3]">
            피드백을 불러오는 중입니다...
          </section>
        )}

        {!isLoading && notice && (
          <section
            className={[
              'mt-4 border p-8 text-sm leading-[1.7]',
              notice.isError
                ? 'border-[#ff786b] text-[#ff786b]'
                : 'border-[#343434] text-[#a3a3a3]',
            ].join(' ')}
          >
            <div>{notice.message}</div>
            <Button className="mt-5" to={notice.actionTo}>
              {notice.actionLabel}
            </Button>
          </section>
        )}

        {!isLoading && !notice && feedback && (
          <>
            <section className="mt-4 border border-[#d6ff50] bg-transparent">
              <div className="flex min-h-[58px] items-center border-b border-[#393939] px-6 max-[760px]:px-5">
                <h2 className="m-0 font-mono text-xl leading-[1.4] font-bold tracking-[0.08em] text-[#d6ff50]">
                  OVERALL.MD
                </h2>
              </div>
              <div className="px-6 pb-7 [&>div>h2:first-child]:border-t-0 [&>div>h2:first-child]:pt-0 max-[760px]:px-5 max-[760px]:pb-5">
                <PromptFeedback feedback={feedback.overallMd} />
              </div>
            </section>

            {selectedSection && (
              <section
                className="mt-4 border border-[#d6ff50] bg-[#121212]"
                aria-label="턴별 프롬프트 피드백"
              >
                <div className="relative border-b border-[#393939]">
                  <span className="absolute top-0 bottom-0 left-0 z-20 grid w-[220px] place-items-center border-r border-[#393939] bg-[#121212] font-mono text-xl leading-[1.4] font-bold tracking-[0.08em] text-[#d6ff50] max-[760px]:hidden">
                    PROMPT HISTORY
                  </span>
                  <button
                    aria-label="이전 턴 보기"
                    className="absolute top-0 bottom-0 left-[220px] z-20 w-10 cursor-pointer border-0 border-r border-[#393939] bg-[#121212] font-mono text-2xl font-bold text-[#d6ff50] hover:bg-[#202020] focus-visible:outline-2 focus-visible:outline-[#d6ff50] focus-visible:outline-offset-[-3px] max-[760px]:left-0"
                    onClick={() => scrollTurnNav(-1)}
                    type="button"
                  >
                    ‹
                  </button>
                  <div
                    className="turn-tab-scrollbar flex items-stretch overflow-x-auto pr-12 pl-[260px] scroll-smooth max-[760px]:px-[42px]"
                    ref={turnNavRef}
                    role="tablist"
                  >
                    {turnSections.map((section) => {
                      const isSelected = section.turn === selectedSection.turn;

                      return (
                        <button
                          aria-selected={isSelected}
                          className={`relative min-h-[58px] min-w-[130px] shrink-0 cursor-pointer border-0 bg-transparent px-[22px] font-mono text-sm font-bold tracking-[0.06em] hover:text-[#f5f5ef] focus-visible:outline-2 focus-visible:outline-[#d6ff50] focus-visible:outline-offset-[-4px] after:absolute after:right-3.5 after:-bottom-px after:left-3.5 after:z-10 after:h-[3px] ${
                            isSelected
                              ? 'text-[#d6ff50] after:bg-[#d6ff50]'
                              : 'text-[#8f8f8f] after:bg-transparent'
                          } max-[760px]:min-w-[110px] max-[760px]:px-3.5`}
                          key={section.turn}
                          onClick={() => setSelectedTurn(section.turn)}
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
                      'absolute top-0 right-0 bottom-0 z-20 w-10 border-0 border-l border-[#393939] bg-[#121212] font-mono text-2xl font-bold focus-visible:outline-2 focus-visible:outline-[#d6ff50] focus-visible:outline-offset-[-3px]',
                      turnSections.length >= 8
                        ? 'cursor-pointer text-[#d6ff50] hover:bg-[#202020]'
                        : 'cursor-not-allowed text-[#666]',
                    ].join(' ')}
                    disabled={turnSections.length < 8}
                    onClick={() => scrollTurnNav(1)}
                    type="button"
                  >
                    ›
                  </button>
                </div>

                <div
                  className="grid grid-cols-[minmax(0,1fr)_minmax(360px,1fr)] divide-x divide-[#393939] max-[760px]:grid-cols-1 max-[760px]:divide-x-0 max-[760px]:divide-y"
                  role="tabpanel"
                >
                  <article className="min-w-0 p-[22px]">
                    <h2 className="m-0 font-mono text-lg leading-[1.4] font-bold tracking-[0.08em] text-[#d6ff50]">
                      TURN {String(selectedSection.turn).padStart(2, '0')} GENERATED CODE
                    </h2>
                    {selectedSection.changedFiles.length > 0 ? (
                      <div className="mt-[18px] grid gap-3">
                        {selectedSection.changedFiles.map((file) => (
                          <div
                            className="overflow-hidden border border-[#393939] bg-[#0d0d0d]"
                            key={file.path}
                          >
                            <div className="border-b border-[#393939] px-4 py-3 font-mono text-xs font-bold text-[#a3a3a3]">
                              {file.path}
                            </div>
                            <pre className="workspace-scrollbar m-0 min-h-[280px] max-w-full overflow-auto p-[18px] font-mono text-[13px] leading-[1.7] text-[#d8d8d2] max-[760px]:min-h-[220px]">
                              <code>{file.content ?? '(deleted)'}</code>
                            </pre>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <div className="mt-[18px] grid min-h-[280px] place-items-center border border-[#393939] bg-[#0d0d0d] p-[18px] font-mono text-xs text-[#777]">
                        NO CHANGED FILES
                      </div>
                    )}
                  </article>

                  <div className="grid min-w-0 grid-rows-[auto_1fr] divide-y divide-[#393939]">
                    <article className="min-w-0 p-[22px]">
                      <h2 className="m-0 font-mono text-lg leading-[1.4] font-bold tracking-[0.08em] text-[#d6ff50]">
                        TURN {String(selectedSection.turn).padStart(2, '0')} USER PROMPT
                      </h2>
                      <p className="mt-[18px] min-h-[110px] whitespace-pre-wrap border border-[#393939] bg-[#111] p-[18px] font-mono text-[15px] leading-[1.9] text-[#d0d0ca] [word-break:keep-all] max-[760px]:min-h-40">
                        {selectedSection.prompt || '(프롬프트 원문을 불러오지 못했습니다.)'}
                      </p>
                    </article>

                    <article className="min-w-0 p-[22px]">
                      <h2 className="m-0 font-mono text-lg leading-[1.4] font-bold tracking-[0.08em] text-[#d6ff50]">
                        TURN {String(selectedSection.turn).padStart(2, '0')} FEEDBACK
                      </h2>
                      <PromptFeedback feedback={selectedSection.feedbackMd} />
                    </article>
                  </div>
                </div>
              </section>
            )}

            <div className="mt-5 flex justify-end gap-3 max-[760px]:flex-col max-[760px]:justify-stretch">
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
