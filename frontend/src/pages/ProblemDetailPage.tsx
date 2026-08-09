import {
  type KeyboardEvent,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import ReactMarkdown from 'react-markdown';
import { useNavigate, useParams } from 'react-router-dom';
import remarkGfm from 'remark-gfm';

import {
  addTurn,
  createAttempt,
  getAttempt,
  getCodeRun,
  getCodeRuns,
  requestCodeRun,
  submitAttempt,
} from '../features/attempt/api';
import { replayFiles } from '../features/attempt/replay';
import type {
  Attempt,
  ChangedFile,
  ChangeType,
  CodeRun,
  CodeRunCase,
  CodeRunCaseStatus,
  CodeRunStatus,
  CodeRunTally,
  TokenUsage,
  Turn,
} from '../features/attempt/types';
import { getProblemDetail } from '../features/problem/api';
import type { ProblemDetail, RepositoryFile } from '../features/problem/types';
import { useTheme } from '../features/theme/ThemeContext';
import CodeViewer from '../features/workspace/CodeViewer';
import { diffLines } from '../features/workspace/diff';
import { nextTabIndex } from '../shared/a11y/tabKeyboard';
import {
  ApiError,
  API_ERROR_CODES,
  createIdempotencyKey,
  isAbortError,
} from '../shared/api/apiClient';
import Button from '../shared/components/Button';
import Header from '../shared/components/Header';
import Spinner from '../shared/components/Spinner';
import type { ErrorPageState } from '../shared/types/error';

type StatusType = 'normal' | 'error';

interface StatusMessage {
  message: string;
  type: StatusType;
}

type DetailTab = 'problem' | 'logs' | 'test';

const detailTabs: ReadonlyArray<readonly [DetailTab, string]> = [
  ['problem', '문제'],
  ['logs', '프롬프트 기록'],
  ['test', '채점'],
];

interface FileTreeNode {
  name: string;
  path: string;
  type: 'folder' | 'file';
  children: FileTreeNode[];
  changeType?: ChangeType;
  deleted?: boolean;
}

const labelClasses =
  'font-mono text-sm leading-[1.5] font-bold tracking-[0.08em] text-[var(--problem-detail-label)]';

const koreanLabelClasses =
  'text-sm leading-[1.5] font-bold text-[var(--problem-detail-label)]';

const pageStateClasses =
  'grid min-h-dvh place-items-center bg-[var(--problem-detail-bg)] p-10 ' +
  'font-mono text-xs leading-[1.7] text-[var(--problem-detail-muted)]';

const changeColorClasses: Record<ChangeType, string> = {
  ADDED: 'text-[var(--problem-detail-acid)]',
  MODIFIED: 'text-[var(--problem-detail-text)]',
  DELETED: 'text-[#ff786b]',
};

const CODE_RUN_POLL_INTERVAL_MS = 1500;
const CODE_RUN_MAX_WAIT_MS = 120_000;

const codeRunStatusLabels: Record<CodeRunStatus, string> = {
  QUEUED: '채점 중',
  SUCCEEDED: '통과',
  COMPILE_ERROR: '컴파일 오류',
  TEST_FAILED: '테스트 실패',
  RUNTIME_ERROR: '실행 오류',
  TIMEOUT: '시간 초과',
  RUNNER_ERROR: '채점기 오류',
};

const codeRunCaseLabels: Record<CodeRunCaseStatus, string> = {
  PASSED: '통과',
  FAILED: '실패',
  ERROR: '오류',
  SKIPPED: '건너뜀',
};

const codeRunCaseColorClasses: Record<CodeRunCaseStatus, string> = {
  PASSED: 'text-[var(--problem-detail-acid)]',
  FAILED: 'text-[#ff786b]',
  ERROR: 'text-[#ffb86b]',
  SKIPPED: 'text-[var(--problem-detail-subtle)]',
};

interface ErrorInfo {
  message: string;
  status?: number;
  code?: string;
}

/**
 * 주소가 가리키는 대상을 열지 못했을 때 화면에 남기는 안내.
 * 에러 페이지로 보내는 대신 이 자리에서 사정과 갈 곳을 함께 보여준다.
 */
interface LoadError {
  message: string;
  actionLabel: string;
  actionTo: string;
}

/** 목록으로 돌려보내는 안내. 잘못된 주소는 사용자가 고칠 수 있는 게 없다. */
function toProblemsError(message: string): LoadError {
  return { actionLabel: '문제 목록으로 돌아가기 ↗', actionTo: '/problems', message };
}

function getErrorInfo(error: unknown, fallback: string): ErrorInfo {
  if (error instanceof ApiError) {
    return { code: error.code, message: error.message, status: error.status };
  }

  return { message: fallback };
}

/**
 * 라우트 파라미터를 ID로 읽는다. 양의 정수가 아니면 null이다 —
 * 잘못된 주소를 조용히 다른 문제로 떨어뜨리지 않기 위해서다.
 */
function parseRouteId(param: string | undefined): number | null {
  if (!param) return null;

  const id = Number(param);
  return Number.isInteger(id) && id > 0 ? id : null;
}

/** 마지막 턴의 변경 파일. 파일 탐색기에서 A/M/D 표시에 쓴다. */
function getLatestChangedFiles(turns: Turn[]): ChangedFile[] {
  return turns.length ? turns[turns.length - 1].changedFiles : [];
}

function findFile(files: RepositoryFile[], path: string): RepositoryFile | undefined {
  const normalizedPath = normalizeRepositoryPath(path);
  return files.find(
    (file) => normalizeRepositoryPath(file.path) === normalizedPath,
  );
}

function findPreviewHtml(files: RepositoryFile[]): RepositoryFile | undefined {
  return (
    findFile(files, 'index.html') ??
    files.find((file) => normalizeRepositoryPath(file.path).endsWith('/index.html'))
  );
}

function normalizeRepositoryPath(path: string): string {
  return path
    .replaceAll('\\', '/')
    .replace(/\/+/g, '/')
    .replace(/^\/+|\/+$/g, '');
}

function getTokenUsageTotal(usage?: TokenUsage | null): number | null {
  if (
    typeof usage?.inputTokens !== 'number' ||
    typeof usage.outputTokens !== 'number'
  ) {
    return null;
  }

  return usage.inputTokens + usage.outputTokens;
}

function formatUsageValue(value: unknown, suffix = ''): string {
  return typeof value === 'number' && Number.isFinite(value)
    ? `${value.toLocaleString('ko-KR')}${suffix}`
    : '—';
}

function tallyCodeRunCases(cases: CodeRunCase[]): CodeRunTally | null {
  if (cases.length === 0) return null;

  return cases.reduce<CodeRunTally>(
    (tally, testCase) => {
      tally.total += 1;
      if (testCase.status === 'PASSED') tally.passed += 1;
      else if (testCase.status === 'FAILED') tally.failed += 1;
      else if (testCase.status === 'ERROR') tally.error += 1;
      else tally.skipped += 1;
      return tally;
    },
    { error: 0, failed: 0, passed: 0, skipped: 0, total: 0 },
  );
}

/**
 * 기록된 실행 중 결과가 남은 마지막 것을 읽어 온다. 새 실행을 요청하지 않으므로
 * 쓰기가 막힌 자리(남의 제출)에서도 쓸 수 있다.
 *
 * 아직 채점 중(QUEUED)인 실행은 건너뛴다 — 보여 줄 결과가 아직 없고, 남의
 * 어템프트라 우리가 그것이 끝나기를 기다릴 이유도 없다. 되살릴 기록이 없으면 null.
 */
async function fetchRecordedCodeRun(
  attemptId: number,
  signal: AbortSignal,
): Promise<{ run: CodeRun; tally: CodeRunTally | null } | null> {
  const response = await getCodeRuns(attemptId, signal);
  const recorded = response.runs.find((run) => run.status !== 'QUEUED');
  if (!recorded) return null;

  const run = await getCodeRun(attemptId, recorded.runId, signal);
  return { run, tally: recorded.tally };
}

function createFileTree(
  files: RepositoryFile[],
  changedFiles: ChangedFile[],
): FileTreeNode[] {
  const changes = new Map(changedFiles.map((file) => [file.path, file.changeType]));
  const paths = new Set(files.map((file) => file.path));

  changedFiles
    .filter((file) => file.changeType === 'DELETED')
    .forEach((file) => paths.add(file.path));

  const root: FileTreeNode[] = [];

  [...paths].sort((a, b) => a.localeCompare(b)).forEach((path) => {
    const normalizedPath = normalizeRepositoryPath(path);
    const segments = normalizedPath.split('/').filter(Boolean);
    let children = root;

    segments.forEach((segment, index) => {
      const isFile = index === segments.length - 1;
      const nodePath = isFile
        ? path
        : segments.slice(0, index + 1).join('/');
      let node = children.find(
        (child) => child.name === segment && child.type === (isFile ? 'file' : 'folder'),
      );

      if (!node) {
        node = {
          name: segment,
          path: nodePath,
          type: isFile ? 'file' : 'folder',
          children: [],
          ...(isFile
            ? {
                changeType: changes.get(path),
                deleted: changes.get(path) === 'DELETED',
              }
            : {}),
        };
        children.push(node);
      }

      children = node.children;
    });
  });

  const sortNodes = (nodes: FileTreeNode[]) => {
    nodes.sort((a, b) => {
      if (a.type !== b.type) return a.type === 'folder' ? -1 : 1;
      return a.name.localeCompare(b.name);
    });
    nodes.forEach((node) => sortNodes(node.children));
  };

  sortNodes(root);
  return root;
}

function getFolderPaths(nodes: FileTreeNode[]): string[] {
  return nodes.flatMap((node) =>
    node.type === 'folder'
      ? [node.path, ...getFolderPaths(node.children)]
      : [],
  );
}

export default function ProblemDetailPage() {
  const { colorMode } = useTheme();
  const navigate = useNavigate();
  /**
   * 이 화면은 두 주소에서 열린다 — /problems/{id}(어템프트 시작 전)와
   * /attempts/{id}(진행 중). 어느 쪽인지는 URL에 attemptId가 있는지로 갈린다.
   */
  const { attemptId: attemptIdParam, problemId: problemIdParam } = useParams();
  const isAttemptRoute = attemptIdParam !== undefined;
  const routeAttemptId = parseRouteId(attemptIdParam);
  const routeProblemId = parseRouteId(problemIdParam);

  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  /**
   * 진행 중인 어템프트. 첫 프롬프트를 실행할 때 만들어지므로 그 전에는 null이고,
   * 이때 화면에는 문제 스켈레톤 파일을 보여준다.
   */
  const [attempt, setAttempt] = useState<Attempt | null>(null);
  const [prompt, setPrompt] = useState('');
  const [selectedFile, setSelectedFile] = useState('');
  const [activeTab, setActiveTab] = useState<DetailTab>('problem');
  const [expandedFolders, setExpandedFolders] = useState<Set<string>>(new Set());
  const [status, setStatus] = useState<StatusMessage | null>(null);
  const [loadError, setLoadError] = useState<LoadError | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRunning, setIsRunning] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [codeRun, setCodeRun] = useState<CodeRun | null>(null);
  const [codeRunTally, setCodeRunTally] = useState<CodeRunTally | null>(null);
  const [codeRunError, setCodeRunError] = useState<string | null>(null);
  const [isCodeRunLoading, setIsCodeRunLoading] = useState(false);
  const [isPreviewFullscreen, setIsPreviewFullscreen] = useState(false);
  const [previewSession, setPreviewSession] = useState(0);
  const detailTabRefs = useRef<
    Partial<Record<DetailTab, HTMLButtonElement | null>>
  >({});
  const previewContainerRef = useRef<HTMLDivElement | null>(null);
  const isRunPendingRef = useRef(false);
  const isCodeRunPendingRef = useRef(false);
  /**
   * 실패한 턴 요청의 Idempotency-Key를 기억한다. 같은 프롬프트로 다시 실행하면
   * 같은 키가 나가므로, 백엔드가 이미 AI를 호출해 둔 경우 재호출 없이 그 결과를
   * 돌려준다(AI 호출이 수 분 걸리므로 중복 호출 비용이 크다).
   */
  const pendingRunRef = useRef<{ prompt: string; key: string } | null>(null);
  /** 진행 중인 로드. 새 로드가 시작되면 이전 것을 끊어 마지막 응답만 화면에 남긴다. */
  const loadControllerRef = useRef<AbortController | null>(null);
  const codeRunControllerRef = useRef<AbortController | null>(null);
  const codeRunPollTimeoutRef = useRef<number | null>(null);
  const routeAttemptIdRef = useRef(routeAttemptId);
  const routeProblemIdRef = useRef(routeProblemId);
  routeAttemptIdRef.current = routeAttemptId;
  routeProblemIdRef.current = routeProblemId;
  /** 마지막으로 정상 반영된 라우트. 다른 주소의 로드 실패 시 이전 문제를 지우는 기준이다. */
  const loadedResourceRef = useRef<string | null>(null);
  /**
   * 지금 화면에 올라와 있는 문제. problem state와 같은 값이지만, loadFromRoute가
   * 렌더마다 새로 만들어져 낡은 state를 붙들 수 있어 ref로 따로 들고 읽는다.
   */
  const problemRef = useRef<ProblemDetail | null>(null);

  const files = attempt?.files ?? problem?.files ?? [];
  const isGame = problem?.type === 'game';
  const isPlaying = isGame && activeTab === 'test';
  const previewHtml = useMemo(
    () => findPreviewHtml(files)?.content ?? null,
    [files],
  );
  const turns = attempt?.turns ?? [];
  const attemptTokenUsage = getTokenUsageTotal(attempt?.usage);
  const turnTokenUsages = turns.map((turn) => getTokenUsageTotal(turn.usage));
  const hasTokenUsage =
    attemptTokenUsage !== null || turnTokenUsages.some((usage) => usage !== null);
  const totalTokenUsage =
    attemptTokenUsage ??
    turnTokenUsages.reduce<number>((total, usage) => total + (usage ?? 0), 0);
  const isSubmitted = attempt?.status === 'SUBMITTED';
  /**
   * 남이 제출한 어템프트를 읽는 중인지. 제3자가 읽을 수 있는 것은 제출된 어템프트뿐이라
   * 프롬프트·제출 UI는 이미 isSubmitted가 막고 있다. 남은 자리는 코드 실행뿐이다 —
   * 실행은 제출 뒤에도 주인에게 열려 있어 isSubmitted로는 가릴 수 없다.
   */
  // mine을 싣지 않는 백엔드가 붙어 있으면 undefined가 온다 — 그때 !mine으로 판정하면
  // 자기 풀이가 전부 남의 것으로 잠긴다. 남의 것이라고 명시(false)될 때만 잠근다.
  const isOthersAttempt = attempt !== null && attempt.mine === false;
  const canSubmit = turns.length > 0 && !isSubmitted;
  const visibleCodeRunTally =
    codeRunTally && codeRunTally.total > 0 ? codeRunTally : null;

  const stopCodeRunPolling = () => {
    codeRunControllerRef.current?.abort();
    codeRunControllerRef.current = null;
    if (codeRunPollTimeoutRef.current !== null) {
      window.clearTimeout(codeRunPollTimeoutRef.current);
      codeRunPollTimeoutRef.current = null;
    }
  };

  const applyCodeRun = (nextRun: CodeRun) => {
    setCodeRun(nextRun);
    const caseTally = tallyCodeRunCases(nextRun.cases);
    if (caseTally) setCodeRunTally(caseTally);
  };

  const startCodeRunPolling = (attemptId: number, runId: string) => {
    stopCodeRunPolling();
    const controller = new AbortController();
    codeRunControllerRef.current = controller;
    const startedAt = Date.now();
    setIsCodeRunLoading(true);

    const poll = async () => {
      try {
        const nextRun = await getCodeRun(attemptId, runId, controller.signal);
        if (controller.signal.aborted) return;

        applyCodeRun(nextRun);
        if (nextRun.status !== 'QUEUED') {
          setIsCodeRunLoading(false);
          codeRunControllerRef.current = null;
          return;
        }

        if (Date.now() - startedAt >= CODE_RUN_MAX_WAIT_MS) {
          setCodeRunError('채점 대기 시간이 초과되었습니다. 다시 시도해주세요.');
          setIsCodeRunLoading(false);
          codeRunControllerRef.current = null;
          return;
        }

        codeRunPollTimeoutRef.current = window.setTimeout(
          () => void poll(),
          CODE_RUN_POLL_INTERVAL_MS,
        );
      } catch (error: unknown) {
        if (isAbortError(error)) return;
        setCodeRunError(
          getErrorInfo(error, '테스트 결과를 불러오지 못했습니다.').message,
        );
        setIsCodeRunLoading(false);
        codeRunControllerRef.current = null;
      }
    };

    void poll();
  };

  /**
   * 남의 제출을 읽을 때의 TEST 탭. 실행은 상태와 무관하게 소유자만 할 수 있어
   * (남이 부르면 404) 새로 돌리지 않고, 그 사람이 남긴 마지막 결과만 되살린다.
   * 폴링도 걸지 않는다 — 주인이 방금 돌린 실행이 남아 있어도 우리 화면이 그것을
   * 기다릴 이유가 없다.
   */
  const showRecordedCodeRun = async (attemptId: number) => {
    isCodeRunPendingRef.current = true;
    stopCodeRunPolling();
    const controller = new AbortController();
    codeRunControllerRef.current = controller;
    setCodeRunError(null);
    setCodeRunTally(null);
    setIsCodeRunLoading(true);

    try {
      const recorded = await fetchRecordedCodeRun(attemptId, controller.signal);
      if (controller.signal.aborted || routeAttemptIdRef.current !== attemptId) {
        return;
      }

      if (recorded) {
        setCodeRunTally(recorded.tally);
        applyCodeRun(recorded.run);
      } else {
        // 한 번도 돌리지 않고 제출한 어템프트다. 빈 결과 자리를 그대로 둔다.
        setCodeRun(null);
      }

      setIsCodeRunLoading(false);
      codeRunControllerRef.current = null;
    } catch (error: unknown) {
      if (isAbortError(error) || controller.signal.aborted) return;

      setCodeRunError(
        getErrorInfo(error, '기록된 테스트 결과를 불러오지 못했습니다.').message,
      );
      setIsCodeRunLoading(false);
      codeRunControllerRef.current = null;
    } finally {
      isCodeRunPendingRef.current = false;
    }
  };

  /**
   * URL이 가리키는 리소스를 서버에서 읽어 화면에 반영한다.
   *
   * 어템프트 모드에서는 어템프트를 먼저 읽고, 거기 담긴 problemId로 문제를 읽는다
   * — AttemptResponse에는 title/specMd가 없어서 왕복이 두 번 필요하다. 이 왕복은
   * 새로고침·북마크 같은 차가운 진입에서만 든다(아래 참조).
   */
  const loadFromRoute = async (
    attemptId: number | null,
    problemId: number | null,
  ) => {
    const requestedResource =
      attemptId === null ? `problem:${problemId}` : `attempt:${attemptId}`;
    loadControllerRef.current?.abort();
    const controller = new AbortController();
    loadControllerRef.current = controller;
    const { signal } = controller;

    setIsLoading(true);

    try {
      const loadedAttempt =
        attemptId === null ? null : await getAttempt(attemptId, signal);

      const targetProblemId = loadedAttempt?.problemId ?? problemId;
      // 호출 전에 주소를 걸러 두므로 여기까지 오면 항상 값이 있다.
      if (targetProblemId === null) return;

      // 문제 명세(title/specMd/스켈레톤)는 어템프트가 진행돼도 바뀌지 않는다. 같은
      // 문제를 이미 들고 있으면 다시 읽지 않는다 — 그러지 않으면 턴을 실행할 때마다,
      // 새로고침 뒤 reload마다 왕복이 하나씩 더 든다.
      const heldProblem = problemRef.current;
      const loadedProblem =
        heldProblem?.id === targetProblemId
          ? heldProblem
          : await getProblemDetail(targetProblemId, signal);

      // 문제를 건너뛰어도 지나간 로드는 여기서 걸러진다 — 어템프트를 읽는 사이에
      // 새 로드가 시작됐다면 이 컨트롤러는 이미 끊겨 있다.
      if (signal.aborted) return;

      setLoadError(null);

      const visibleFiles = loadedAttempt?.files ?? loadedProblem.files;
      const changedFiles = loadedAttempt
        ? getLatestChangedFiles(loadedAttempt.turns)
        : [];

      problemRef.current = loadedProblem;
      loadedResourceRef.current = requestedResource;
      setProblem(loadedProblem);
      setAttempt(loadedAttempt);
      // 사용자가 펼쳐 둔 폴더는 유지한 채 새로 생긴 폴더만 더한다.
      setExpandedFolders((folders) => {
        const nextFolders = new Set(folders);
        getFolderPaths(createFileTree(visibleFiles, changedFiles)).forEach(
          (path) => nextFolders.add(path),
        );
        return nextFolders;
      });
      // 보고 있던 파일이 아직 있으면 선택을 유지한다.
      setSelectedFile((current) =>
        findFile(visibleFiles, current) ? current : (visibleFiles[0]?.path ?? ''),
      );

      if (loadedAttempt?.turns.length) {
        setActiveTab('logs');
      }

      if (loadedAttempt?.status === 'SUBMITTED') {
        // 제출된 어템프트는 누구나 열 수 있으므로 "이미 제출된"이 늘 참은 아니다 —
        // 남의 기록에서는 내가 제출한 적이 없다. 파생 플래그가 아니라 방금 받은
        // 응답의 mine을 본다(이 시점의 attempt 상태는 아직 갱신 전이다).
        setStatus({
          message:
            loadedAttempt.mine === false
              ? '다른 사람이 제출한 풀이입니다. 읽기만 할 수 있습니다.'
              : '이미 제출된 어템프트입니다. 피드백만 확인할 수 있습니다.',
          type: 'normal',
        });
      }
    } catch (error: unknown) {
      if (isAbortError(error)) return;

      const errorInfo = getErrorInfo(error, '문제 상세를 불러오지 못했습니다.');

      // 실패를 알리는 자리는 화면에 이미 무엇이 떠 있는지에 따라 갈린다. 둘 다
      // 쓰면 같은 사정이 두 번 보고된다.
      //
      // 아직 아무것도 못 띄운 차가운 진입이면 페이지 전체를 안내로 채운다 — 주소가
      // 잘못됐다는 사실과 갈 곳을 같이 줘야 사용자가 막히지 않는다. 반대로 작업장이
      // 이미 떠 있으면(턴 실행 뒤 reload 등) 화면을 통째로 덮는 대신 상태줄에만
      // 남긴다. 보고 있던 파일과 턴 기록을 오류 하나로 걷어낼 이유가 없다.
      if (
        problemRef.current === null ||
        loadedResourceRef.current !== requestedResource
      ) {
        problemRef.current = null;
        loadedResourceRef.current = null;
        setProblem(null);
        setAttempt(null);

        if (errorInfo.code === API_ERROR_CODES.attemptNotFound) {
          setLoadError(toProblemsError('어템프트를 찾을 수 없습니다.'));
        } else if (errorInfo.code === API_ERROR_CODES.problemNotFound) {
          setLoadError(toProblemsError('문제를 찾을 수 없습니다.'));
        } else {
          setLoadError(toProblemsError(errorInfo.message));
        }
      } else {
        setStatus({ type: 'error', message: errorInfo.message });
      }
    } finally {
      // 뒤늦게 끝난 옛 로드가 새 로드의 로딩 표시를 끄지 않도록 한다.
      if (!signal.aborted) setIsLoading(false);
    }
  };

  /** 서버 상태를 다시 읽어 화면을 맞춘다. POST 응답 대신 이 GET을 정본으로 쓴다. */
  const reload = (attemptId: number) => loadFromRoute(attemptId, null);

  useEffect(() => {
    // 주소에 담긴 ID부터 확인한다. 서버에 물어볼 것도 없는 요청은 보내지 않는다.
    if (isAttemptRoute && routeAttemptId === null) {
      setLoadError(toProblemsError('잘못된 어템프트 주소입니다.'));
      setIsLoading(false);
      return;
    }

    if (!isAttemptRoute && routeProblemId === null) {
      setLoadError(toProblemsError('잘못된 문제 주소입니다.'));
      setIsLoading(false);
      return;
    }

    void loadFromRoute(routeAttemptId, routeProblemId);

    return () => {
      loadControllerRef.current?.abort();
    };
  }, [isAttemptRoute, routeAttemptId, routeProblemId]);

  useEffect(() => {
    stopCodeRunPolling();
    setCodeRun(null);
    setCodeRunTally(null);
    setCodeRunError(null);
    setIsCodeRunLoading(false);

    const isCurrentAttempt =
      attempt?.id === routeAttemptId && attempt.problemId === problem?.id;

    if (routeAttemptId === null || !isCurrentAttempt || isGame) {
      return stopCodeRunPolling;
    }

    // 남의 제출은 기록만 되살린다. 주인이 돌리는 중인 실행이 남아 있어도 폴링을
    // 걸지 않는다 — 그것이 끝나기를 기다리는 것은 이 화면의 일이 아니다.
    if (isOthersAttempt) {
      void showRecordedCodeRun(routeAttemptId);
      return stopCodeRunPolling;
    }

    const controller = new AbortController();
    codeRunControllerRef.current = controller;

    const restoreLatestCodeRun = async () => {
      setIsCodeRunLoading(true);

      try {
        const response = await getCodeRuns(routeAttemptId, controller.signal);
        if (controller.signal.aborted) return;

        const latestRun = response.runs[0];
        if (!latestRun) {
          setIsCodeRunLoading(false);
          codeRunControllerRef.current = null;
          return;
        }

        setCodeRunTally(latestRun.tally);
        if (latestRun.status === 'QUEUED') {
          startCodeRunPolling(routeAttemptId, latestRun.runId);
          return;
        }

        const restoredRun = await getCodeRun(
          routeAttemptId,
          latestRun.runId,
          controller.signal,
        );
        if (controller.signal.aborted) return;
        applyCodeRun(restoredRun);
        setIsCodeRunLoading(false);
        codeRunControllerRef.current = null;
      } catch (error: unknown) {
        if (isAbortError(error)) return;
        setCodeRunError(
          getErrorInfo(error, '최근 테스트 결과를 불러오지 못했습니다.').message,
        );
        setIsCodeRunLoading(false);
        codeRunControllerRef.current = null;
      }
    };

    void restoreLatestCodeRun();

    return stopCodeRunPolling;
  }, [
    attempt?.id,
    attempt?.problemId,
    isGame,
    isOthersAttempt,
    problem?.id,
    routeAttemptId,
    routeProblemId,
  ]);

  const fileTree = useMemo(
    () => createFileTree(files, getLatestChangedFiles(turns)),
    [files, turns],
  );

  const selectedCode = useMemo(() => {
    if (!selectedFile) return '// 파일을 선택해주세요.';
    return findFile(files, selectedFile)?.content ?? '// 이 턴에서 삭제된 파일입니다.';
  }, [files, selectedFile]);

  /** 직전 턴 직후의 파일 상태. 마지막 턴 diff의 기준선이다. 턴이 없으면 null. */
  const previousFiles = useMemo(() => {
    if (!attempt || attempt.turns.length === 0) return null;
    return replayFiles(attempt.baseFiles, attempt.turns.slice(0, -1));
  }, [attempt]);

  /** 선택 파일의 직전 턴 대비 diff. 마지막 턴이 안 건드린 파일은 강조가 없다. */
  const selectedDiff = useMemo(() => {
    if (!attempt || !previousFiles || !selectedFile) return undefined;
    const lastTurn = attempt.turns[attempt.turns.length - 1];
    const normalizedSelected = normalizeRepositoryPath(selectedFile);
    const touched = lastTurn.changedFiles.some(
      (file) =>
        normalizeRepositoryPath(file.path) === normalizedSelected &&
        file.changeType !== 'DELETED',
    );
    if (!touched) return undefined;
    const afterContent = findFile(files, selectedFile)?.content;
    if (afterContent === undefined) return undefined;
    return diffLines(findFile(previousFiles, selectedFile)?.content ?? null, afterContent);
  }, [attempt, files, previousFiles, selectedFile]);

  const showStatus = (message: string, type: StatusType = 'normal') => {
    setStatus({ message, type });
  };

  /**
   * PLAY 탭과 전체 화면 전환 뒤에도 게임이 바로 키보드 입력을 받게 한다.
   * iframe이 문서를 모두 붙인 다음 한 프레임을 기다려야 전체 화면 전환과
   * 포커스가 경합하지 않는다.
   */
  const focusGamePreview = (iframe: HTMLIFrameElement) => {
    window.requestAnimationFrame(() => {
      if (iframe.isConnected) {
        iframe.focus({ preventScroll: true });
      }
    });
  };

  const handlePreviewFullscreen = async () => {
    const previewContainer = previewContainerRef.current;
    if (!previewContainer) return;

    try {
      if (document.fullscreenElement === previewContainer) {
        await document.exitFullscreen();
      } else {
        await previewContainer.requestFullscreen();
        setPreviewSession((session) => session + 1);
      }
    } catch {
      showStatus('전체 화면을 시작할 수 없습니다. 브라우저 설정을 확인해주세요.', 'error');
    }
  };

  useEffect(() => {
    const updatePreviewFullscreenState = () => {
      setIsPreviewFullscreen(document.fullscreenElement === previewContainerRef.current);
    };

    document.addEventListener('fullscreenchange', updatePreviewFullscreenState);
    return () => document.removeEventListener('fullscreenchange', updatePreviewFullscreenState);
  }, []);

  const handleCodeRunRequest = async () => {
    if (isGame || !problem || isCodeRunLoading || isCodeRunPendingRef.current) return;

    /*
      TEST 탭은 열자마자 실행을 요청한다. 제출된 어템프트가 공개로 바뀌어 이 화면은
      남의 풀이로도 열리는데, 실행은 소유자만 할 수 있어(남이 부르면 404) 그대로
      두면 어템프트가 멀쩡히 보이는 화면이 "찾을 수 없다"고 말하게 된다.
      남의 것이면 요청 대신 그 사람이 남긴 결과를 읽는다 — 랭킹에서 넘어온 사람이
      가장 궁금해할 "이 풀이가 정말 통과했나"에 답하는 것이 이 탭의 쓸모다.
    */
    if (attempt && isOthersAttempt) {
      await showRecordedCodeRun(attempt.id);
      return;
    }

    isCodeRunPendingRef.current = true;
    stopCodeRunPolling();
    const controller = new AbortController();
    codeRunControllerRef.current = controller;
    const requestedProblemId = problem.id;
    let requestedAttempt = attempt;
    let requestedAttemptId = attempt?.id ?? null;
    let createdForSkeleton = false;
    setCodeRunError(null);
    setCodeRunTally(null);
    setIsCodeRunLoading(true);

    const isCurrentTarget = () =>
      !controller.signal.aborted &&
      (createdForSkeleton
        ? routeProblemIdRef.current === requestedProblemId
        : routeAttemptIdRef.current === requestedAttemptId);

    const moveToCreatedAttempt = () => {
      if (!createdForSkeleton || !requestedAttempt || requestedAttemptId === null) {
        return;
      }

      setAttempt(requestedAttempt);
      routeAttemptIdRef.current = requestedAttemptId;
      navigate(`/attempts/${requestedAttemptId}`, { replace: true });
    };

    try {
      if (requestedAttemptId === null) {
        requestedAttempt = await createAttempt(requestedProblemId);
        if (
          controller.signal.aborted ||
          routeProblemIdRef.current !== requestedProblemId
        ) {
          return;
        }

        requestedAttemptId = requestedAttempt.id;
        createdForSkeleton = true;
      }

      const requestedRun = await requestCodeRun(
        requestedAttemptId,
        controller.signal,
      );
      if (!isCurrentTarget()) return;

      applyCodeRun(requestedRun);

      if (createdForSkeleton) {
        moveToCreatedAttempt();
        return;
      }

      if (requestedRun.status === 'QUEUED') {
        startCodeRunPolling(requestedAttemptId, requestedRun.runId);
      } else {
        setIsCodeRunLoading(false);
        codeRunControllerRef.current = null;
      }
    } catch (error: unknown) {
      if (isAbortError(error) || controller.signal.aborted) return;

      const errorInfo = getErrorInfo(error, '코드 실행을 요청하지 못했습니다.');

      if (errorInfo.code === API_ERROR_CODES.codeRunInProgress) {
        try {
          if (requestedAttemptId === null) return;

          const response = await getCodeRuns(
            requestedAttemptId,
            controller.signal,
          );
          if (!isCurrentTarget()) return;

          const queuedRun = response.runs.find((run) => run.status === 'QUEUED');

          if (queuedRun) {
            setCodeRunTally(queuedRun.tally);
            if (createdForSkeleton) moveToCreatedAttempt();
            else startCodeRunPolling(requestedAttemptId, queuedRun.runId);
            return;
          }

          // 목록을 읽는 사이 실행이 끝났다면 409를 오류로 남기지 않고
          // 최근 완료 실행의 상세 결과를 바로 복원한다.
          const latestRun = response.runs[0];
          if (latestRun) {
            const restoredRun = await getCodeRun(
              requestedAttemptId,
              latestRun.runId,
              controller.signal,
            );
            if (!isCurrentTarget()) return;

            setCodeRunTally(latestRun.tally);
            applyCodeRun(restoredRun);
            if (createdForSkeleton) {
              moveToCreatedAttempt();
            } else if (restoredRun.status === 'QUEUED') {
              startCodeRunPolling(requestedAttemptId, restoredRun.runId);
            } else {
              setIsCodeRunLoading(false);
              codeRunControllerRef.current = null;
            }
            return;
          }
        } catch (restoreError: unknown) {
          if (isAbortError(restoreError) || controller.signal.aborted) return;

          setCodeRunError(
            getErrorInfo(
              restoreError,
              '진행 중인 테스트 정보를 불러오지 못했습니다.',
            ).message,
          );
          setIsCodeRunLoading(false);
          return;
        }
      }

      setCodeRunError(errorInfo.message);
      setIsCodeRunLoading(false);
      codeRunControllerRef.current = null;
    } finally {
      isCodeRunPendingRef.current = false;
    }
  };

  const handleDetailTabClick = (tab: DetailTab) => {
    setActiveTab(tab);
    if (tab === 'test' && !isGame) void handleCodeRunRequest();
  };

  const handleRun = async () => {
    if (
      !problem ||
      isSubmitted ||
      isRunning ||
      isSubmitting ||
      isRunPendingRef.current
    ) {
      return;
    }

    const trimmedPrompt = prompt.trim();
    if (!trimmedPrompt) {
      showStatus('프롬프트를 입력해주세요.', 'error');
      return;
    }

    if (trimmedPrompt.length > 4000) {
      showStatus('프롬프트는 4,000자 이하로 입력해주세요.', 'error');
      return;
    }

    isRunPendingRef.current = true;

    // 같은 프롬프트를 재시도하면 이전 키를 재사용해 AI 중복 호출을 막는다.
    const idempotencyKey =
      pendingRunRef.current?.prompt === trimmedPrompt
        ? pendingRunRef.current.key
        : createIdempotencyKey();
    pendingRunRef.current = { key: idempotencyKey, prompt: trimmedPrompt };

    let attemptId = routeAttemptId;

    setIsRunning(true);
    showStatus('프롬프트를 실행하고 있습니다. 완료될 때까지 잠시 기다려주세요.');

    try {
      // 첫 실행이면 어템프트를 먼저 만들고(AI 호출 없음) 그 주소로 옮겨 간다.
      // 응답 본문은 버리고 id만 쓴다 — 화면은 아래 reload()의 GET으로 채운다.
      // Idempotency-Key는 붙이지 않는다: 키는 엔드포인트와 무관하게 조회되므로
      // 생성에 쓴 키를 턴에 재사용하면 턴이 붙지 않는다.
      if (attemptId === null) {
        attemptId = (await createAttempt(problem.id)).id;
        // replace인 이유: push하면 뒤로가기가 문제 화면으로 돌아가 두 번째
        // 어템프트를 만들게 된다. 같은 컴포넌트를 렌더하는 라우트라 이 이동에
        // 리마운트는 없고, 아래 addTurn 요청은 그대로 이어진다.
        navigate(`/attempts/${attemptId}`, { replace: true });
      }

      await addTurn(attemptId, trimmedPrompt, idempotencyKey);
      await reload(attemptId);

      setActiveTab('logs');
      setPrompt('');
      pendingRunRef.current = null;
      showStatus('실행이 완료되었습니다. 변경 파일과 AI 응답을 확인해주세요.');
    } catch (error: unknown) {
      const errorInfo = getErrorInfo(
        error,
        '실행에 실패했습니다. 다시 시도해주세요.',
      );

      // 이미 제출된 어템프트에는 턴을 더할 수 없다 — 서버 상태를 다시 읽어 맞춘다.
      if (
        errorInfo.code === API_ERROR_CODES.attemptAlreadySubmitted &&
        attemptId !== null
      ) {
        await reload(attemptId);
      }

      showStatus(errorInfo.message, 'error');
    } finally {
      isRunPendingRef.current = false;
      setIsRunning(false);
    }
  };

  const handlePromptKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    const isSubmitShortcut =
      event.key === 'Enter' && (event.ctrlKey || event.metaKey);

    if (
      !isSubmitShortcut ||
      event.nativeEvent.isComposing ||
      event.keyCode === 229
    ) {
      return;
    }

    event.preventDefault();

    if (
      isRunning ||
      isSubmitting ||
      isSubmitted ||
      !prompt.trim() ||
      isRunPendingRef.current
    ) {
      return;
    }

    void handleRun();
  };

  const handleDetailTabKeyDown = (
    event: KeyboardEvent<HTMLButtonElement>,
    currentTab: DetailTab,
  ) => {
    const currentIndex = detailTabs.findIndex(([tab]) => tab === currentTab);
    const nextIndex = nextTabIndex(event.key, currentIndex, detailTabs.length);

    if (nextIndex === null) return;

    event.preventDefault();
    const nextTab = detailTabs[nextIndex][0];
    setActiveTab(nextTab);
    detailTabRefs.current[nextTab]?.focus();
    if (nextTab === 'test' && !isGame) void handleCodeRunRequest();
  };

  const handleSubmit = async () => {
    if (!problem || !attempt) return;

    // 이미 제출된 어템프트는 저장된 피드백을 그대로 보여준다.
    if (isSubmitted) {
      navigate(`/attempts/${attempt.id}/feedback`);
      return;
    }

    setIsSubmitting(true);
    showStatus('프롬프트 피드백을 생성하고 있습니다.');

    try {
      await submitAttempt(attempt.id);

      // 제출 응답도 화면에 반영하지 않는다 — 곧바로 피드백 주소로 옮겨 가고,
      // 그 화면이 자기 주소를 보고 서버에서 다시 읽는다.
      navigate(`/attempts/${attempt.id}/feedback`);
    } catch (error: unknown) {
      const errorInfo = getErrorInfo(
        error,
        '피드백 생성에 실패했습니다. 잠시 후 다시 제출해주세요.',
      );

      // 이미 제출됐다면 오류가 아니라 피드백 화면으로 보내는 편이 자연스럽다.
      if (errorInfo.code === API_ERROR_CODES.attemptAlreadySubmitted) {
        navigate(`/attempts/${attempt.id}/feedback`);
        return;
      }

      // 409 feedback-in-progress는 실패가 아니라 정상 동작이다 — 다른 요청이 아직
      // 피드백을 만드는 중이므로, 에러 페이지로 보내지 않고 화면에 남겨 재시도를 안내한다.
      if (errorInfo.code === API_ERROR_CODES.feedbackInProgress) {
        showStatus('피드백을 생성하고 있습니다. 잠시 후 다시 눌러주세요.');
        return;
      }

      const errorState: ErrorPageState = {
        title: '피드백 생성에 실패했습니다.',
        message: errorInfo.message,
        problemTitle: problem.title,
        status: errorInfo.status,
        returnPath: `/problems/${problem.id}`,
        returnLabel: '문제로 돌아가 다시 시도',
      };

      navigate('/error', {
        state: errorState,
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  const toggleFolder = (path: string) => {
    setExpandedFolders((folders) => {
      const nextFolders = new Set(folders);
      if (nextFolders.has(path)) nextFolders.delete(path);
      else nextFolders.add(path);
      return nextFolders;
    });
  };

  const renderFileTree = (nodes: FileTreeNode[], depth = 0) =>
    nodes.map((item) => {
      if (item.type === 'folder') {
        const isExpanded = expandedFolders.has(item.path);

        return (
          <div key={item.path}>
            <button
              aria-expanded={isExpanded}
              className="grid min-h-[30px] w-full cursor-pointer grid-cols-[14px_14px_max-content] items-center gap-1 border-0 bg-transparent pr-2 text-left font-inherit text-[var(--problem-detail-muted)] hover:text-[var(--problem-detail-text)]"
              onClick={() => toggleFolder(item.path)}
              style={{ paddingLeft: `${7 + depth * 14}px` }}
              type="button"
            >
              <span className="text-[10px] text-[var(--problem-detail-subtle)]" aria-hidden="true">
                {isExpanded ? '▼' : '▶'}
              </span>
              <span className="text-[13px] text-[var(--problem-detail-acid)]" aria-hidden="true">
                {isExpanded ? '▱' : '□'}
              </span>
              <span className="whitespace-nowrap">{item.name}</span>
            </button>
            {isExpanded && renderFileTree(item.children, depth + 1)}
          </div>
        );
      }

      const isSelected =
        normalizeRepositoryPath(selectedFile) ===
        normalizeRepositoryPath(item.path);

      return (
        <button
          aria-current={isSelected ? 'true' : undefined}
          className={[
            'grid min-h-[30px] w-full cursor-pointer grid-cols-[14px_max-content_28px] items-center gap-1 border-0 bg-transparent pr-2 text-left font-inherit text-inherit hover:text-[var(--problem-detail-text)]',
            isSelected
              ? 'bg-[var(--problem-detail-acid)] text-[#090909] hover:text-[#090909]'
              : '',
            item.deleted ? 'opacity-60 line-through' : '',
          ]
            .filter(Boolean)
            .join(' ')}
          key={item.path}
          onClick={() => setSelectedFile(item.path)}
          style={{
            paddingLeft: `${7 + depth * 14}px`,
            ...(isSelected
              ? {
                  backgroundColor: 'var(--problem-detail-acid)',
                  color: '#090909',
                }
              : {}),
          }}
          title={item.path}
          type="button"
        >
          <span
            className={[
              'text-[11px]',
              isSelected ? 'text-[#090909]' : 'text-[var(--problem-detail-subtle)]',
            ].join(' ')}
            aria-hidden="true"
          >
            ◇
          </span>
          <span className="whitespace-nowrap">{item.name}</span>
          <span
            className={[
              'text-right font-black',
              isSelected
                ? 'text-[#090909]'
                : item.changeType
                  ? changeColorClasses[item.changeType]
                  : '',
            ].join(' ')}
          >
            {item.changeType?.[0] ?? ''}
          </span>
        </button>
      );
    });

  // 전체 화면 로딩은 보여줄 것이 아직 없을 때만 쓴다. 실행 중에 주소가 바뀌어
  // 배경에서 다시 읽을 때까지 작업장을 덮으면, 수 분 걸리는 AI 실행 내내
  // 로딩 문구만 보이게 된다.
  if (isLoading && !problem) {
    return (
      <div
        className={`problem-detail-page ${pageStateClasses}`}
        data-color-mode={colorMode}
      >
        문제 상세를 불러오는 중입니다…
      </div>
    );
  }

  if (!problem) {
    const notice = loadError ?? toProblemsError('문제를 찾을 수 없습니다.');

    return (
      <div
        className={`problem-detail-page ${pageStateClasses} text-[#ff786b]`}
        data-color-mode={colorMode}
      >
        <div>
          <p>{notice.message}</p>
          <Button className="problem-detail-primary-action mt-5" to={notice.actionTo}>
            <span className="text-[14px]">{notice.actionLabel}</span>
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div
      className="problem-detail-page flex h-screen min-w-80 flex-col overflow-hidden bg-[var(--problem-detail-bg)] text-[var(--problem-detail-text)] [font-family:Arial,'Noto_Sans_KR',sans-serif] max-[700px]:h-auto max-[700px]:min-h-screen max-[700px]:overflow-visible"
      data-color-mode={colorMode}
    >
      <Header variant="workspace" />

      <main className="grid min-h-0 flex-1 overflow-hidden grid-cols-[230px_minmax(360px,1fr)_minmax(420px,480px)] max-[1080px]:grid-cols-[190px_minmax(0,1fr)] max-[700px]:block max-[700px]:overflow-visible">
        <aside className="flex min-h-0 min-w-0 flex-col overflow-hidden border-r border-[var(--problem-detail-border)] px-6 py-[22px] max-[700px]:overflow-visible max-[700px]:border-r-0 max-[700px]:border-b max-[700px]:px-4 max-[700px]:py-[18px]">
          <div className={koreanLabelClasses}>파일 탐색기</div>

          <div className="workspace-scrollbar mt-[18px] min-h-0 flex-1 overflow-auto max-[700px]:flex-none max-[700px]:overflow-visible">
            <div className="grid w-max min-w-full select-none gap-[3px] font-mono text-xs leading-[1.5] text-[var(--problem-detail-muted)]">
              {renderFileTree(fileTree)}
            </div>
          </div>

        </aside>

        <section className="flex min-h-0 min-w-0 flex-col overflow-hidden border-r border-[var(--problem-detail-border)] px-7 py-[22px] max-[1080px]:border-r-0 max-[700px]:block max-[700px]:overflow-visible max-[700px]:border-b max-[700px]:px-4 max-[700px]:pt-5 max-[700px]:pb-[30px]">
          <div className="mb-5 flex items-start justify-between gap-[18px]">
            <div>
              <div className={selectedFile ? labelClasses : koreanLabelClasses}>
                {selectedFile || '파일'}
              </div>
            </div>
            {/* 테두리 상자는 이 앱의 버튼 생김새다 — 상태 표시는 채움 배경으로 구분한다. */}
            <span className="shrink-0 bg-[var(--problem-detail-code-bg)] px-2 py-1.5 text-[9px] text-[var(--problem-detail-muted)]">
              읽기 전용
            </span>
          </div>

          <div className="workspace-scrollbar min-h-0 flex-1 overflow-auto border border-[var(--problem-detail-border)] bg-[var(--problem-detail-code-bg)] max-[700px]:min-h-[360px]">
            <CodeViewer
              code={selectedCode}
              diff={selectedDiff}
              key={`${selectedFile}::${turns.length}`}
              path={selectedFile}
            />
          </div>
        </section>

        <aside
          className="col-span-1 grid min-h-0 min-w-0 grid-rows-[minmax(0,1fr)_auto] overflow-hidden px-6 py-[22px] max-[1080px]:col-span-full max-[1080px]:grid-rows-1 max-[1080px]:grid-cols-[minmax(0,0.8fr)_minmax(300px,1.2fr)] max-[1080px]:gap-7 max-[1080px]:overflow-visible max-[1080px]:border-t max-[1080px]:border-[var(--problem-detail-border)] max-[700px]:block max-[700px]:px-4 max-[700px]:pt-5 max-[700px]:pb-[30px]"
          style={isPlaying ? { gridTemplateRows: 'minmax(0, 1fr)' } : undefined}
        >
          <section className="flex min-h-0 flex-1 flex-col overflow-hidden border-b border-[var(--problem-detail-border)] pb-[22px] max-[1080px]:border-b-0 max-[1080px]:pb-0 max-[700px]:overflow-visible">
            <div
              className="grid shrink-0 grid-cols-3 border border-[var(--problem-detail-border)]"
              role="tablist"
              aria-label="문제 상세 정보"
            >
              {detailTabs.map(([tab, label]) => (
                <button
                  aria-controls="problem-detail-tabpanel"
                  aria-selected={activeTab === tab}
                  className={[
                    'min-h-10 cursor-pointer border-0 bg-transparent px-3 text-sm leading-[1.5] font-bold',
                    tab !== 'test' ? 'border-r border-[var(--problem-detail-border)]' : '',
                    activeTab === tab
                      ? 'border-b-2 border-b-[var(--problem-detail-acid)] text-[var(--problem-detail-acid)]'
                      : 'text-[var(--problem-detail-muted)] hover:text-[var(--problem-detail-text)]',
                  ].join(' ')}
                  id={`problem-detail-tab-${tab}`}
                  key={tab}
                  onClick={() => handleDetailTabClick(tab)}
                  onKeyDown={(event) => handleDetailTabKeyDown(event, tab)}
                  ref={(element) => {
                    detailTabRefs.current[tab] = element;
                  }}
                  role="tab"
                  tabIndex={activeTab === tab ? 0 : -1}
                  type="button"
                >
                  {isGame && tab === 'test' ? '플레이' : label}
                </button>
              ))}
            </div>

            <div
              aria-labelledby={`problem-detail-tab-${activeTab}`}
              className="workspace-scrollbar mt-[18px] min-h-[210px] flex-1 overflow-y-auto overflow-x-hidden max-[700px]:overflow-visible"
              id="problem-detail-tabpanel"
              role="tabpanel"
              tabIndex={0}
            >
              {activeTab === 'problem' ? (
                <>
                  <div className="m-0 whitespace-pre-wrap text-[13px] leading-[1.7] text-[var(--problem-detail-muted)] [word-break:keep-all] [&_pre_code]:bg-transparent [&_pre_code]:p-0">
                    <ReactMarkdown
                      components={{
                        h1: ({ children }) => (
                          <h1 className="my-3 mt-2.5 text-[24px] leading-[1.05] font-bold tracking-[-0.045em] text-[var(--problem-detail-text)]">
                            {children}
                          </h1>
                        ),
                        h2: ({ children }) => <h2 className="font-bold text-[var(--problem-detail-text)]">{children}</h2>,
                        h3: ({ children }) => <h3 className="font-bold text-[var(--problem-detail-text)]">{children}</h3>,
                        h4: ({ children }) => <h4 className="font-bold text-[var(--problem-detail-text)]">{children}</h4>,
                        h5: ({ children }) => <h5 className="font-bold text-[var(--problem-detail-text)]">{children}</h5>,
                        h6: ({ children }) => <h6 className="font-bold text-[var(--problem-detail-text)]">{children}</h6>,
                        // preflight가 목록의 불릿·번호·들여쓰기를 지운다 — 매핑이 없으면
                        // 명세의 조건 나열이 그냥 줄글로 보인다.
                        ul: ({ children }) => <ul className="my-2 list-disc pl-5">{children}</ul>,
                        ol: ({ children }) => <ol className="my-2 list-decimal pl-5">{children}</ol>,
                        li: ({ children }) => <li className="my-1">{children}</li>,
                        blockquote: ({ children }) => (
                          <blockquote className="my-3 border-0 border-l-2 border-[var(--problem-detail-border)] pl-3">
                            {children}
                          </blockquote>
                        ),
                        // preflight가 a의 색과 밑줄도 지워 링크가 본문에 묻힌다.
                        a: ({ children, href }) => (
                          <a
                            className="text-[var(--problem-detail-acid)] underline"
                            href={href}
                            rel="noreferrer"
                            target="_blank"
                          >
                            {children}
                          </a>
                        ),
                        code: ({ children }) => (
                          <code className="bg-[var(--problem-detail-code-bg)] px-1 py-0.5 text-[12px] text-[var(--problem-detail-code-text)]">
                            {children}
                          </code>
                        ),
                        // 감싼 div가 whitespace-pre-wrap이라 표 안에서는 원문 줄바꿈이
                        // 그대로 살아난다 — 표만 normal로 되돌리고 가로 스크롤시킨다.
                        table: ({ children }) => (
                          <div className="my-3 max-w-full overflow-x-auto">
                            <table className="w-full border-collapse text-[12px] whitespace-normal">
                              {children}
                            </table>
                          </div>
                        ),
                        th: ({ children }) => (
                          <th className="border border-[var(--problem-detail-border)] px-2.5 py-1.5 text-left font-bold text-[var(--problem-detail-text)]">
                            {children}
                          </th>
                        ),
                        td: ({ children }) => (
                          <td className="border border-[var(--problem-detail-border)] px-2.5 py-1.5 align-top">
                            {children}
                          </td>
                        ),
                      }}
                      remarkPlugins={[remarkGfm]}
                    >
                      {problem.specMd}
                    </ReactMarkdown>
                  </div>
                </>
              ) : activeTab === 'logs' && turns.length ? (
                <div className="grid gap-4">
                  <div className="flex items-end justify-between border border-[var(--problem-detail-border)] bg-[var(--problem-detail-surface)] px-4 py-3">
                    <div>
                      <p className="m-0 text-[12px] text-[var(--problem-detail-text)]">
                        전체 프롬프트 토큰 사용량
                      </p>
                    </div>
                    <strong className="font-mono text-xl text-[var(--problem-detail-acid)]">
                      {formatUsageValue(hasTokenUsage ? totalTokenUsage : null)}
                    </strong>
                  </div>

                  {turns.map((turn, index) => (
                    <article
                      className="border-l-2 border-[var(--problem-detail-acid)] pl-3"
                      key={`turn-${index + 1}`}
                    >
                      <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 text-[11px] font-bold">
                        <span className="text-[var(--problem-detail-acid)]">
                          턴 {index + 1}
                        </span>
                        <span className="flex gap-3 text-[var(--problem-detail-subtle)]">
                          <span>
                            토큰{' '}
                            <strong className="text-[var(--problem-detail-text)]">
                              {formatUsageValue(turnTokenUsages[index])}
                            </strong>
                          </span>
                          <span>
                            응답 시간{' '}
                            <strong className="text-[var(--problem-detail-text)]">
                              {formatUsageValue(turn.usage?.latencyMs, 'ms')}
                            </strong>
                          </span>
                        </span>
                      </div>
                      <p className="my-2 whitespace-pre-wrap text-[12px] leading-[1.6] text-[var(--problem-detail-text)]">
                        {turn.prompt}
                      </p>
                      <p className="m-0 whitespace-pre-wrap text-[11px] leading-[1.6] text-[var(--problem-detail-muted)]">
                        {turn.aiResponse}
                      </p>

                      {turn.changedFiles.length > 0 && (
                        <div className="mt-1.5 grid gap-0.5 font-mono text-[9px]">
                          {turn.changedFiles.map((changedFile) => (
                            <span
                              className={changeColorClasses[changedFile.changeType]}
                              key={`changed-${index}-${changedFile.path}`}
                            >
                              {changedFile.changeType[0]} {changedFile.path}
                            </span>
                          ))}
                        </div>
                      )}
                    </article>
                  ))}
                </div>
              ) : activeTab === 'logs' ? (
                <div className="grid min-h-[160px] place-items-center text-center font-mono text-[11px] leading-[1.7] text-[var(--problem-detail-subtle)]">
                  실행한 프롬프트가 없습니다.
                </div>
              ) : activeTab === 'test' && isGame ? (
                previewHtml ? (
                  <div
                    className="relative h-full min-h-[320px] bg-[#090909] [&:fullscreen]:h-dvh [&:fullscreen]:w-dvw [&:fullscreen]:box-border [&:fullscreen]:bg-[#090909] [&:fullscreen]:p-6"
                    ref={previewContainerRef}
                  >
                    <button
                      className="absolute top-3 right-3 z-10 cursor-pointer border border-[#d6ff50] bg-[#090909]/90 px-3 py-1.5 font-mono text-[10px] font-bold tracking-[0.08em] text-[#d6ff50] hover:bg-[#d6ff50] hover:text-[#090909]"
                      onClick={() => void handlePreviewFullscreen()}
                      type="button"
                    >
                      {isPreviewFullscreen ? '전체 화면 닫기' : '확대 플레이'}
                    </button>
                    <iframe
                      className="h-full min-h-[320px] w-full border border-[#3f3f3f] bg-white"
                      key={`${previewHtml}-${previewSession}`}
                      onLoad={(event) => focusGamePreview(event.currentTarget)}
                      sandbox="allow-scripts"
                      srcDoc={previewHtml}
                      title="게임 미리보기"
                      />
                  </div>
                ) : (
                  <div className="grid min-h-[160px] place-items-center border border-[var(--problem-detail-border)] px-4 text-center font-mono text-[11px] leading-[1.7] text-[var(--problem-detail-subtle)]">
                    실행할 index.html 파일이 없습니다.
                  </div>
                )
              ) : codeRunError ? (
                <div className="grid min-h-[160px] place-items-center text-center font-mono text-[11px] leading-[1.7] text-[#ff786b]">
                  <div>
                    <p className="m-0">{codeRunError}</p>
                    <button
                      className="mt-3 cursor-pointer border border-[var(--problem-detail-border-strong)] bg-transparent px-3 py-1.5 text-[10px] text-[var(--problem-detail-text)] hover:border-[var(--problem-detail-acid)] hover:text-[var(--problem-detail-acid)]"
                      onClick={() => void handleCodeRunRequest()}
                      type="button"
                    >
                      {/* 남의 제출에서 이 버튼이 다시 하는 일은 채점이 아니라 읽기다. */}
                      {isOthersAttempt ? '다시 불러오기' : '다시 채점하기'}
                    </button>
                  </div>
                </div>
              ) : isCodeRunLoading || codeRun?.status === 'QUEUED' ? (
                <div className="grid min-h-[160px] place-items-center text-center font-mono text-[11px] leading-[1.7] text-[var(--problem-detail-muted)]">
                  {/* 남의 제출에서는 우리가 채점을 시킨 적이 없다 — 읽는 중일 뿐이다. */}
                  {isOthersAttempt ? (
                    <div>
                      {/* 멈춘 화면처럼 보이면 오류로 오해한다 — 도는 표시를 함께 둔다. */}
                      <Spinner className="mb-3 text-[var(--problem-detail-muted)]" />
                      <div className="text-[var(--problem-detail-acid)]">
                        기록을 불러오는 중…
                      </div>
                      <div className="mt-2 text-[9px] text-[var(--problem-detail-subtle)]">
                        이 제출에 남은 실행 결과를 읽고 있습니다.
                      </div>
                    </div>
                  ) : (
                    <div>
                      <Spinner className="mb-3 text-[var(--problem-detail-muted)]" />
                      <div className="text-[var(--problem-detail-acid)]">채점 중…</div>
                      <div className="mt-2 text-[9px] text-[var(--problem-detail-subtle)]">
                        완료될 때까지 잠시 기다려주세요.
                      </div>
                    </div>
                  )}
                </div>
              ) : codeRun ? (
                <div className="[font-family:Arial,'Noto_Sans_KR',sans-serif]">
                  <div className="border border-[var(--problem-detail-border-strong)] bg-[var(--problem-detail-surface)] px-4 py-3">
                    <div className="flex items-end justify-between gap-4">
                      {/*
                        남의 제출에서 보이는 것은 지금 돌린 결과가 아니라 그 사람이
                        남긴 기록이다. 제목 자리에서 그렇게 밝힌다 — 아래 케이스 목록과
                        실행 로그가 방금 만들어진 것처럼 읽히면 안 된다.
                      */}
                      <div>
                        <p className="m-0 text-[12px] text-[var(--problem-detail-text)]">
                          {isOthersAttempt
                            ? '이 제출에 기록된 마지막 채점 결과'
                            : '테스트 케이스 채점 결과'}
                        </p>
                      </div>
                      {visibleCodeRunTally ? (
                        <div className="flex shrink-0 items-baseline gap-1 leading-none">
                          <strong className="font-mono text-xl text-[var(--problem-detail-acid)]">
                            {visibleCodeRunTally.passed}/{visibleCodeRunTally.total}
                          </strong>
                          <span className="text-[10px] leading-none text-[var(--problem-detail-subtle)]">
                            개 통과
                          </span>
                        </div>
                      ) : (
                        <strong
                          className={`font-mono text-[11px] ${
                            codeRun.status === 'SUCCEEDED'
                              ? 'text-[var(--problem-detail-acid)]'
                              : 'text-[#ff786b]'
                          }`}
                        >
                          {codeRunStatusLabels[codeRun.status]}
                        </strong>
                      )}
                    </div>

                    {visibleCodeRunTally && (
                      <div
                        aria-label={`테스트 케이스 ${visibleCodeRunTally.total}개 중 ${visibleCodeRunTally.passed}개 통과`}
                        aria-valuemax={visibleCodeRunTally.total}
                        aria-valuemin={0}
                        aria-valuenow={visibleCodeRunTally.passed}
                        className="mt-4 h-1.5 overflow-hidden bg-[var(--problem-detail-border)]"
                        role="progressbar"
                      >
                        <div
                          className="h-full bg-[var(--problem-detail-acid)]"
                          style={{
                            width: `${(visibleCodeRunTally.passed / visibleCodeRunTally.total) * 100}%`,
                          }}
                        />
                      </div>
                    )}

                    <div className="mt-3 flex flex-wrap gap-x-3 gap-y-1 text-[9px] text-[var(--problem-detail-subtle)]">
                      <span>상태 {codeRunStatusLabels[codeRun.status]}</span>
                      {codeRun.turnOrdinal !== null && (
                        <span>턴 {codeRun.turnOrdinal + 1}</span>
                      )}
                      {codeRun.durationMs !== null && (
                        <span>{codeRun.durationMs.toLocaleString('ko-KR')}ms</span>
                      )}
                    </div>
                  </div>

                  {codeRun.cases.length > 0 ? (
                    <div className="mt-3 grid border-x border-t border-[var(--problem-detail-border)]">
                      {codeRun.cases.map((testCase, index) => (
                        <article
                          className="border-b border-[var(--problem-detail-border)] px-3 py-2.5"
                          key={`${testCase.className ?? 'case'}-${testCase.name}-${index}`}
                        >
                          <div className="flex items-start justify-between gap-3">
                            <div className="min-w-0">
                              <p className="m-0 break-words text-[11px] leading-[1.5] text-[var(--problem-detail-text)]">
                                {testCase.name}
                              </p>
                              <div className="mt-1 flex flex-wrap gap-2 font-mono text-[8px] text-[var(--problem-detail-subtle)]">
                                {testCase.className && <span>{testCase.className}</span>}
                                {testCase.durationMs !== null && (
                                  <span>{testCase.durationMs.toLocaleString('ko-KR')}ms</span>
                                )}
                              </div>
                            </div>
                            <strong
                              className={`shrink-0 text-[10px] ${codeRunCaseColorClasses[testCase.status]}`}
                            >
                              {codeRunCaseLabels[testCase.status]}
                            </strong>
                          </div>
                          {(testCase.status === 'FAILED' ||
                            testCase.status === 'ERROR') &&
                            testCase.message && (
                              <p className="mt-2 mb-0 break-words border-l-2 border-[var(--problem-detail-border-strong)] pl-2 font-mono text-[9px] leading-[1.5] text-[var(--problem-detail-muted)]">
                                {testCase.message}
                              </p>
                            )}
                        </article>
                      ))}
                    </div>
                  ) : (
                    <div className="mt-3 border border-[var(--problem-detail-border)] px-3 py-4 text-center text-[11px] text-[var(--problem-detail-subtle)]">
                      테스트 케이스 기록이 없습니다.
                    </div>
                  )}

                  {(codeRun.stdout || codeRun.stderr) && (
                    <details className="mt-3 border border-[var(--problem-detail-border)] bg-[var(--problem-detail-surface)]">
                      <summary className="cursor-pointer px-3 py-2 font-mono text-[10px] text-[var(--problem-detail-muted)] hover:text-[var(--problem-detail-text)]">
                        전체 실행 로그
                      </summary>
                      {codeRun.stderr && (
                        <pre className="workspace-scrollbar m-0 max-h-48 overflow-auto border-t border-[var(--problem-detail-border)] p-3 font-mono text-[9px] leading-[1.6] whitespace-pre-wrap text-[#ff786b]">
                          {codeRun.stderr}
                        </pre>
                      )}
                      {codeRun.stdout && (
                        <pre className="workspace-scrollbar m-0 max-h-48 overflow-auto border-t border-[var(--problem-detail-border)] p-3 font-mono text-[9px] leading-[1.6] whitespace-pre-wrap text-[var(--problem-detail-muted)]">
                          {codeRun.stdout}
                        </pre>
                      )}
                    </details>
                  )}
                </div>
              ) : (
                <div className="grid min-h-[160px] place-items-center text-center font-mono text-[11px] leading-[1.7] text-[var(--problem-detail-subtle)]">
                  {/*
                    남의 제출은 우리가 돌려 채울 수 없다. 채점 없이 제출했거나 아직
                    채점 중이면 보여 줄 기록이 없고, 그건 오류가 아니라 사실이다.
                  */}
                  {isOthersAttempt
                    ? '이 제출에는 기록된 채점 결과가 없습니다.'
                    : '테스트 결과가 없습니다.'}
                </div>
              )}
            </div>
          </section>

          {!isPlaying && (
            <section className="flex min-h-0 flex-col overflow-hidden pt-2 max-[1080px]:overflow-visible max-[1080px]:pt-0 max-[700px]:pt-[22px]">
            <div className={koreanLabelClasses}>프롬프트 / 최대 4,000자</div>
            <div className="relative mt-2 shrink-0 border border-[var(--problem-detail-border-strong)] bg-[var(--problem-detail-input-bg)] focus-within:border-[var(--problem-detail-acid)]">
              <textarea
                aria-keyshortcuts="Control+Enter Meta+Enter"
                className="workspace-scrollbar block h-[108px] w-full resize-none overflow-y-auto border-0 bg-transparent py-3 pr-16 pl-3.5 text-[13px] leading-[1.6] text-[var(--problem-detail-text)] outline-0 [scrollbar-gutter:stable] disabled:cursor-not-allowed disabled:opacity-60"
                disabled={isRunning || isSubmitting || isSubmitted}
                maxLength={4000}
                onChange={(event) => setPrompt(event.target.value)}
                onKeyDown={handlePromptKeyDown}
                placeholder={
                  isOthersAttempt
                    ? '다른 사람의 풀이라 프롬프트를 쓸 수 없습니다.'
                    : isSubmitted
                      ? '제출이 완료된 어템프트입니다.'
                      : '문제를 해결할 프롬프트를 입력하세요.'
                }
                rows={1}
                value={prompt}
              />
              <button
                aria-label="프롬프트 실행"
                className="absolute right-4 bottom-2 grid size-7 cursor-pointer place-items-center rounded-full border border-[var(--problem-detail-acid)] bg-[var(--problem-detail-acid)] text-[#090909] transition-colors hover:bg-transparent hover:text-[var(--problem-detail-acid)] disabled:cursor-not-allowed disabled:opacity-45"
                disabled={isRunning || isSubmitting || isSubmitted || !prompt.trim()}
                onClick={handleRun}
                title="프롬프트 실행"
                type="button"
              >
                {isRunning ? (
                  <Spinner size={15} />
                ) : (
                  <svg
                    aria-hidden="true"
                    className="size-[17px]"
                    fill="none"
                    viewBox="0 0 18 18"
                  >
                    <path
                      d="M9 15V3M4.5 7.5 9 3l4.5 4.5"
                      stroke="currentColor"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth="2.8"
                    />
                  </svg>
                )}
              </button>
            </div>
            <div className="mt-1 flex items-center justify-between gap-3 px-3.5 font-mono text-[9px] text-[var(--problem-detail-subtle)]">
              <span>Ctrl/Cmd + Enter 전송 · Enter 줄바꿈</span>
              <span>
                <b>{prompt.length.toLocaleString('ko-KR')}</b> / 4,000
              </span>
            </div>

            <div className="mt-auto pt-2">
              {/* 색을 덧칠하지 않고 상태마다 한 벌씩 고른다 — 같은 속성을 두 번
                  적으면 어느 쪽이 이길지는 Tailwind가 CSS를 찍은 순서가 정한다. */}
              {status && (
                <div
                  className={[
                    'mb-2 border p-2 font-mono text-[10px] leading-[1.5]',
                    status.type === 'error'
                      ? 'border-[#ff786b] text-[#ff786b]'
                      : 'border-[var(--problem-detail-border-strong)] text-[var(--problem-detail-muted)]',
                  ].join(' ')}
                  role="status"
                >
                  {status.message}
                </div>
              )}

              {/*
                제출은 곧 공개다. 확인 다이얼로그를 새로 세우면 지금 한 번에 끝나는
                제출 흐름이 통째로 바뀌므로, 버튼에 붙은 한 줄로만 알린다.

                제출이 끝나면 지운다 — 그때 버튼은 피드백을 여는 문이고, 바로 위
                상태 칸이 이미 제출됐다고 말하고 있다. 이미 참인 사실은 공개된
                내용이 실제로 있는 피드백 화면(주인 띠)이 계속 이고 있다.
              */}
              {!isSubmitted && (
                <p className="mb-2 font-mono text-[10px] leading-[1.5] text-[var(--problem-detail-muted)]">
                  제출하면 이 풀이와 피드백을 누구나 볼 수 있습니다.
                </p>
              )}

              <Button
                className="problem-detail-primary-action"
                disabled={isRunning || isSubmitting || !(canSubmit || isSubmitted)}
                fullWidth
                onClick={handleSubmit}
              >
                {/*
                  남의 풀이에서는 제출을 말하지 않는다. 버튼은 활성인 채로 두는데,
                  랭킹에서 이 주소로 들어온 사람에게 그 사람 피드백으로 가는 길이
                  여기뿐이기 때문이다 — handleSubmit도 제출된 어템프트면 바로
                  피드백으로 넘긴다. 문구만 실제로 하는 일에 맞춘다.
                */}
                {isSubmitting && <Spinner size={15} />}
                <span className="text-[14px]">
                  {isSubmitting
                    ? '제출 중…'
                    : isOthersAttempt
                      ? '이 풀이의 피드백 보기 ↗'
                      : '최종 제출 & 피드백 확인하기 ↗'}
                </span>
              </Button>
            </div>

            </section>
          )}
        </aside>
      </main>
    </div>
  );
}
