import { createBrowserRouter } from 'react-router-dom'

import ProtectedRoute from '../features/auth/ProtectedRoute'
import HomePage from '../pages/HomePage'
import ProblemListPage from '../pages/ProblemListPage'
import ProblemDetailPage from '../pages/ProblemDetailPage'
import RankingPage from '../pages/RankingPage'
import FeedbackPage from '../pages/FeedbackPage'
import ErrorPage from '../pages/ErrorPage'
import MyPage from '../pages/MyPage'
import LoginPage from '../pages/LoginPage'
import SignupPage from '../pages/SignupPage'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <HomePage />,
  },
  {
    path: '/problems',
    element: <ProblemListPage />,
  },
<<<<<<< HEAD
=======
  // 어느 문제의 랭킹을 볼지는 경로가 아니라 쿼리(?problem=N)에 담는다 — 문제 탭이
  // 곧 주소 이동이라 탭을 바꾸면 page 파라미터가 자연히 떨어진다.
  {
    path: '/ranking',
    element: <RankingPage />,
  },
>>>>>>> 3b1e1afdf2a7e0bcbeaf4a55469af2bcf0b9d371
  // 어템프트 시작 전(문제 스켈레톤)과 진행 중(어템프트)은 같은 화면이고 주소만 다르다.
  // 두 라우트를 최상위 형제로 나란히 두는 것이 중요하다 — react-router는 라우트
  // element에 key를 붙이지 않으므로 React가 같은 트리 위치·같은 컴포넌트 타입으로 보고
  // 재조정한다. 덕분에 첫 실행에서 /problems/5 → /attempts/123으로 주소를 바꿔도
  // 리마운트가 없어 수 분짜리 AI 요청이 끊기지 않는다.
  // 둘 중 하나를 layout으로 감싸거나 key를 붙이면 이 성질이 조용히 깨진다.
  {
    path: '/problems/:problemId',
    element: <ProblemDetailPage />,
  },
  {
    path: '/attempts/:attemptId',
    element: <ProblemDetailPage />,
  },
  {
    path: '/attempts/:attemptId/feedback',
    element: <FeedbackPage />,
  },
  {
    path: '/error',
    element: <ErrorPage />,
  },
  {

    path: '/my',
    element: (
      <ProtectedRoute>
        <MyPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/signup',
    element: <SignupPage />,
  },
])
