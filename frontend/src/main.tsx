import { Prism } from 'prism-react-renderer'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import App from './app/App'
import './index.css'

// prism-react-renderer 번들에는 java 문법이 없다 — 전역에 Prism을 노출한 뒤
// prismjs의 java 문법을 사이드로드한다(prism-react-renderer README 공식 방법).
// 정적 import는 호이스팅 때문에 전역 할당보다 먼저 실행돼 깨진다.
;(globalThis as { Prism?: typeof Prism }).Prism = Prism
await import('prismjs/components/prism-java')

const rootElement = document.getElementById('root')

if (!rootElement) {
  throw new Error('index.html에서 root 요소를 찾지 못했습니다.')
}

createRoot(rootElement).render(
  <StrictMode>
    <App />
  </StrictMode>,
)