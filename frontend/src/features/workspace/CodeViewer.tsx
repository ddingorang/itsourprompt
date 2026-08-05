import { Highlight } from 'prism-react-renderer';
import type { CSSProperties } from 'react';

import { codeViewerTheme, languageForPath } from './highlight';

interface CodeViewerProps {
  code: string;
  /** 확장자로 강조 문법을 고른다. 없거나 모르는 확장자면 강조 없이 그린다. */
  path?: string;
  /** 줄번호 표시. 기본 켬. */
  showLineNumbers?: boolean;
  /** 줄번호 칸 너비. 기본 2.0rem(문제 상세·피드백), 릴레이는 2.5rem. */
  gutterWidth?: string;
}

/**
 * 읽기 전용 코드 뷰어. 스크롤 컨테이너·테두리·배경은 호출부가 유지하고, 색은
 * index.css의 .code-viewer 블록이 페이지별 변수로 alias한다.
 */
export default function CodeViewer({
  code,
  path,
  showLineNumbers = true,
  gutterWidth = '2.0rem',
}: CodeViewerProps) {
  return (
    <Highlight code={code} language={languageForPath(path)} theme={codeViewerTheme}>
      {({ tokens, getTokenProps }) => (
        <div
          className="code-viewer min-w-max py-3 font-mono text-xs leading-[1.9] whitespace-pre text-[var(--code-viewer-text)] [tab-size:2]"
          style={{ '--code-viewer-gutter-width': gutterWidth } as CSSProperties}
        >
          {tokens.map((line, index) => (
            <div
              className={
                showLineNumbers
                  ? 'grid min-h-[1.9em] grid-cols-[var(--code-viewer-gutter-width)_max-content]'
                  : 'min-h-[1.9em]'
              }
              key={index}
            >
              {showLineNumbers && (
                <span
                  aria-hidden="true"
                  className="sticky left-0 border-r border-[var(--code-viewer-gutter-border)] bg-[var(--code-viewer-gutter-bg)] pr-3 text-right text-[var(--code-viewer-gutter-text)] select-none"
                >
                  {index + 1}
                </span>
              )}
              <code className="px-5">
                {line.map((token, tokenIndex) => (
                  <span key={tokenIndex} {...getTokenProps({ token })} />
                ))}
              </code>
            </div>
          ))}
        </div>
      )}
    </Highlight>
  );
}
