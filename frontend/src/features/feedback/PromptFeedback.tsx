import './PromptFeedback.css'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

interface PromptFeedbackProps {
  feedback: string
}

export default function PromptFeedback({
  feedback,
}: PromptFeedbackProps) {
  return (
    <div className="feedback-markdown">
      <ReactMarkdown remarkPlugins={[remarkGfm]}>
        {feedback}
      </ReactMarkdown>
    </div>
  )
}