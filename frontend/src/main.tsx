import { Prism } from 'prism-react-renderer'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import App from './app/App'
import './index.css'

// prism-react-renderer 번들에는 java 문법이 없다 — 전역에 Prism을 노출한 뒤
// prismjs의 java 문법을 사이드로드한다(prism-react-renderer README 공식 방법).
// 정적 import는 호이스팅 때문에 전역 할당보다 먼저 실행돼 깨진다.
;(globalThis as { Prism?: typeof Prism }).Prism = Prism
// 이 import는 최상위 await이라, 거부되면 아래 render가 아예 실행되지 않는다 —
// 배포 직후 옛 index.html이 사라진 청크를 가리키기만 해도 화면이 통째로 빈다.
// 문법 하나를 못 받은 대가로 앱을 못 띄울 이유는 없어, 삼키고 계속 띄운다.
// 이때 Prism.languages.java가 비어 Highlight가 평문 경로로 떨어진다(강조만 없음).
await import('prismjs/components/prism-java').catch((error: unknown) => {
  console.warn('java 문법을 불러오지 못해 java 코드는 강조 없이 표시됩니다.', error)
})

const rootElement = document.getElementById('root')

if (!rootElement) {
  throw new Error('index.html에서 root 요소를 찾지 못했습니다.')
}

createRoot(rootElement).render(
  <StrictMode>
    <App />
  </StrictMode>,
)