import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

interface PromptFeedbackProps {
  feedback: string
}

/**
 * 백엔드가 총평 끝에 붙이는 출처 한 줄(PatternPrompts.SOURCE_NOTE)은 주소를 날것으로
 * 노출한다. 이름을 입힌 링크로 바꿔 그린다.
 *
 * 백엔드 문구가 바뀌면 이 치환은 조용히 지나가고 원문이 그대로 보인다 — 링크가 덜
 * 예뻐질 뿐 정보는 남는다. 백엔드가 마크다운 링크로 보내 주기 시작하면 이 함수를 지우면 된다.
 */
const SOURCE_NOTE_PATTERN =
  /여기 쓴 용어는 AI Coding Dictionary에서 가져왔어요\.\s*https:\/\/aicodingdictionary\.com/g

function withNamedDictionaryLink(markdown: string): string {
  return markdown.replace(
    SOURCE_NOTE_PATTERN,
    '용어 출처: [AI Coding Dictionary ↗](https://aicodingdictionary.com)',
  )
}

export default function PromptFeedback({
  feedback,
}: PromptFeedbackProps) {
  // 마지막 구분선과 그 뒤 한 줄은 본문 끝에 붙는 용어 출처다 — 본문 문단과 같은
  // 여백·색을 쓰면 읽을 거리처럼 보인다. 간격을 좁히고 링크까지 회색으로 낮춘다.
  return (
    <div className="leading-[1.75] text-[var(--feedback-text)] [word-break:keep-all] [&>h2:first-child]:mt-[18px] [&>h2:first-child]:border-t [&>h2:first-child]:border-[var(--feedback-border)] [&>h2:first-child]:pt-[18px] [&>hr:last-of-type]:my-3 [&>hr:last-of-type+p]:mt-0 [&>hr:last-of-type+p]:-mb-4 [&>hr:last-of-type+p]:text-[13px] [&>hr:last-of-type+p]:text-[var(--feedback-muted)] [&>hr:last-of-type+p_a]:text-[var(--feedback-muted)]">
      <ReactMarkdown
        components={{
          h2: ({ children }) => (
            <h2 className="mt-8 mb-4 text-[22px] font-bold text-[var(--feedback-acid)]">
              {children}
            </h2>
          ),
          h3: ({ children }) => (
            <h3 className="mt-6 mb-3 text-lg font-bold">{children}</h3>
          ),
          p: ({ children }) => <p className="my-3">{children}</p>,
          ul: ({ children }) => (
            <ul className="my-3 list-disc pl-6">{children}</ul>
          ),
          ol: ({ children }) => (
            <ol className="my-3 list-decimal pl-6">{children}</ol>
          ),
          li: ({ children }) => <li className="my-2">{children}</li>,
          strong: ({ children }) => (
            <strong className="font-bold text-[var(--feedback-text)]">{children}</strong>
          ),
          code: ({ children }) => (
            <code className="rounded-none border-0 bg-transparent p-0">
              {children}
            </code>
          ),
          pre: ({ children }) => (
            <pre className="max-w-full overflow-x-auto whitespace-pre-wrap [overflow-wrap:anywhere] [&_code]:whitespace-inherit [&_code]:break-words">
              {children}
            </pre>
          ),
          // Tailwind preflight가 a의 색과 밑줄을 지워 링크가 본문과 구분되지 않는다.
          a: ({ children, href }) => (
            <a
              className="text-[var(--feedback-acid)] underline"
              href={href}
              rel="noreferrer"
              target="_blank"
            >
              {children}
            </a>
          ),
          // preflight의 hr은 색이 currentColor라 페이지의 다른 구분선보다 진하다.
          hr: () => (
            <hr className="my-6 border-[var(--feedback-border)]" />
          ),
          blockquote: ({ children }) => (
            <blockquote className="my-4 border-0 border-l-2 border-[var(--feedback-border)] pl-4">
              {children}
            </blockquote>
          ),
          // 표는 셀 수만큼 넓어져 본문 폭을 밀어낸다 — 표만 따로 가로 스크롤시킨다.
          table: ({ children }) => (
            <div className="my-4 max-w-full overflow-x-auto">
              <table className="w-full border-collapse text-sm">{children}</table>
            </div>
          ),
          th: ({ children }) => (
            <th className="border border-[var(--feedback-border)] bg-[var(--feedback-surface)] px-3 py-2 text-left font-bold text-[var(--feedback-text)]">
              {children}
            </th>
          ),
          td: ({ children }) => (
            <td className="border border-[var(--feedback-border)] px-3 py-2 align-top">
              {children}
            </td>
          ),
        }}
        remarkPlugins={[remarkGfm]}
      >
        {withNamedDictionaryLink(feedback)}
      </ReactMarkdown>
    </div>
  )
}
