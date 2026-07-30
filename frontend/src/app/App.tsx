import { RouterProvider } from 'react-router-dom'

import { AuthProvider } from '../features/auth/AuthContext'
import { router } from './router'

function App() {
  return (
    // AuthProvider가 라우터 바깥에 있어 모든 페이지가 로그인 상태(useAuth)를 공유한다.
    // (Provider 내부에서는 라우터 훅을 쓰지 않으므로 이 배치가 안전하다)
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>
  )
}

export default App