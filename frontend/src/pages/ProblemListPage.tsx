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
        if (isMounted) setProblems(response.problems);
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
        if (isMounted) setIsLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, []);

  return (
    <>
      <style>{problemListPageStyles}</style>

      <header className="site-header">
        <Link className="logo" to="/problems" aria-label="문제 목록으로 이동">
          prompt<i>.</i>practice
        </Link>
        <div className="header-meta">ANONYMOUS SESSION / NO HISTORY</div>
      </header>

      <main className="problem-list-main">
        <div className="eyebrow">PRACTICE / PROBLEM QUEUE</div>

        <section className="hero">
          <h1>
            ONE PROMPT<br />
            ONE RUN
          </h1>
        </section>

        <section className="controls" aria-label="문제 목록 정보">
          <div className="count">
            AVAILABLE PROBLEMS / {String(problems.length).padStart(2, '0')}
          </div>
        </section>

        {isLoading && <div className="page-state">문제 목록을 불러오는 중입니다…</div>}

        {errorMessage && <div className="page-state error">{errorMessage}</div>}

        {!isLoading && !errorMessage && (
          <section className="problem-list">
            {problems.map((problem) => (
              <Link
                className="problem-row"
                key={problem.id}
                to={`/problems/${problem.id}`}
              >
                <span className="number">
                  {String(problem.id).padStart(2, '0')}
                </span>
                <div className="problem-title">{problem.title}</div>
                <span className="arrow" aria-hidden="true">
                  ↗
                </span>
              </Link>
            ))}
          </section>
        )}
      </main>
    </>
  );
}
