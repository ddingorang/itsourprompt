export interface ProblemDetails {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
}

export class ApiProblemError extends Error {
  readonly problem: ProblemDetails;

  constructor(problem: ProblemDetails) {
    super(problem.detail || problem.title);
    this.name = 'ApiProblemError';
    this.problem = problem;
  }
}

const API_BASE_PATH = import.meta.env.VITE_API_BASE_URL ?? '/api';

function createHeaders(init?: RequestInit): Headers {
  const headers = new Headers(init?.headers);

  if (init?.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  headers.set('Accept', 'application/json, application/problem+json');
  return headers;
}

async function parseProblem(response: Response): Promise<ProblemDetails> {
  try {
    const data = (await response.json()) as Partial<ProblemDetails>;

    return {
      type: data.type ?? 'about:blank',
      title: data.title ?? 'Request failed',
      status: data.status ?? response.status,
      detail: data.detail ?? `요청 처리에 실패했습니다. (${response.status})`,
      instance: data.instance,
    };
  } catch {
    return {
      type: 'about:blank',
      title: 'Request failed',
      status: response.status,
      detail: `요청 처리에 실패했습니다. (${response.status})`,
    };
  }
}

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_PATH}${path}`, {
    ...init,
    headers: createHeaders(init),
  });

  if (!response.ok) {
    throw new ApiProblemError(await parseProblem(response));
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}
