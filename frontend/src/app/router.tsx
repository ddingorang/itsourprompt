import { createBrowserRouter } from 'react-router-dom'

import HomePage from '../pages/HomePage'
import ProblemListPage from '../pages/ProblemListPage'
import ProblemDetailPage from '../pages/ProblemDetailPage'
import FeedbackPage from '../pages/FeedbackPage'
import ErrorPage from '../pages/ErrorPage'
import MyPage from '../pages/MyPage'
import AuthPlaceholderPage from '../pages/AuthPlaceholderPage'

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
    element: <MyPage />,
  },
  {
    path: '/login',
    element: <AuthPlaceholderPage title="LOGIN" />,
  },
  {
    path: '/signup',
    element: <AuthPlaceholderPage title="SIGN UP" />,
  },
])
