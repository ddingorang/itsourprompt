import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';

import { getAttempt, getAttemptFeedback } from '../features/attempt/api';
import type { Attempt, AttemptFeedback } from '../features/attempt/types';
import PromptFeedback from '../features/feedback/PromptFeedback';
import { ApiError } from '../shared/api/apiClient';
import Button from '../shared/components/Button';
import Footer from '../shared/components/Footer';
import Header from '../shared/components/Header';

/**
 * 제출된 어템프트의 프롬프트 피드백 화면.
 *
 * 피드백은 sessionStorage가 아니라 서버(GET /api/attempts/{id}/feedback)에서
 * 가져온다 — 새로고침해도 남고, 링크를 그대로 열어도 동작한다.
 * 프롬프트 원문은 피드백 응답에 없으므로 어템프트도 함께 조회해 짝지어 보여준다.
 */
export default function FeedbackPage() {
  const { attemptId: attemptIdParam } = useParams();
  const attemptId = Number(attemptIdParam);

  const [attempt, setAttempt] = useState<Attempt | null>(null);
  const [feedback, setFeedback] = useState<AttemptFeedback | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!Number.isInteger(attemptId) || attemptId <= 0) {
      setErrorMessage('잘못된 어템프트 주소입니다.');
      setIsLoading(false);
      return;
    }

    let isMounted = true;

    void (async () => {
      try {
        const [loadedAttempt, loadedFeedback] = await Promise.all([
          getAttempt(attemptId),
          getAttemptFeedback(attemptId),
        ]);

        if (!isMounted) return;
        setAttempt(loadedAttempt);
        setFeedback(loadedFeedback);
      } catch (error: unknown) {
        if (!isMounted) return;

        setErrorMessage(
          error instanceof ApiError
            ? error.message
            : '피드백을 불러오지 못했습니다.',
        );
      } finally {
        if (isMounted) setIsLoading(false);
      }
    })();

    return () => {
      isMounted = false;
    };
  }, [attemptId]);

  /** 턴별 피드백을 프롬프트 원문과 짝지운다. turn은 1부터 시작한다. */
  const turnSections = (feedback?.turns ?? []).map((turnFeedback) => ({
    ...turnFeedback,
    prompt: attempt?.turns[turnFeedback.turn - 1]?.prompt ?? '',
  }));

  return (
    <div className="flex min-h-screen min-w-80 flex-col bg-[#090909] text-[#f5f5ef] [font-family:Arial,'Noto_Sans_KR',sans-serif]">
      <Header mobileBreakpoint="760" />

      <main className="mx-auto w-[calc(100%_-_10vw)] flex-1 pt-[clamp(32px,5vw,56px)] pb-20 max-[760px]:w-[min(calc(100%_-_32px),680px)] max-[760px]:pt-8">
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
            피드백을 불러오는 중입니다…
          </section>
        )}

        {!isLoading && errorMessage && (
          <section className="mt-4 border border-[#ff786b] p-8 text-sm leading-[1.7] text-[#ff786b]">
            <div>{errorMessage}</div>
            <Button className="mt-5" to="/problems">
              BACK TO PROBLEMS ↗
            </Button>
          </section>
        )}

        {!isLoading && !errorMessage && feedback && (
          <>
            {turnSections.length > 0 && (
              <section className="mt-4 grid gap-4" aria-label="턴별 피드백">
                {turnSections.map((section) => (
                  <div
                    className="grid grid-cols-[minmax(260px,0.78fr)_minmax(0,1.22fr)] gap-4 max-[760px]:grid-cols-1"
                    key={section.turn}
                  >
                    <article className="min-w-0 bg-[#1b1b1b] p-[22px]">
                      <div className="font-mono text-xl leading-[1.4] font-bold tracking-[0.08em] text-[#d6ff50]">
                        TURN {String(section.turn).padStart(2, '0')} PROMPT
                      </div>
                      <p className="mt-[18px] min-h-[160px] whitespace-pre-wrap border border-[#393939] bg-[#111] p-[18px] font-mono text-[15px] leading-[1.9] text-[#d0d0ca] [word-break:keep-all]">
                        {section.prompt || '(프롬프트 원문을 불러오지 못했습니다.)'}
                      </p>
                    </article>

                    <article className="min-w-0 border border-[#d6ff50] bg-transparent p-[22px]">
                      <div className="font-mono text-xl leading-[1.4] font-bold tracking-[0.08em] text-[#d6ff50]">
                        TURN {String(section.turn).padStart(2, '0')} FEEDBACK
                      </div>
                      <PromptFeedback feedback={section.feedbackMd} />
                    </article>
                  </div>
                ))}
              </section>
            )}

            <section className="mt-4 border border-[#d6ff50] bg-transparent p-[22px]">
              <div className="font-mono text-xl leading-[1.4] font-bold tracking-[0.08em] text-[#d6ff50]">
                OVERALL.MD
              </div>
              <PromptFeedback feedback={feedback.overallMd} />
            </section>

            <div className="mt-5 flex justify-end gap-3 max-[760px]:flex-col max-[760px]:justify-stretch">
              {attempt && (
                <Button
                  className="max-[760px]:w-full"
                  to={`/problems/${attempt.problemId}`}
                >
                  BACK TO PROBLEM ↗
                </Button>
              )}
              <Button className="max-[760px]:w-full" to="/problems">
                BACK TO PROBLEMS ↗
              </Button>
            </div>
          </>
        )}
      </main>
      <Footer />
    </div>
  );
}
