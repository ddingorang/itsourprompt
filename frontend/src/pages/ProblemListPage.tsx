interface Problem {
  id: number;
  title: string;
  description: string;
}

const problems: Problem[] = [
  {
    id: 1,
    title: '게시판 API 구현',
    description: '게시글 엔티티와 CRUD 계층을 완성하는 문제',
  },
  {
    id: 2,
    title: 'Todo 입력 예외 처리',
    description: '빈 문자열과 공백 입력을 안전하게 처리하는 문제',
  },
  {
    id: 3,
    title: '사용자 프로필 컴포넌트',
    description: '주어진 데이터 구조를 읽기 쉬운 UI로 구성하는 문제',
  },
  {
    id: 4,
    title: '상품 목록 필터링',
    description: '조건 조합과 빈 결과 상태를 구현하는 문제',
  },
];

const styles = `
  :root {
    --accent: #d6ff50;
    --black: #090909;
    --panel: #171717;
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
    position: sticky;
    top: 0;
    z-index: 10;
    display: flex;
    align-items: center;
    justify-content: space-between;
    min-height: 66px;
    padding: 0 5vw;
    border-bottom: 1px solid var(--line);
    background: rgba(9, 9, 9, .94);
    backdrop-filter: blur(12px);
    font: 11px var(--font-mono);
    letter-spacing: .04em;
  }

  .logo {
    font: 900 20px/1 var(--font-sans);
    letter-spacing: -1.6px;
  }
  .logo i { color: var(--accent); font-style: normal; }
  .header-meta { color: var(--muted); }

  main {
    width: min(1180px, calc(100% - 48px));
    margin: 0 auto;
    padding: clamp(56px, 8vw, 96px) 0 80px;
  }

  .eyebrow {
    color: var(--accent);
    font: 700 20px/1.4 var(--font-mono);
    letter-spacing: .08em;
  }

  .hero {
    display: grid;
    grid-template-columns: minmax(0, 1.25fr) minmax(250px, .75fr);
    gap: 36px;
    align-items: end;
  }

  .hero h1 {
    margin: 14px 0 54px;
    max-width: 820px;
    font-size: clamp(66px, 10vw, 132px);
    line-height: .78;
    letter-spacing: -.075em;
    text-wrap: balance;
  }

  .hero-copy {
    margin: 0 0 58px auto;
    max-width: 430px;
    color: var(--muted);
    font-size: 14px;
    line-height: 1.7;
    word-break: keep-all;
  }

  .controls {
    padding: 15px 0;
    border-top: 1px solid var(--white);
    border-bottom: 1px solid var(--line);
  }

  .count {
    font: 700 11px var(--font-mono);
    letter-spacing: .06em;
  }

  .problem-list { border-bottom: 1px solid var(--line); }

  .problem-row {
    display: grid;
    grid-template-columns: 76px minmax(0, 1fr) 42px;
    gap: 16px;
    align-items: center;
    min-height: 108px;
    padding: 22px 0;
    border-top: 1px solid var(--line);
    transition: background .18s ease, padding .18s ease;
  }

  .problem-row:first-child { border-top: 0; }
  .problem-row:hover,
  .problem-row:focus-visible {
    padding-left: 14px;
    padding-right: 14px;
    outline: none;
    background: var(--panel);
  }

  .number {
    color: var(--muted);
    font: 12px var(--font-mono);
  }

  .problem-title {
    min-width: 0;
    font-size: clamp(18px, 2.2vw, 25px);
    font-weight: 800;
    letter-spacing: -.035em;
    word-break: keep-all;
  }

  .problem-title p {
    margin: 8px 0 0;
    color: var(--muted);
    font-size: 12px;
    font-weight: 400;
    line-height: 1.55;
    letter-spacing: 0;
  }


  .arrow {
    justify-self: end;
    color: var(--accent);
    font-size: 24px;
    transition: transform .18s ease;
  }
  .problem-row:hover .arrow { transform: translate(3px, -3px); }

  .footer-note {
    display: flex;
    justify-content: space-between;
    gap: 24px;
    margin-top: 22px;
    color: #777;
    font: 10px/1.5 var(--font-mono);
  }

  @media (max-width: 760px) {
    .site-header { padding: 0 20px; }
    .header-meta { display: none; }
    main { width: min(100% - 32px, 680px); padding-top: 48px; }
    .hero { grid-template-columns: 1fr; gap: 0; }
    .hero h1 { margin-bottom: 26px; }
    .hero-copy { margin: 0 0 42px; max-width: 520px; }
    .problem-row { grid-template-columns: 44px minmax(0, 1fr) 28px; min-height: 100px; }
    .footer-note { flex-direction: column; gap: 6px; }
  }
`;

export default function ProblemListPage() {
  return (
    <>
      <style>{styles}</style>

      <header className="site-header">
        <a className="logo" href="/problems" aria-label="문제 목록으로 이동">
          prompt<i>.</i>practice
        </a>
        <div className="header-meta">ANONYMOUS SESSION / NO HISTORY</div>
      </header>

      <main>
        <div className="eyebrow">PRACTICE / PROBLEM QUEUE</div>

        <section className="hero">
          <h1>
            ONE PROMPT<br />
            ONE RUN
          </h1>
          <p className="hero-copy">
            문제를 선택하고 자연어 프롬프트 하나를 입력하세요.
            <br />
            매 실행은 깨끗한 스켈레톤에서 시작하며
            <br />
            결과는 브라우저에만 잠시 남습니다.
          </p>
        </section>

        <section className="controls" aria-label="문제 목록 정보">
          <div className="count">
            AVAILABLE PROBLEMS / {String(problems.length).padStart(2, '0')}
          </div>
        </section>

        <section className="problem-list">
          {problems.map((problem) => (
            <a
              className="problem-row"
              href={`/problems/${problem.id}`}
              key={problem.id}
            >
              <span className="number">{String(problem.id).padStart(2, '0')}</span>
              <div className="problem-title">
                {problem.title}
                <p>{problem.description}</p>
              </div>
              <span className="arrow" aria-hidden="true">
                ↗
              </span>
            </a>
          ))}
        </section>

        <div className="footer-note">
          <span>DATA SOURCE / GET /api/problems</span>
          <span>AUTH / NONE · RATE LIMIT / IP BASED</span>
        </div>
      </main>
    </>
  );
}
