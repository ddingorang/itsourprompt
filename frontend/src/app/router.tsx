import { createBrowserRouter } from 'react-router-dom'

import ProblemListPage from '../pages/ProblemListPage'
import ProblemDetailPage from '../pages/ProblemDetailPage'
import FeedbackPage from '../pages/FeedbackPage'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <ProblemListPage />,
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
])