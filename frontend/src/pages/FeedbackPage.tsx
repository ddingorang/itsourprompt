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
        <Link className="logo" to="/" aria-label="홈으로 이동">
          prompt<i>.</i>practice
        </Link>
        <span className="header-meta">
          {result
            ? `PROBLEM ${String(result.problemId).padStart(2, '0')} / FEEDBACK RESULT`
            : 'FEEDBACK RESULT'}
        </span>
      </header>

      <main className="feedback-main">
        <section className="principle">
          <h1>PROMPT FEEDBACK</h1>
          <span>
            INTENT RECONSTRUCTION
            <br />+ SUGGESTIONS
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
                <p className="prompt-copy">{result.prompt}</p>
              </article>

              <article className="box outline">
                <div className="label">FEEDBACK.MD</div>
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
