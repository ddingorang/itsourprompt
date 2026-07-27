import { createBrowserRouter } from 'react-router-dom'

import HomePage from '../pages/HomePage'
import ProblemDetailPage from '../pages/ProblemDetailPage'
import FeedbackPage from '../pages/FeedbackPage'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <HomePage />,
  },
  {
    path: '/problems',
    element: <HomePage />,
  },
  {
    path: '/problems/:problemId',
    element: <ProblemDetailPage />,
  },
  {
    path: '/feedback/:problemId',
    element: <FeedbackPage />,
  },
])
