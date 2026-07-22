import { useEffect, useState } from 'react';

interface SavedRun {
  problemId: number;
  prompt: string;
  passedTests: number;
  totalTests: number;
}

const fallbackResult: SavedRun = {
  problemId: 1,
  prompt: '',
  passedTests: 7,
  totalTests: 10,
};

const styles = `
  :root {
    --accent: #d6ff50;
    --black: #090909;
    --panel: #1b1b1b;
    --line: #343434;
    --white: #f5f5ef;
    --muted: #a3a3a3;
    --font-sans: Arial, "Noto Sans KR", sans-serif;
    --font-mono: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  }

  * { box-sizing: border-box; }
  html { background: var(--black); }
  body {
    margin: 0;
    min-width: 320px;
    background: var(--black);
    color: var(--white);
    font-family: var(--font-sans);
  }

  a { color: inherit; text-decoration: none; }

  .site-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    min-height: 66px;
    padding: 0 5vw;
    border-bottom: 1px solid var(--line);
    font: 11px var(--font-mono);
  }

  .logo { font: 900 19px/1 var(--font-sans); letter-spacing: -1.5px; }
  .logo i { color: var(--accent); font-style: normal; }
  .header-meta { color: var(--muted); }

  main {
    width: min(1040px, calc(100% - 48px));
    margin: 0 auto;
    padding: clamp(52px, 7vw, 82px) 0 80px;
  }

  .label {
    color: var(--accent);
    font: 700 16px/1.5 var(--font-mono);
    letter-spacing: .08em;
  }

  .hero {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(250px, 330px);
    gap: 36px;
    align-items: end;
  }

  .hero h1 {
    margin: 12px 0 48px;
    font-size: clamp(68px, 10vw, 112px);
    line-height: .8;
    letter-spacing: -.075em;
  }

  .hero-copy {
    margin: 0 0 50px;
    color: var(--muted);
    font-size: 14px;
    line-height: 1.7;
    word-break: keep-all;
  }

  .principle {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 18px;
    padding: 20px 22px;
    background: var(--accent);
    color: var(--black);
  }

  .principle strong {
    font-size: clamp(24px, 4vw, 42px);
    line-height: 1;
    letter-spacing: -.055em;
  }

  .principle span {
    text-align: right;
    font: 700 14px/1.5 var(--font-mono);
  }

  .content-grid {
    display: grid;
    grid-template-columns: minmax(260px, .82fr) minmax(0, 1.18fr);
    gap: 16px;
    margin-top: 16px;
  }

  .box {
    min-width: 0;
    padding: 22px;
    background: var(--panel);
  }

  .box.outline {
    border: 1px solid var(--accent);
    background: transparent;
  }

  .box h2 {
    margin: 8px 0 18px;
    font-size: 17px;
    letter-spacing: -.025em;
  }

  .test-result {
    display: grid;
    place-items: center;
    min-height: 240px;
    padding: 28px 16px;
    border: 1px solid #393939;
    background: #111;
    text-align: center;
  }

  .test-count {
    font-size: clamp(64px, 10vw, 104px);
    font-weight: 900;
    line-height: .9;
    letter-spacing: -.07em;
  }

  .test-count span { color: var(--accent); }

  .test-caption {
    margin-top: 20px;
    color: var(--muted);
    font: 10px/1.6 var(--font-mono);
    letter-spacing: .08em;
  }

  .feedback-copy {
    margin: 0;
    color: #d0d0ca;
    font-size: 14px;
    line-height: 1.95;
    word-break: keep-all;
  }

  .actions {
    display: flex;
    justify-content: flex-end;
    margin-top: 20px;
  }

  .button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 42px;
    padding: 0 18px;
    border: 1px solid var(--accent);
    background: var(--accent);
    color: var(--black);
    font-size: 11px;
    font-weight: 800;
  }


  @media (max-width: 760px) {
    .site-header { padding: 0 18px; }
    .header-meta { display: none; }
    main { width: min(100% - 32px, 680px); padding-top: 46px; }
    .hero { grid-template-columns: 1fr; gap: 0; }
    .hero h1 { margin-bottom: 24px; }
    .hero-copy { margin-bottom: 38px; }
    .principle { align-items: flex-start; flex-direction: column; }
    .principle span { text-align: left; }
    .content-grid { grid-template-columns: 1fr; }
    .actions { justify-content: stretch; }
    .button { width: 100%; }
  }
`;

export default function FeedbackPage() {
  const [result, setResult] = useState<SavedRun>(fallbackResult);

  useEffect(() => {
    try {
      const saved = localStorage.getItem('promptPracticeRun');
      if (!saved) return;

      const parsed = JSON.parse(saved) as Partial<SavedRun>;
      if (
        typeof parsed.passedTests === 'number' &&
        typeof parsed.totalTests === 'number'
      ) {
        setResult({
          problemId: parsed.problemId ?? 1,
          prompt: parsed.prompt ?? '',
          passedTests: parsed.passedTests,
          totalTests: parsed.totalTests,
        });
      }
    } catch (error) {
      console.warn('Saved test result could not be loaded.', error);
    }
  }, []);

  return (
    <>
      <style>{styles}</style>

      <header className="site-header">
        <a className="logo" href="/problems">
          prompt<i>.</i>practice
        </a>
        <span className="header-meta">PROBLEM 01 / FEEDBACK RESULT</span>
      </header>

      <main>
        <div className="label">SUBMISSION / PROMPT FEEDBACK</div>

        <section className="hero">
          <h1>
            MAKE IT<br />
            MORE PRECISE
          </h1>
          <p className="hero-copy">
            생성된 코드의 테스트 실행 결과를 확인하고, 다음 프롬프트에서 의도를 더
            정확하게 전달할 수 있도록 피드백을 제공합니다.
          </p>
        </section>

        <section className="principle">
          <strong>RUN RESULT + FEEDBACK</strong>
          <span>
            TEST CASES
            <br />+ PROMPT IMPROVEMENT
          </span>
        </section>

        <section className="content-grid">
          <article className="box">
            <div className="label">TEST RESULT</div>
            <h2>테스트 실행 결과</h2>
            <div className="test-result">
              <div>
                <div className="test-count">
                  <span>{result.passedTests}</span> / {result.totalTests}
                </div>
                <div className="test-caption">PASSED TEST CASES / TOTAL TEST CASES</div>
              </div>
            </div>
          </article>

          <article className="box outline">
            <div className="label">FEEDBACK.MD</div>
            <h2>프롬프트 피드백</h2>
            <p className="feedback-copy">
              작성한 프롬프트는 게시판 기능에 필요한 엔티티와 CRUD 계층을 한 번에
              구성하고, 제목과 본문의 필수값 처리 및 컨트롤러와 서비스 계층 분리까지
              요청했다는 점에서 핵심 의도가 비교적 명확합니다. 다만 일부 테스트가
              통과하지 못한 원인을 줄이려면 각 CRUD API의 경로와 HTTP 메서드, 요청과
              응답 형식, 빈 값이 들어왔을 때 반환할 예외 유형과 상태 코드를 구체적으로
              적는 것이 좋습니다. 다음 프롬프트에서는 POST /posts, GET /posts/:id, PUT
              /posts/:id, DELETE /posts/:id처럼 엔드포인트를 명시하고, 제목 또는 본문이
              비어 있으면 422 응답을 반환하도록 요구하면 생성 결과의 일관성을 높일 수
              있습니다.
            </p>
          </article>
        </section>

        <div className="actions">
          <a className="button" href="/problems">
            BACK TO PROBLEMS
          </a>
        </div>
      </main>
    </>
  );
}
