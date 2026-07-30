import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import App from './app/App'
import './index.css'

const rootElement = document.getElementById('root')

if (!rootElement) {
  throw new Error('index.html에서 root 요소를 찾지 못했습니다.')
}

createRoot(rootElement).render(
  <StrictMode>
    <App />
  </StrictMode>,
)