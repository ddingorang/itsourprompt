import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

interface PromptFeedbackProps {
  feedback: string
}

export default function PromptFeedback({
  feedback,
}: PromptFeedbackProps) {
  return (
    <div className="leading-[1.75] text-[#f5f5f0] [word-break:keep-all] [&>h2:first-child]:mt-[18px] [&>h2:first-child]:border-t [&>h2:first-child]:border-[#444] [&>h2:first-child]:pt-[18px]">
      <ReactMarkdown
        components={{
          h2: ({ children }) => (
            <h2 className="mt-8 mb-4 text-[22px] font-bold text-[#d6ff50]">
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
            <strong className="font-bold text-white">{children}</strong>
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
        }}
        remarkPlugins={[remarkGfm]}
      >
        {feedback}
      </ReactMarkdown>
    </div>
  )
}
