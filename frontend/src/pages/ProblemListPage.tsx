import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { getProblems } from '../features/problem/api';
import type { ProblemSummary } from '../features/problem/types';
import { ApiProblemError } from '../shared/api/apiClient';
import { problemListPageStyles } from './ProblemListPage.style';

export default function ProblemListPage() {
  const [problems, setProblems] = useState<ProblemSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

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
          error instanceof ApiProblemError
            ? error.problem.detail
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

  return (
    <>
      <style>{problemListPageStyles}</style>

      <header className="site-header">
        <Link className="logo" to="/" aria-label="홈으로 이동">
          prompt<i>.</i>practice
        </Link>
        <div className="header-meta">ANONYMOUS SESSION / NO HISTORY</div>
      </header>

      <main className="problem-list-page">
        <header className="problem-list-header">
          <div>
            <p className="problem-list-eyebrow">PROBLEM LIST</p>
            <h1>
              PRACTICE MORE
              <br />
              PROMPT BETTER
            </h1>
          </div>
        </header>

        <section className="problem-list-controls" aria-label="문제 목록 정보">
          <div className="problem-count">
            AVAILABLE PROBLEMS / {String(problems.length).padStart(2, '0')}
          </div>
        </section>

        {isLoading && (
          <div className="problem-list-state">문제 목록을 불러오는 중입니다…</div>
        )}

        {errorMessage && (
          <div className="problem-list-state problem-list-error">
            {errorMessage}
          </div>
        )}

        {!isLoading && !errorMessage && problems.length === 0 && (
          <div className="problem-list-state">등록된 문제가 없습니다.</div>
        )}

        {!isLoading && !errorMessage && problems.length > 0 && (
          <section className="problem-list-items" aria-label="문제 목록">
            {problems.map((problem) => (
              <Link
                className="problem-list-item"
                key={problem.id}
                to={`/problems/${problem.id}`}
              >
                <span className="problem-number">
                  {String(problem.id).padStart(2, '0')}
                </span>
                <span className="problem-title">{problem.title}</span>
                <span className="problem-action" aria-hidden="true">
                  START PRACTICE <span className="problem-arrow">↗</span>
                </span>
              </Link>
            ))}
          </section>
        )}
      </main>
    </>
  );
}
