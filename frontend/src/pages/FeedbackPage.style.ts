export const feedbackPageStyles = `
  :root {
    --accent: #d6ff50;
    --black: #090909;
    --panel: #1b1b1b;
    --line: #343434;
    --white: #f5f5ef;
    --muted: #a3a3a3;
    --danger: #ff786b;
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

  .feedback-main {
    width: min(1180px, calc(100% - 48px));
    margin: 0 auto;
    padding: clamp(32px, 5vw, 56px) 0 80px;
  }

  .label {
    color: var(--accent);
    font: 700 20px/1.4 var(--font-mono);
    letter-spacing: .08em;
  }

  .hero { display: block; }
  .hero h1 {
    margin: 14px 0 54px;
    max-width: 820px;
    font-size: clamp(48px, 8vw, 88px);
    line-height: .78;
    letter-spacing: -.075em;
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
    letter-spacing: .05em;
  }

  .content-grid {
    display: grid;
    grid-template-columns: minmax(260px, .78fr) minmax(0, 1.22fr);
    gap: 16px;
    margin-top: 16px;
  }

  .box { min-width: 0; padding: 22px; background: var(--panel); }
  .box.outline { border: 1px solid var(--accent); background: transparent; }
  .box h2 { margin: 8px 0 18px; font-size: 20px; letter-spacing: -.025em; }

  .prompt-copy,
  .feedback-copy {
    margin: 0;
    color: #d0d0ca;
    font-size: 14px;
    line-height: 1.95;
    white-space: pre-wrap;
    word-break: keep-all;
  }

  .prompt-copy {
    min-height: 220px;
    padding: 18px;
    border: 1px solid #393939;
    background: #111;
    font-family: var(--font-mono);
    font-size: 12px;
  }

  .actions { display: flex; justify-content: flex-end; margin-top: 20px; }

  .page-state {
    padding: 32px;
    border: 1px solid var(--danger);
    color: var(--danger);
    font-size: 14px;
    line-height: 1.7;
  }

  .page-state .app-button { margin-top: 20px; }

  @media (max-width: 760px) {
    .site-header { padding: 0 20px; }
    .header-meta { display: none; }
    .feedback-main { width: min(100% - 32px, 680px); padding-top: 32px; }
    .hero h1 { margin-bottom: 34px; }
    .principle { align-items: flex-start; flex-direction: column; }
    .principle span { text-align: left; }
    .content-grid { grid-template-columns: 1fr; }
    .actions { justify-content: stretch; }
    .actions .app-button { width: 100%; }
  }
`;
