import { Link, useParams } from 'react-router-dom';

import PromptFeedback from '../features/feedback/PromptFeedback';
import { getFeedbackResult } from '../features/submission/storage';
import Button from '../shared/components/Button';
import { buttonStyles } from '../shared/components/Button.style';
import { feedbackPageStyles } from './FeedbackPage.style';

export default function FeedbackPage() {
  const { problemId } = useParams();
  const result = getFeedbackResult();
  const matchesCurrentProblem =
    result && (!problemId || result.problemId === Number(problemId));

  return (
    <>
      <style>{feedbackPageStyles + buttonStyles}</style>

      <header className="site-header">
        <Link className="logo" to="/problems">
          prompt<i>.</i>practice
        </Link>
        <span className="header-meta">
          {result
            ? `PROBLEM ${String(result.problemId).padStart(2, '0')} / FEEDBACK RESULT`
            : 'FEEDBACK RESULT'}
        </span>
      </header>

      <main className="feedback-main">
        <div className="label">SUBMISSION / PROMPT FEEDBACK</div>

        <section className="hero">
          <h1>
            MAKE IT<br />
            MORE<br />
            PRECISE
          </h1>
        </section>

        <section className="principle">
          <strong>RUN RESULT + FEEDBACK</strong>
          <span>
            INTENT RECONSTRUCTION
            <br />+ 1–2 SUGGESTIONS
          </span>
        </section>

        {!matchesCurrentProblem ? (
          <section className="page-state">
            <div>저장된 피드백 결과가 없습니다. 문제를 실행하고 다시 제출해주세요.</div>
            <Button to="/problems">BACK TO PROBLEMS ↗</Button>
          </section>
        ) : (
          <>
            <section className="content-grid">
              <article className="box">
                <div className="label">SUBMITTED PROMPT</div>
                <h2>사용자가 작성한 프롬프트</h2>
                <p className="prompt-copy">{result.prompt}</p>
              </article>

              <article className="box outline">
                <div className="label">FEEDBACK.MD</div>
                <h2>프롬프트 피드백</h2>
                <PromptFeedback feedback={result.feedback} />
              </article>
            </section>

            <div className="actions">
              <Button to="/problems">BACK TO PROBLEMS ↗</Button>
            </div>
          </>
        )}
      </main>
    </>
  );
}
