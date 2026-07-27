export const problemListPageStyles = `
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

  .logo i {
    color: var(--accent);
    font-style: normal;
  }

  .header-meta { color: var(--muted); }

  .problem-list-page {
    width: calc(100% - 10vw);
    margin: 0 auto;
    padding: clamp(32px, 5vw, 56px) 0 80px;
  }

  .problem-list-header {
    display: flex;
    align-items: end;
    justify-content: space-between;
    gap: 24px;
    padding-bottom: 54px;
  }

  .problem-list-eyebrow {
    margin: 0;
    color: var(--accent);
    font: 700 clamp(36px, 6vw, 64px)/.82 var(--font-mono);
    letter-spacing: -.04em;
  }

  .problem-list-controls {
    padding: 15px 0;
    border-top: 1px solid var(--white);
    border-bottom: 1px solid var(--line);
  }

  .problem-count {
    color: var(--muted);
    font: 700 11px var(--font-mono);
    letter-spacing: .06em;
    white-space: nowrap;
  }

  .problem-list-items {
    border-bottom: 1px solid var(--line);
  }

  .problem-list-item {
    display: grid;
    grid-template-columns: 64px minmax(0, 1fr) auto;
    gap: 20px;
    align-items: center;
    min-height: 96px;
    padding: 20px 0;
    border-top: 1px solid var(--line);
    color: inherit;
    text-decoration: none;
    transition: padding .18s ease, background .18s ease, color .18s ease;
  }

  .problem-list-item:first-child {
    border-top: 0;
  }

  .problem-list-item:hover,
  .problem-list-item:focus-visible {
    padding-right: 14px;
    padding-left: 14px;
    outline: none;
    background: var(--accent);
    color: var(--black);
  }

  .problem-number {
    color: var(--muted);
    font: 12px var(--font-mono);
    transition: color .18s ease;
  }

  .problem-title {
    min-width: 0;
    font-size: clamp(17px, 2vw, 23px);
    font-weight: 800;
    letter-spacing: -.03em;
    word-break: keep-all;
  }

  .problem-action {
    justify-self: end;
    color: var(--accent);
    font: 800 11px/1 var(--font-sans);
    letter-spacing: .02em;
    white-space: nowrap;
    transition: color .18s ease;
  }

  .problem-arrow {
    display: inline-block;
    margin-left: 8px;
    font-size: 18px;
    transition: transform .18s ease;
  }

  .problem-list-item:hover .problem-number,
  .problem-list-item:focus-visible .problem-number,
  .problem-list-item:hover .problem-action,
  .problem-list-item:focus-visible .problem-action {
    color: var(--black);
  }

  .problem-list-item:hover .problem-arrow,
  .problem-list-item:focus-visible .problem-arrow {
    transform: translate(3px, -3px);
  }

  .problem-list-state {
    padding: 48px 0;
    border-bottom: 1px solid var(--line);
    color: var(--muted);
    font: 12px/1.7 var(--font-mono);
  }

  .problem-list-error {
    color: #ff786b;
  }

  @media (max-width: 640px) {
    .site-header {
      padding: 0 20px;
    }

    .header-meta {
      display: none;
    }

    .problem-list-page {
      width: min(calc(100% - 32px), 1080px);
      padding-top: 40px;
    }

    .problem-list-header {
      align-items: start;
      flex-direction: column;
      gap: 16px;
    }

    .problem-list-item {
      grid-template-columns: 42px minmax(0, 1fr) auto;
      gap: 12px;
      min-height: 88px;
    }

    .problem-action {
      font-size: 0;
    }

    .problem-arrow {
      margin-left: 0;
      font-size: 18px;
    }
  }
`;
