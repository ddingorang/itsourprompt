import { createBrowserRouter } from 'react-router-dom'

import ProtectedRoute from '../features/auth/ProtectedRoute'
import HomePage from '../pages/HomePage'
import ProblemListPage from '../pages/ProblemListPage'
import ProblemDetailPage from '../pages/ProblemDetailPage'
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
  {
    path: '/problems/:problemId',
    element: <ProblemDetailPage />,
  },
  {
    path: '/feedback/:problemId',
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
