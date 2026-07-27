export const problemDetailPageStyles = `
  :root {
    --accent: #d6ff50;
    --black: #090909;
    --panel: #171717;
    --panel-2: #202020;
    --line: #343434;
    --white: #f5f5ef;
    --muted: #a3a3a3;
    --danger: #ff786b;
    --font-sans: Arial, "Noto Sans KR", sans-serif;
    --font-mono: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  }

  * { box-sizing: border-box; }
  html, body { min-height: 100%; }
  body {
    margin: 0;
    min-width: 320px;
    background: var(--black);
    color: var(--white);
    font-family: var(--font-sans);
  }

  button, textarea { font: inherit; }
  button { cursor: pointer; }
  a { color: inherit; text-decoration: none; }

  .site-header {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr);
    align-items: center;
    gap: 24px;
    min-height: 66px;
    padding: 0 24px;
    border-bottom: 1px solid var(--line);
    background: var(--black);
    font: 11px var(--font-mono);
  }

  .logo { font: 900 19px/1 var(--font-sans); letter-spacing: -1.5px; }
  .logo i { color: var(--accent); font-style: normal; }
  .header-title {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: var(--muted);
    text-align: right;
  }

  .workspace {
    display: grid;
    grid-template-columns: 230px minmax(360px, 1fr) minmax(340px, 390px);
    min-height: calc(100vh - 66px);
  }

  .column { min-width: 0; }
  .file-column,
  .viewer-column { border-right: 1px solid var(--line); }
  .file-column { padding: 22px 24px; }
  .viewer-column { padding: 22px 28px 40px; }
  .action-column { padding: 22px 24px; }

  .label {
    color: var(--accent);
    font: 700 14px/1.5 var(--font-mono);
    letter-spacing: .08em;
  }

  .section-title { margin: 10px 0 22px; font-size: 22px; letter-spacing: -.04em; }

  .file-tree {
    display: grid;
    gap: 3px;
    margin-top: 18px;
    color: var(--muted);
    font: 12px/1.5 var(--font-mono);
    user-select: none;
  }

  .tree-row {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 28px;
    align-items: center;
    gap: 8px;
    width: 100%;
    min-height: 34px;
    padding: 0 7px;
    border: 0;
    background: transparent;
    color: inherit;
    text-align: left;
    font: inherit;
  }

  .tree-row:hover { color: var(--white); }
  .tree-row.active { background: var(--accent); color: var(--black); }
  .tree-row.deleted { opacity: .6; text-decoration: line-through; }
  .tree-row .path { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .change { text-align: right; font-weight: 900; }
  .change.ADDED { color: var(--accent); }
  .change.MODIFIED { color: #fff; }
  .change.DELETED { color: var(--danger); }
  .active .change { color: var(--black); }

  .legend {
    display: grid;
    gap: 8px;
    margin-top: 28px;
    padding-top: 16px;
    border-top: 1px solid var(--line);
    color: #767676;
    font: 9px var(--font-mono);
  }

  .viewer-top {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 18px;
    margin-bottom: 20px;
  }


  .read-only {
    flex: 0 0 auto;
    padding: 6px 8px;
    border: 1px solid #494949;
    color: var(--muted);
    font: 9px var(--font-mono);
  }

  .code-shell {
    min-height: 360px;
    overflow: auto;
    border: 1px solid #292929;
    background: var(--panel-2);
  }

  .code-head {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    padding: 11px 14px;
    border-bottom: 1px solid #333;
    color: var(--muted);
    font: 10px var(--font-mono);
  }

  pre {
    margin: 0;
    padding: 24px;
    min-width: max-content;
    color: #e3e3dd;
    font: 12px/1.9 var(--font-mono);
    tab-size: 2;
  }

  .mission-card { padding-bottom: 22px; border-bottom: 1px solid var(--line); }
  .mission-card h2 {
    margin: 10px 0 12px;
    font-size: 25px;
    line-height: 1.05;
    letter-spacing: -.045em;
  }

  .spec-content,
  .result-copy {
    margin: 0;
    color: var(--muted);
    font-size: 13px;
    line-height: 1.7;
    white-space: pre-wrap;
    word-break: keep-all;
  }

  .spec-content h1,
  .spec-content h2,
  .spec-content h3,
  .spec-content h4,
  .spec-content h5,
  .spec-content h6 {
    color: var(--white);
  }

  .chips { margin-top: 15px; }
  .chip {
    display: inline-block;
    margin: 0 4px 5px 0;
    padding: 5px 7px;
    border: 1px solid #4c4c4c;
    color: #bdbdbd;
    font: 9px var(--font-mono);
  }

  .prompt-block { padding-top: 22px; }
  textarea {
    width: 100%;
    min-height: 150px;
    margin-top: 10px;
    resize: vertical;
    padding: 14px;
    border: 1px solid #555;
    outline: 0;
    background: #131313;
    color: var(--white);
    font-size: 13px;
    line-height: 1.6;
  }

  textarea:focus { border-color: var(--accent); }
  textarea:disabled { opacity: .6; cursor: not-allowed; }

  .prompt-meta {
    display: flex;
    justify-content: flex-end;
    margin-top: 8px;
    color: #777;
    font: 9px var(--font-mono);
  }

  .run-button { margin-top: 12px; }

  .status {
    margin-top: 14px;
    padding: 12px;
    border: 1px solid #484848;
    color: var(--muted);
    font: 10px/1.6 var(--font-mono);
  }

  .status.error { border-color: var(--danger); color: var(--danger); }

  .result-card {
    margin-top: 18px;
    padding: 18px;
    border: 1px solid var(--accent);
    background: #121212;
  }

  .result-card h3 { margin: 10px 0; font-size: 15px; }
  .ai-response {
    max-height: 180px;
    overflow: auto;
    margin-top: 14px;
    padding: 12px;
    border: 1px solid #333;
    background: #0e0e0e;
    color: #d8d8d2;
    font: 11px/1.7 var(--font-mono);
    white-space: pre-wrap;
  }

  .result-actions {
    display: grid;
    grid-template-columns: 1fr auto;
    gap: 8px;
    margin-top: 16px;
  }

  .page-state {
    display: grid;
    place-items: center;
    min-height: calc(100vh - 66px);
    padding: 40px;
    color: var(--muted);
    font: 12px/1.7 var(--font-mono);
  }

  .page-state.error { color: var(--danger); }

  @media (max-width: 1080px) {
    .workspace { grid-template-columns: 190px minmax(0, 1fr); }
    .action-column { grid-column: 1 / -1; border-top: 1px solid var(--line); }
    .viewer-column { border-right: 0; }
    .action-column {
      display: grid;
      grid-template-columns: minmax(0, .8fr) minmax(300px, 1.2fr);
      gap: 28px;
    }
    .prompt-block { padding-top: 0; }
    .mission-card { border-bottom: 0; padding-bottom: 0; }
  }

  @media (max-width: 700px) {
    .site-header { grid-template-columns: auto 1fr; padding: 0 16px; }
    .header-title { display: none; }
    .workspace { display: block; }
    .file-column, .viewer-column { border-right: 0; border-bottom: 1px solid var(--line); }
    .file-column { padding: 18px 16px; }
    .viewer-column, .action-column { padding: 20px 16px 30px; }
    .action-column { display: block; }
    .prompt-block { padding-top: 22px; }
    .result-actions { grid-template-columns: 1fr; }
  }
`;
