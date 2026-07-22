import { useMemo, useState } from 'react';

type FileMap = Record<string, string>;
type StatusType = 'normal' | 'error';

interface StatusMessage {
  message: string;
  type: StatusType;
}

interface TreeItem {
  path: string;
  label: string;
  depth: 0 | 1 | 2;
  folder?: boolean;
  resultOnly?: boolean;
  changeType?: 'A' | 'M';
}

interface SavedRun {
  problemId: number;
  prompt: string;
  passedTests: number;
  totalTests: number;
}

const originalFiles: FileMap = {
  'build.gradle': `plugins {
  id 'java'
}

group = 'com.example'
version = '0.0.1'`,
  'src/main/java/App.java': `package com.example.board;

public class App {
  public static void main(String[] args) {
    System.out.println("Board API skeleton");
  }
}`,
  'src/test/AppTest.java': `package com.example.board;

class AppTest {
  // TODO: add tests
}`,
};

const resultFiles: FileMap = {
  ...originalFiles,
  'src/main/java/App.java': `package com.example.board;

public class App {
  public static void main(String[] args) {
    System.out.println("Board API ready");
  }
}`,
  'src/main/java/Post.java': `package com.example.board;

public record Post(
  Long id,
  String title,
  String content
) {}`,
  'src/main/java/PostController.java': `package com.example.board;

public class PostController {
  private final PostService postService;

  public PostController(PostService postService) {
    this.postService = postService;
  }
}`,
  'src/main/java/PostService.java': `package com.example.board;

public class PostService {
  public Post create(String title, String content) {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("title is required");
    }
    return new Post(null, title, content);
  }
}`,
};

const treeItems: TreeItem[] = [
  { path: 'build.gradle', label: '▣ build.gradle', depth: 0 },
  {
    path: 'src/main/java',
    label: '⌄ src / main / java',
    depth: 1,
    folder: true,
  },
  { path: 'src/main/java/App.java', label: 'App.java', depth: 2 },
  {
    path: 'src/main/java/Post.java',
    label: 'Post.java',
    depth: 2,
    resultOnly: true,
    changeType: 'A',
  },
  {
    path: 'src/main/java/PostController.java',
    label: 'PostController.java',
    depth: 2,
    resultOnly: true,
    changeType: 'A',
  },
  {
    path: 'src/main/java/PostService.java',
    label: 'PostService.java',
    depth: 2,
    resultOnly: true,
    changeType: 'A',
  },
  {
    path: 'src/test',
    label: '⌄ src / test',
    depth: 1,
    folder: true,
  },
  { path: 'src/test/AppTest.java', label: 'AppTest.java', depth: 2 },
];

const styles = `
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

  .primary-btn,
  .small-btn {
    border: 0;
    font-weight: 800;
    letter-spacing: -.01em;
  }

  .workspace {
    display: grid;
    grid-template-columns: 230px minmax(360px, 1fr) minmax(340px, 390px);
    min-height: calc(100vh - 66px);
  }

  .column { min-width: 0; }
  .file-column,
  .viewer-column { border-right: 1px solid var(--line); }

  .file-column { padding: 22px 18px; }
  .viewer-column { padding: 22px 28px 40px; }
  .action-column { padding: 22px; }

  .label {
    color: var(--accent);
    font: 10px/1.5 var(--font-mono);
    letter-spacing: .08em;
  }

  .section-title {
    margin: 10px 0 22px;
    font-size: 22px;
    letter-spacing: -.04em;
  }

  .file-tree {
    margin-top: 18px;
    color: var(--muted);
    font: 12px/2.35 var(--font-mono);
    user-select: none;
  }

  .tree-row {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 24px;
    align-items: center;
    gap: 8px;
    width: 100%;
    padding: 0 7px;
    border: 0;
    background: transparent;
    color: inherit;
    text-align: left;
    font: inherit;
  }

  .tree-row:hover { color: var(--white); }
  .tree-row.active { background: var(--accent); color: var(--black); }
  .tree-row .path { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .depth-1 .path { padding-left: 14px; }
  .depth-2 .path { padding-left: 28px; }
  .change { text-align: right; font-weight: 900; }
  .change.added { color: var(--accent); }
  .change.modified { color: #fff; }
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

  .viewer-top h1 {
    margin: 10px 0 0;
    font-size: clamp(24px, 3vw, 38px);
    line-height: 1;
    letter-spacing: -.055em;
    word-break: keep-all;
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

  .api-strip {
    display: flex;
    justify-content: space-between;
    gap: 16px;
    margin-top: 14px;
    color: #747474;
    font: 9px/1.5 var(--font-mono);
  }

  .mission-card {
    padding-bottom: 22px;
    border-bottom: 1px solid var(--line);
  }

  .mission-card h2 {
    margin: 10px 0 12px;
    font-size: 25px;
    line-height: 1.05;
    letter-spacing: -.045em;
  }

  .mission-card p,
  .result-copy {
    margin: 0;
    color: var(--muted);
    font-size: 13px;
    line-height: 1.7;
    word-break: keep-all;
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
    justify-content: space-between;
    gap: 16px;
    margin-top: 8px;
    color: #777;
    font: 9px var(--font-mono);
  }

  .primary-btn {
    width: 100%;
    margin-top: 12px;
    padding: 14px 16px;
    background: var(--accent);
    color: var(--black);
  }
  .primary-btn:hover { filter: brightness(.95); }
  .primary-btn:disabled { cursor: not-allowed; opacity: .45; }

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
  .result-card h3 { margin: 10px 0 10px; font-size: 15px; }

  .result-actions {
    display: grid;
    grid-template-columns: 1fr auto;
    gap: 8px;
    margin-top: 16px;
  }
  .small-btn { padding: 11px 12px; background: var(--accent); color: var(--black); }
  .small-btn.secondary {
    border: 1px solid #4b4b4b;
    background: transparent;
    color: var(--white);
  }

  @media (max-width: 1080px) {
    .workspace { grid-template-columns: 190px minmax(0, 1fr); }
    .action-column { grid-column: 1 / -1; border-top: 1px solid var(--line); }
    .file-column { grid-row: 1; }
    .viewer-column { grid-row: 1; border-right: 0; }
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
    .file-tree {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 4px;
    }
    .tree-row { min-height: 34px; border: 1px solid #2f2f2f; }
    .depth-1 .path, .depth-2 .path { padding-left: 0; }
    .action-column { display: block; }
    .prompt-block { padding-top: 22px; }
    .api-strip { flex-direction: column; gap: 4px; }
    .result-actions { grid-template-columns: 1fr; }
  }
`;

export default function ProblemDetailPage() {
  const [prompt, setPrompt] = useState(
    'Post 엔티티와 게시글 CRUD API를 만들어줘. 제목과 본문은 필수로 처리하고, 컨트롤러와 서비스 계층을 분리해줘.',
  );
  const [isRunning, setIsRunning] = useState(false);
  const [hasRunResult, setHasRunResult] = useState(false);
  const [selectedFile, setSelectedFile] = useState('src/main/java/App.java');
  const [status, setStatus] = useState<StatusMessage | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const visibleTreeItems = useMemo(
    () => treeItems.filter((item) => !item.resultOnly || hasRunResult),
    [hasRunResult],
  );

  const activeFiles = hasRunResult ? resultFiles : originalFiles;
  const selectedCode = activeFiles[selectedFile] ?? '// Folder selected';

  const showStatus = (message: string, type: StatusType = 'normal') => {
    setStatus({ message, type });
  };

  const completeRun = () => {
    const savedRun: SavedRun = {
      problemId: 1,
      prompt,
      passedTests: 7,
      totalTests: 10,
    };

    localStorage.setItem('promptPracticeRun', JSON.stringify(savedRun));
    setHasRunResult(true);
    setIsRunning(false);
    setSelectedFile('src/main/java/App.java');
    showStatus('200 OK · 생성된 코드를 실행하고 테스트 결과를 수신했습니다.');
  };

  const startRun = () => {
    const trimmedPrompt = prompt.trim();

    if (!trimmedPrompt) {
      showStatus('422 invalid-prompt · 프롬프트를 입력해주세요.', 'error');
      return;
    }

    if (hasRunResult) {
      const confirmed = window.confirm(
        '새로 실행하면 현재 결과가 사라지고 깨끗한 스켈레톤에서 다시 시작합니다. 계속할까요?',
      );
      if (!confirmed) return;
    }

    setHasRunResult(false);
    setIsRunning(true);
    setSelectedFile('src/main/java/App.java');
    showStatus('POST /run 요청 처리 중 · 생성된 코드와 테스트를 실행하고 있습니다.');
    window.setTimeout(completeRun, 1100);
  };

  const submitForFeedback = () => {
    setIsSubmitting(true);
    showStatus('POST /submit 요청 중 · 테스트 결과를 피드백 페이지로 전달합니다.');
    window.setTimeout(() => {
      window.location.href = '/feedback/1';
    }, 750);
  };

  return (
    <>
      <style>{styles}</style>

      <header className="site-header">
        <a className="logo" href="/problems">
          prompt<i>.</i>practice
        </a>
        <div className="header-title">게시판 API 구현 / PROBLEM 01</div>
      </header>

      <main className="workspace">
        <aside className="column file-column">
          <div className="label">PROJECT / FILE TREE</div>
          <h2 className="section-title">board-api</h2>

          <div className="file-tree">
            {visibleTreeItems.map((item) => {
              const isAppFile = item.path === 'src/main/java/App.java';
              const changeType = hasRunResult && isAppFile ? 'M' : item.changeType;
              const isActive = !item.folder && selectedFile === item.path;

              return (
                <button
                  className={`tree-row depth-${item.depth}${isActive ? ' active' : ''}`}
                  disabled={item.folder}
                  key={`${item.path}-${item.label}`}
                  onClick={() => {
                    if (!item.folder) setSelectedFile(item.path);
                  }}
                  type="button"
                >
                  <span className="path">{item.label}</span>
                  <span
                    className={`change${changeType === 'A' ? ' added' : ''}${
                      changeType === 'M' ? ' modified' : ''
                    }`}
                  >
                    {changeType ?? ''}
                  </span>
                </button>
              );
            })}
          </div>

          <div className="legend">
            <span>A / ADDED</span>
            <span>M / MODIFIED</span>
          </div>
        </aside>

        <section className="column viewer-column">
          <div className="viewer-top">
            <div>
              <div className="label">
                {selectedFile.split('/').pop()?.toUpperCase()} /{' '}
                {hasRunResult ? 'RUN RESULT' : 'SKELETON'}
              </div>
              <h1>
                Read first.<br />
                Prompt once.
              </h1>
            </div>
            <span className="read-only">READ ONLY</span>
          </div>

          <div className="code-shell">
            <div className="code-head">
              <span>{selectedFile}</span>
              <span>{hasRunResult ? 'RESULT' : 'ORIGINAL'}</span>
            </div>
            <pre>
              <code>{selectedCode}</code>
            </pre>
          </div>

          <div className="api-strip">
            <span>DETAIL / GET /api/problems/1</span>
            <span>
              REPOSITORY / {hasRunResult ? 'RUN RESULT IN CLIENT' : 'CLEAN SKELETON'}
            </span>
          </div>
        </section>

        <aside className="column action-column">
          <section className="mission-card">
            <div className="label">CURRENT PROBLEM</div>
            <h2>게시판 API 구현</h2>
            <p>
              게시글 엔티티와 CRUD API 계층을 구현하세요. 각 실행은 원본
              스켈레톤에서 새로 시작하며 코드는 직접 편집할 수 없습니다.
            </p>
            <div className="chips">
              <span className="chip">JAVA</span>
              <span className="chip">CRUD</span>
              <span className="chip">READ ONLY</span>
              <span className="chip">STATELESS</span>
            </div>
          </section>

          <section className="prompt-block">
            <div className="label">PROMPT / MAX 4,000</div>
            <textarea
              disabled={isRunning}
              maxLength={4000}
              onChange={(event) => setPrompt(event.target.value)}
              value={prompt}
            />
            <div className="prompt-meta">
              <span>POST /api/problems/1/run</span>
              <span>
                <b>{prompt.length.toLocaleString('ko-KR')}</b> / 4,000
              </span>
            </div>

            <button
              className="primary-btn"
              disabled={isRunning}
              onClick={startRun}
              type="button"
            >
              {isRunning ? 'RUNNING…' : 'RUN PROMPT ↗'}
            </button>

            {status && (
              <div className={`status${status.type === 'error' ? ' error' : ''}`} role="status">
                {status.message}
              </div>
            )}

            {hasRunResult && (
              <div className="result-card">
                <div className="label">AI RESPONSE / RUN COMPLETE</div>
                <h3>코드 생성과 테스트 실행이 완료되었습니다.</h3>
                <p className="result-copy">
                  생성된 코드를 기준으로 테스트를 실행했습니다. 제출하면 통과한
                  테스트 케이스 개수와 프롬프트 피드백을 확인할 수 있습니다.
                </p>
                <div className="result-actions">
                  <button
                    className="small-btn"
                    disabled={isSubmitting}
                    onClick={submitForFeedback}
                    type="button"
                  >
                    {isSubmitting ? 'SUBMITTING…' : 'SUBMIT FOR FEEDBACK ↗'}
                  </button>
                  <button className="small-btn secondary" onClick={startRun} type="button">
                    RE-RUN
                  </button>
                </div>
              </div>
            )}
          </section>
        </aside>
      </main>
    </>
  );
}
