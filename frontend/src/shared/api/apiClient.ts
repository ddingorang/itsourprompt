/**
 * 백엔드 공통 HTTP 클라이언트.
 *
 * 백엔드는 모든 오류를 GlobalExceptionHandler에서 ApiErrorResponse
 * `{ code, message }` 형태로 내려준다(RFC7807 problem+json이 아니다).
 * 따라서 오류 판별은 HTTP status와 함께 `code`로 한다.
 *
 * 인증은 세션 + HttpOnly 쿠키(JSESSIONID) 방식이라 토큰을 다루는 코드가 없다.
 * 대신 모든 요청에 credentials를 실어 보내야 쿠키가 오간다.
 */

/** 백엔드 ApiErrorResponse와 1:1 대응. */
export interface ApiErrorBody {
  code: string;
  message: string;
}

/**
 * 백엔드가 알려진 오류 코드로 응답했을 때 던지는 예외.
 *
 * `code`는 화면 분기(예: attempt-already-submitted면 제출 버튼을 잠금)에,
 * `message`는 사용자에게 그대로 보여주는 용도로 쓴다 — 백엔드가 한국어
 * 메시지를 내려주므로 별도 번역이 필요 없다.
 */
export class ApiError extends Error {
  readonly code: string;
  readonly status: number;

  constructor(status: number, body: ApiErrorBody) {
    super(body.message);
    this.name = 'ApiError';
    this.code = body.code;
    this.status = status;
  }
}

/** 백엔드에서 실제로 내려오는 오류 코드 목록. */
export const API_ERROR_CODES = {
  aiProviderError: 'ai-provider-error',
  attemptAlreadySubmitted: 'attempt-already-submitted',
  attemptHasNoTurns: 'attempt-has-no-turns',
  attemptNotFound: 'attempt-not-found',
  badCredentials: 'bad-credentials',
  duplicateEmail: 'duplicate-email',
  duplicateRequest: 'duplicate-request',
  duplicateUser: 'duplicate-user',
  duplicateUsername: 'duplicate-username',
  feedbackInProgress: 'feedback-in-progress',
  feedbackNotFound: 'feedback-not-found',
  feedbackTimeout: 'feedback-timeout',
  invalidRequest: 'invalid-request',
  problemInactive: 'problem-inactive',
  problemNotFound: 'problem-not-found',
  runTimeout: 'run-timeout',
  unauthenticated: 'unauthenticated',
} as const;

/**
 * API 경로. 기본값은 상대 경로 '/api'로, 페이지의 오리진과 스킴을 그대로 물려받는다.
 *
 * 절대 URL을 넣으면 세 가지가 함께 따라온다 — HTTPS 페이지에서 http를 부르면 브라우저가
 * mixed content로 차단하고(Postman은 이 규칙이 없어 정상 응답이 온다), 오리진이 달라지면
 * 백엔드 CORS 허용 목록이 필요하고, 세션 쿠키는 SameSite=None; Secure 없이는 전송되지 않는다.
 * 프론트와 백엔드가 같은 호스트에 배포되는 한 상대 경로가 이 셋을 모두 없앤다.
 *
 * ?? 가 아니라 || 인 이유: Dockerfile의 ARG를 주지 않으면 ENV가 빈 문자열로 정의되어
 * 이 값이 ''가 된다. ?? 는 ''를 통과시켜 '/api'가 빠진 요청(/problems 등)을 만들고,
 * 그러면 nginx의 try_files가 SPA HTML을 200으로 돌려줘 원인을 찾기 어려워진다.
 */
const API_BASE_PATH = import.meta.env.VITE_API_BASE_URL?.trim() || '/api';

export interface ApiRequestInit extends RequestInit {
  /**
   * 중복 요청 방지 키. AI를 호출하는 엔드포인트(어템프트 생성/턴 추가)에서
   * 같은 키로 재요청하면 백엔드가 AI를 다시 부르지 않고 기존 결과를 돌려준다.
   */
  idempotencyKey?: string;
}

function createHeaders(init?: ApiRequestInit): Headers {
  const headers = new Headers(init?.headers);

  if (init?.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  if (init?.idempotencyKey) {
    headers.set('Idempotency-Key', init.idempotencyKey);
  }

  headers.set('Accept', 'application/json');
  return headers;
}

async function parseError(response: Response): Promise<ApiErrorBody> {
  try {
    const data = (await response.json()) as Partial<ApiErrorBody>;

    if (data.code || data.message) {
      return {
        code: data.code ?? 'unknown-error',
        message: data.message ?? `요청 처리에 실패했습니다. (${response.status})`,
      };
    }
  } catch {
    // 본문이 비어 있거나 JSON이 아닌 경우(프록시 오류, 502 HTML 등)로 넘어간다.
  }

  return {
    code: 'unknown-error',
    message: `요청 처리에 실패했습니다. (${response.status})`,
  };
}

export async function apiRequest<T>(path: string, init?: ApiRequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_PATH}${path}`, {
    ...init,
    // 세션 쿠키를 주고받기 위해 필수. 개발 환경은 Vite 프록시로 same-origin이지만,
    // VITE_API_BASE_URL로 다른 오리진을 가리키는 배포 환경에서도 동작해야 한다.
    credentials: 'include',
    headers: createHeaders(init),
  });

  if (!response.ok) {
    throw new ApiError(response.status, await parseError(response));
  }

  // 204(로그아웃) 및 본문 없는 200 응답 처리.
  if (response.status === 204 || response.headers.get('Content-Length') === '0') {
    return undefined as T;
  }

  return (await response.json()) as T;
}

/**
 * 새 로드가 시작돼 취소된 요청인지 본다. 화면이 스스로 끊은 요청이므로
 * 사용자에게 보여줄 오류가 아니다 — 조회 화면들이 catch에서 이걸로 걸러낸다.
 */
export function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError';
}

/** 새 Idempotency-Key를 만든다. crypto.randomUUID가 없는 환경도 대비한다. */
export function createIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}
