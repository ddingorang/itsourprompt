import { useParams } from 'react-router-dom';

import PromptFeedback from '../features/feedback/PromptFeedback';
import { getFeedbackResult } from '../features/submission/storage';
import Button from '../shared/components/Button';
import Header from '../shared/components/Header';

export default function FeedbackPage() {
  const { problemId } = useParams();
  const result = getFeedbackResult();
  const matchesCurrentProblem =
    result && (!problemId || result.problemId === Number(problemId));

  return (
    <div className="min-h-screen min-w-80 bg-[#090909] text-[#f5f5ef] [font-family:Arial,'Noto_Sans_KR',sans-serif]">
      <Header mobileBreakpoint="760">
        <span className="text-[#a3a3a3] max-[760px]:hidden">
          {result
            ? `PROBLEM ${String(result.problemId).padStart(2, '0')} / FEEDBACK RESULT`
            : 'FEEDBACK RESULT'}
        </span>
      </Header>

      <main className="mx-auto w-[calc(100%_-_10vw)] pt-[clamp(32px,5vw,56px)] pb-20 max-[760px]:w-[min(calc(100%_-_32px),680px)] max-[760px]:pt-8">
        <section className="flex items-center justify-between gap-[18px] bg-[#d6ff50] px-[22px] py-5 text-[#090909] max-[760px]:flex-col max-[760px]:items-start">
          <h1 className="m-0 font-mono text-[clamp(36px,6vw,64px)] leading-[0.82] font-bold tracking-[-0.04em]">
            PROMPT FEEDBACK
          </h1>
          <span className="text-right font-mono text-sm leading-[1.5] font-bold tracking-[0.05em] max-[760px]:text-left">
            INTENT RECONSTRUCTION
            <br />+ SUGGESTIONS
          </span>
        </section>

        {!matchesCurrentProblem ? (
          <section className="border border-[#ff786b] p-8 text-sm leading-[1.7] text-[#ff786b]">
            <div>
              저장된 피드백 결과가 없습니다. 문제를 실행하고 다시 제출해
              주세요.
            </div>
            <Button className="mt-5" to="/problems">
              BACK TO PROBLEMS ↗
            </Button>
          </section>
        ) : (
          <>
            <section className="mt-4 grid grid-cols-[minmax(260px,0.78fr)_minmax(0,1.22fr)] gap-4 max-[760px]:grid-cols-1">
              <article className="min-w-0 bg-[#1b1b1b] p-[22px]">
                <div className="font-mono text-xl leading-[1.4] font-bold tracking-[0.08em] text-[#d6ff50]">
                  SUBMITTED PROMPT
                </div>
                <p className="mt-[18px] min-h-[220px] whitespace-pre-wrap border border-[#393939] bg-[#111] p-[18px] font-mono text-[16px] leading-[1.95] text-[#d0d0ca] [word-break:keep-all]">
                  {result.prompt}
                </p>
              </article>

              <article className="min-w-0 border border-[#d6ff50] bg-transparent p-[22px]">
                <div className="font-mono text-xl leading-[1.4] font-bold tracking-[0.08em] text-[#d6ff50]">
                  FEEDBACK.MD
                </div>
                <PromptFeedback feedback={result.feedback} />
              </article>
            </section>

            <div className="mt-5 flex justify-end max-[760px]:justify-stretch">
              <Button className="max-[760px]:w-full" to="/problems">
                BACK TO PROBLEMS ↗
              </Button>
            </div>
          </>
        )}
      </main>
    </div>
  );
}
