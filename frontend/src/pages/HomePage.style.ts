export const homePageStyles = `
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

  .logo { font: 900 20px/1 var(--font-sans); letter-spacing: -1.6px; }
  .logo i { color: var(--accent); font-style: normal; }
  .header-meta { color: var(--muted); }

  .problem-list-main {
    width: calc(100% - 10vw);
    margin: 0 auto;
    padding: clamp(32px, 5vw, 56px) 0 80px;
  }

  .eyebrow {
    color: var(--accent);
    font: 700 20px/1.4 var(--font-mono);
    letter-spacing: .08em;
  }

  .hero {
    display: block;
    border-bottom: 1px solid var(--accent);
  }

  .hero-content {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    gap: 40px;
    margin: 14px 0 54px;
  }

  .hero h1 {
    margin: 0;
    max-width: 820px;
    font-size: clamp(48px, 8vw, 88px);
    line-height: .78;
    letter-spacing: -.075em;
  }

  .problem-list-link {
    display: inline-flex;
    align-items: center;
    flex-shrink: 0;
    gap: 28px;
    padding: 15px 20px;
    border: 1px solid var(--accent);
    background: var(--accent);
    color: var(--black);
    font: 800 12px/1 var(--font-sans);
    letter-spacing: -.01em;
    transition: gap .18s ease, background .18s ease, color .18s ease;
  }

  .problem-list-link:hover,
  .problem-list-link:focus-visible {
    gap: 36px;
    outline: none;
    background: transparent;
    color: var(--accent);
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

  .number { color: var(--muted); font: 12px var(--font-mono); }

  .problem-title {
    min-width: 0;
    font-size: clamp(18px, 2.2vw, 25px);
    font-weight: 800;
    letter-spacing: -.035em;
    word-break: keep-all;
  }

  .arrow {
    justify-self: end;
    color: var(--accent);
    font-size: 24px;
    transition: transform .18s ease;
  }

  .problem-row:hover .arrow { transform: translate(3px, -3px); }

  .page-state {
    padding: 44px 0;
    border-bottom: 1px solid var(--line);
    color: var(--muted);
    font: 12px/1.7 var(--font-mono);
  }

  .page-state.error { color: #ff786b; }

  @media (max-width: 760px) {
    .site-header { padding: 0 20px; }
    .header-meta { display: none; }
    .problem-list-main { width: min(100% - 32px, 680px); padding-top: 32px; }
    .hero-content {
      align-items: flex-start;
      flex-direction: column;
      gap: 30px;
      margin-bottom: 34px;
    }
    .problem-row {
      grid-template-columns: 44px minmax(0, 1fr) 28px;
      min-height: 100px;
    }
  }
`;
