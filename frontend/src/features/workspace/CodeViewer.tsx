import { Highlight } from 'prism-react-renderer';
import { Fragment, useState } from 'react';
import type { CSSProperties } from 'react';

import type { DiffLineStatus, LineDiff } from './diff';
import { codeViewerTheme, languageForPath } from './highlight';

// diff 상태별 장식. 문자열 앞 공백은 기존 클래스 뒤에 이어 붙이기 위한 것이고,
// unchanged는 배경·기호 없이 막대 자리만 투명하게 잡아 줄 간 정렬을 지킨다.
const DIFF_ROW_BG: Record<DiffLineStatus, string> = {
  unchanged: '',
  added: ' bg-[var(--code-diff-added-bg)]',
  changed: ' bg-[var(--code-diff-changed-bg)]',
};

const DIFF_GUTTER_BAR: Record<DiffLineStatus, string> = {
  unchanged: ' border-l-2 border-l-transparent',
  added: ' border-l-2 border-l-[var(--code-diff-added-bar)]',
  changed: ' border-l-2 border-l-[var(--code-diff-changed-bar)]',
};

const DIFF_SIGN: Record<DiffLineStatus, string> = {
  unchanged: '',
  added: '+',
  changed: '~',
};

const DIFF_SIGN_COLOR: Record<DiffLineStatus, string> = {
  unchanged: '',
  added: ' text-[var(--code-diff-added-bar)]',
  changed: ' text-[var(--code-diff-changed-bar)]',
};

interface CodeViewerProps {
  code: string;
  /** 확장자로 강조 문법을 고른다. 없거나 모르는 확장자면 강조 없이 그린다. */
  path?: string;
  /** 줄번호 표시. 기본 켬. */
  showLineNumbers?: boolean;
  /** 줄번호 칸 너비. 기본 2.0rem(문제 상세·피드백), 릴레이는 2.5rem. */
  gutterWidth?: string;
  /** 줄 단위 diff 강조. 넘기지 않으면 렌더 결과가 기존과 완전히 동일하다. */
  diff?: LineDiff;
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
  diff,
}: CodeViewerProps) {
  // 펼쳐 둔 삭제 마커의 anchor. 파일을 바꾸면 호출부가 key로 리셋한다.
  const [expandedDeletions, setExpandedDeletions] = useState<Set<number>>(new Set());

  const toggleDeletion = (anchor: number) => {
    setExpandedDeletions((current) => {
      const next = new Set(current);
      if (next.has(anchor)) next.delete(anchor);
      else next.add(anchor);
      return next;
    });
  };

  /**
   * 그 자리에서 사라진 줄 묶음. 코드 줄이 아닌 별도 행이라 줄번호를 붙이지 않는다 —
   * 실제 파일 줄번호가 밀리지 않게 하는 핵심이다.
   */
  const renderDeletedRows = (anchor: number, lines: string[]) => {
    const expanded = expandedDeletions.has(anchor);
    // 줄번호를 끈 호출부(릴레이)에는 gutter 셀이 없어 색막대도 생략된다.
    const rowClassName = showLineNumbers
      ? 'grid min-h-[1.9em] grid-cols-[var(--code-viewer-gutter-width)_max-content] bg-[var(--code-diff-deleted-bg)]'
      : 'min-h-[1.9em] bg-[var(--code-diff-deleted-bg)]';
    const gutterClassName =
      'sticky left-0 border-r border-r-[var(--code-viewer-gutter-border)] border-l-2 border-l-[var(--code-diff-deleted-bar)] bg-[var(--code-viewer-gutter-bg)] select-none';

    return (
      <>
        <div className={rowClassName}>
          {showLineNumbers && <span aria-hidden="true" className={gutterClassName} />}
          <button
            aria-expanded={expanded}
            className="cursor-pointer border-0 bg-transparent p-0 pr-5 pl-1.5 text-left text-[var(--code-diff-deleted-bar)]"
            onClick={() => toggleDeletion(anchor)}
            type="button"
          >
            {expanded ? '▾' : '▸'} {lines.length}줄 삭제됨
          </button>
        </div>
        {expanded &&
          lines.map((deletedLine, deletedIndex) => (
            <div className={rowClassName} key={deletedIndex}>
              {showLineNumbers && <span aria-hidden="true" className={gutterClassName} />}
              {/* 삭제된 줄은 구문 강조 없는 평문이다. 들여쓰기는 컨테이너의 whitespace-pre가 지킨다. */}
              <code className="pr-5 pl-1.5 opacity-70">
                <span aria-hidden="true" className="inline-block w-3.5 select-none" />
                {deletedLine}
              </code>
            </div>
          ))}
      </>
    );
  };

  return (
    <Highlight code={code} language={languageForPath(path)} theme={codeViewerTheme}>
      {({ tokens, getTokenProps }) => (
        <div
          className="code-viewer min-w-max py-3 font-mono text-xs leading-[1.9] whitespace-pre text-[var(--code-viewer-text)] [tab-size:2]"
          style={{ '--code-viewer-gutter-width': gutterWidth } as CSSProperties}
        >
          {tokens.map((line, index) => {
            // diff를 안 켠 호출부에서는 아래 클래스 문자열이 전부 기존과 바이트 동일해야 한다.
            const status = diff ? (diff.statuses[index] ?? 'unchanged') : null;
            return (
              <Fragment key={index}>
                {diff?.deletions.has(index) &&
                  renderDeletedRows(index, diff.deletions.get(index) ?? [])}
                <div
                  className={
                    (showLineNumbers
                      ? 'grid min-h-[1.9em] grid-cols-[var(--code-viewer-gutter-width)_max-content]'
                      : 'min-h-[1.9em]') + (status ? DIFF_ROW_BG[status] : '')
                  }
                >
                  {showLineNumbers && (
                    <span
                      aria-hidden="true"
                      className={
                        'sticky left-0 border-r border-[var(--code-viewer-gutter-border)] bg-[var(--code-viewer-gutter-bg)] pr-3 text-right text-[var(--code-viewer-gutter-text)] select-none' +
                        (status ? DIFF_GUTTER_BAR[status] : '')
                      }
                    >
                      {index + 1}
                    </span>
                  )}
                  <code className={status ? 'pr-5 pl-1.5' : 'px-5'}>
                    {status && (
                      <span
                        aria-hidden="true"
                        className={'inline-block w-3.5 select-none' + DIFF_SIGN_COLOR[status]}
                      >
                        {DIFF_SIGN[status]}
                      </span>
                    )}
                    {line.map((token, tokenIndex) => (
                      <span key={tokenIndex} {...getTokenProps({ token })} />
                    ))}
                  </code>
                </div>
              </Fragment>
            );
          })}
          {/* 파일 끝에서 사라진 줄. anchor가 tokens.length면 붙일 코드 줄이 없다. */}
          {diff?.deletions.has(tokens.length) &&
            renderDeletedRows(tokens.length, diff.deletions.get(tokens.length) ?? [])}
        </div>
      )}
    </Highlight>
  );
}
