import {
  type KeyboardEvent,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import ReactMarkdown from 'react-markdown';
import { useNavigate, useParams } from 'react-router-dom';

import {
  addTurn,
  createAttempt,
  getAttempt,
  getCodeRun,
  getCodeRuns,
  requestCodeRun,
  submitAttempt,
} from '../features/attempt/api';
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
import {
  ApiError,
  API_ERROR_CODES,
  createIdempotencyKey,
  isAbortError,
} from '../shared/api/apiClient';
import Button from '../shared/components/Button';
import Header from '../shared/components/Header';
import type { ErrorPageState } from '../shared/types/error';

type StatusType = 'normal' | 'error';

interface StatusMessage {
  message: string;
  type: StatusType;
}

type DetailTab = 'problem' | 'logs' | 'test';

const detailTabs: ReadonlyArray<readonly [DetailTab, string]> = [
  ['problem', 'PROBLEM'],
  ['logs', 'PROMPT LOG'],
  ['test', 'TEST'],
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
  'font-mono text-sm leading-[1.5] font-bold tracking-[0.08em] text-[#d6ff50]';

const pageStateClasses =
  'grid min-h-dvh place-items-center bg-[#090909] p-10 ' +
  'font-mono text-xs leading-[1.7] text-[#a3a3a3]';

const changeColorClasses: Record<ChangeType, string> = {
  ADDED: 'text-[#d6ff50]',
  MODIFIED: 'text-white',
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
  PASSED: 'PASSED',
  FAILED: 'FAILED',
  ERROR: 'ERROR',
  SKIPPED: 'SKIPPED',
};

const codeRunCaseColorClasses: Record<CodeRunCaseStatus, string> = {
  PASSED: 'text-[#d6ff50]',
  FAILED: 'text-[#ff786b]',
  ERROR: 'text-[#ffb86b]',
  SKIPPED: 'text-[#8b8b8b]',
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
  const detailTabRefs = useRef<
    Partial<Record<DetailTab, HTMLButtonElement | null>>
  >({});
  const isRunPendingRef = useRef(false);
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
  routeAttemptIdRef.current = routeAttemptId;
  /** 마지막으로 정상 반영된 라우트. 다른 주소의 로드 실패 시 이전 문제를 지우는 기준이다. */
  const loadedResourceRef = useRef<string | null>(null);
  /**
   * 지금 화면에 올라와 있는 문제. problem state와 같은 값이지만, loadFromRoute가
   * 렌더마다 새로 만들어져 낡은 state를 붙들 수 있어 ref로 따로 들고 읽는다.
   */
  const problemRef = useRef<ProblemDetail | null>(null);

  const files = attempt?.files ?? problem?.files ?? [];
  const turns = attempt?.turns ?? [];
  const attemptTokenUsage = getTokenUsageTotal(attempt?.usage);
  const turnTokenUsages = turns.map((turn) => getTokenUsageTotal(turn.usage));
  const hasTokenUsage =
    attemptTokenUsage !== null || turnTokenUsages.some((usage) => usage !== null);
  const totalTokenUsage =
    attemptTokenUsage ??
    turnTokenUsages.reduce<number>((total, usage) => total + (usage ?? 0), 0);
  const isSubmitted = attempt?.status === 'SUBMITTED';
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
        setStatus({
          message: '이미 제출된 어템프트입니다. 피드백만 확인할 수 있습니다.',
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

    if (routeAttemptId === null) return;

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
  }, [routeAttemptId]);

  const fileTree = useMemo(
    () => createFileTree(files, getLatestChangedFiles(turns)),
    [files, turns],
  );

  const selectedCode = useMemo(() => {
    if (!selectedFile) return '// 파일을 선택해주세요.';
    return findFile(files, selectedFile)?.content ?? '// 이 턴에서 삭제된 파일입니다.';
  }, [files, selectedFile]);

  const showStatus = (message: string, type: StatusType = 'normal') => {
    setStatus({ message, type });
  };

  const handleCodeRunRequest = async () => {
    if (!attempt || turns.length === 0 || isCodeRunLoading) return;

    stopCodeRunPolling();
    const requestedAttemptId = attempt.id;
    const controller = new AbortController();
    codeRunControllerRef.current = controller;
    setCodeRunError(null);
    setCodeRunTally(null);
    setIsCodeRunLoading(true);

    try {
      const requestedRun = await requestCodeRun(
        requestedAttemptId,
        controller.signal,
      );
      if (
        controller.signal.aborted ||
        routeAttemptIdRef.current !== requestedAttemptId
      ) {
        return;
      }

      applyCodeRun(requestedRun);

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
          const response = await getCodeRuns(
            requestedAttemptId,
            controller.signal,
          );
          if (
            controller.signal.aborted ||
            routeAttemptIdRef.current !== requestedAttemptId
          ) {
            return;
          }

          const queuedRun = response.runs.find((run) => run.status === 'QUEUED');

          if (queuedRun) {
            setCodeRunTally(queuedRun.tally);
            startCodeRunPolling(requestedAttemptId, queuedRun.runId);
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
            if (
              controller.signal.aborted ||
              routeAttemptIdRef.current !== requestedAttemptId
            ) {
              return;
            }

            setCodeRunTally(latestRun.tally);
            applyCodeRun(restoredRun);
            if (restoredRun.status === 'QUEUED') {
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
    }
  };

  const handleDetailTabClick = (tab: DetailTab) => {
    setActiveTab(tab);
    if (tab === 'test') void handleCodeRunRequest();
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
    let nextIndex: number | null = null;

    if (event.key === 'ArrowRight') {
      nextIndex = (currentIndex + 1) % detailTabs.length;
    } else if (event.key === 'ArrowLeft') {
      nextIndex = (currentIndex - 1 + detailTabs.length) % detailTabs.length;
    } else if (event.key === 'Home') {
      nextIndex = 0;
    } else if (event.key === 'End') {
      nextIndex = detailTabs.length - 1;
    }

    if (nextIndex === null) return;

    event.preventDefault();
    const nextTab = detailTabs[nextIndex][0];
    setActiveTab(nextTab);
    detailTabRefs.current[nextTab]?.focus();
    if (nextTab === 'test') void handleCodeRunRequest();
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
              className="grid min-h-[30px] w-full cursor-pointer grid-cols-[14px_14px_max-content] items-center gap-1 border-0 bg-transparent pr-2 text-left font-inherit text-[#a3a3a3] hover:text-[#f5f5ef]"
              onClick={() => toggleFolder(item.path)}
              style={{ paddingLeft: `${7 + depth * 14}px` }}
              type="button"
            >
              <span className="text-[10px] text-[#777]" aria-hidden="true">
                {isExpanded ? '▼' : '▶'}
              </span>
              <span className="text-[13px] text-[#d6ff50]" aria-hidden="true">
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
            'grid min-h-[30px] w-full cursor-pointer grid-cols-[14px_max-content_28px] items-center gap-1 border-0 bg-transparent pr-2 text-left font-inherit text-inherit hover:text-[#f5f5ef]',
            isSelected
              ? 'bg-[#d6ff50] text-[#090909] hover:text-[#090909]'
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
                  backgroundColor: '#d6ff50',
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
              isSelected ? 'text-[#090909]' : 'text-[#777]',
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
      <div className={pageStateClasses}>문제 상세를 불러오는 중입니다…</div>
    );
  }

  if (!problem) {
    const notice = loadError ?? toProblemsError('문제를 찾을 수 없습니다.');

    return (
      <div className={`${pageStateClasses} text-[#ff786b]`}>
        <div>
          <p>{notice.message}</p>
          <Button className="mt-5" to={notice.actionTo}>
            <span className="text-[14px]">{notice.actionLabel}</span>
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-screen min-w-80 flex-col overflow-hidden bg-[#090909] text-[#f5f5ef] [font-family:Arial,'Noto_Sans_KR',sans-serif] max-[700px]:h-auto max-[700px]:min-h-screen max-[700px]:overflow-visible">
      <Header variant="workspace" />

      <main className="grid min-h-0 flex-1 overflow-hidden grid-cols-[230px_minmax(360px,1fr)_minmax(420px,480px)] max-[1080px]:grid-cols-[190px_minmax(0,1fr)] max-[700px]:block max-[700px]:overflow-visible">
        <aside className="flex min-h-0 min-w-0 flex-col overflow-hidden border-r border-[#343434] px-6 py-[22px] max-[700px]:overflow-visible max-[700px]:border-r-0 max-[700px]:border-b max-[700px]:px-4 max-[700px]:py-[18px]">
          <div className={labelClasses}>FILE EXPLORER</div>

          <div className="workspace-scrollbar mt-[18px] min-h-0 flex-1 overflow-auto max-[700px]:flex-none max-[700px]:overflow-visible">
            <div className="grid w-max min-w-full select-none gap-[3px] font-mono text-xs leading-[1.5] text-[#a3a3a3]">
              {renderFileTree(fileTree)}
            </div>
          </div>

          <div className="mt-4 grid shrink-0 gap-2 border-t border-[#343434] pt-4 font-mono text-[9px] text-[#767676]">
            <span>A / ADDED</span>
            <span>M / MODIFIED</span>
            <span>D / DELETED</span>
          </div>
        </aside>

        <section className="flex min-h-0 min-w-0 flex-col overflow-hidden border-r border-[#343434] px-7 py-[22px] max-[1080px]:border-r-0 max-[700px]:block max-[700px]:overflow-visible max-[700px]:border-b max-[700px]:px-4 max-[700px]:pt-5 max-[700px]:pb-[30px]">
          <div className="mb-5 flex items-start justify-between gap-[18px]">
            <div>
              <div className={labelClasses}>
                {selectedFile || 'FILE'}
              </div>
            </div>
            <span className="shrink-0 border border-[#494949] px-2 py-1.5 font-mono text-[9px] text-[#a3a3a3]">
              READ ONLY
            </span>
          </div>

          <div className="workspace-scrollbar min-h-0 flex-1 overflow-auto border border-[#292929] bg-[#202020] max-[700px]:min-h-[360px]">
            <div
              aria-hidden="true"
              className="sticky top-0 z-[2] h-8 min-w-full border-b border-[#333] bg-[#151515]"
            />
            <div className="min-w-max py-3 font-mono text-xs leading-[1.9] whitespace-pre text-[#e3e3dd] [tab-size:2]">
              {selectedCode.split('\n').map((line, index) => (
                <div
                  className="grid min-h-[1.9em] grid-cols-[2.0rem_max-content]"
                  key={`${index}-${line}`}
                >
                  <span
                    aria-hidden="true"
                    className="sticky left-0 border-r border-[#333] bg-[#202020] pr-3 text-right text-[#686868] select-none"
                  >
                    {index + 1}
                  </span>
                  <code className="px-5">{line || ' '}</code>
                </div>
              ))}
            </div>
          </div>
        </section>

        <aside className="col-span-1 grid min-h-0 min-w-0 grid-rows-[minmax(0,1fr)_auto] overflow-hidden px-6 py-[22px] max-[1080px]:col-span-full max-[1080px]:grid-rows-1 max-[1080px]:grid-cols-[minmax(0,0.8fr)_minmax(300px,1.2fr)] max-[1080px]:gap-7 max-[1080px]:overflow-visible max-[1080px]:border-t max-[1080px]:border-[#343434] max-[700px]:block max-[700px]:px-4 max-[700px]:pt-5 max-[700px]:pb-[30px]">
          <section className="flex min-h-0 flex-1 flex-col overflow-hidden border-b border-[#343434] pb-[22px] max-[1080px]:border-b-0 max-[1080px]:pb-0 max-[700px]:overflow-visible">
            <div
              className="grid shrink-0 grid-cols-3 border border-[#3f3f3f]"
              role="tablist"
              aria-label="문제 상세 정보"
            >
              {detailTabs.map(([tab, label]) => (
                <button
                  aria-controls="problem-detail-tabpanel"
                  aria-selected={activeTab === tab}
                  className={[
                    'min-h-10 cursor-pointer border-0 bg-transparent px-3 font-mono text-sm leading-[1.5] font-bold tracking-[0.08em]',
                    tab !== 'test' ? 'border-r border-[#3f3f3f]' : '',
                    activeTab === tab
                      ? 'border-b-2 border-b-[#d6ff50] text-[#d6ff50]'
                      : 'text-[#8b8b8b] hover:text-[#b8b8b8]',
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
                  {label}
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
                  <div className="m-0 whitespace-pre-wrap text-[13px] leading-[1.7] text-[#a3a3a3] [word-break:keep-all]">
                    <ReactMarkdown
                      components={{
                        h1: ({ children }) => (
                          <h1 className="my-3 mt-2.5 text-[24px] leading-[1.05] font-bold tracking-[-0.045em] text-[#f5f5ef]">
                            {children}
                          </h1>
                        ),
                        h2: ({ children }) => <h2 className="font-bold text-[#f5f5ef]">{children}</h2>,
                        h3: ({ children }) => <h3 className="font-bold text-[#f5f5ef]">{children}</h3>,
                        h4: ({ children }) => <h4 className="font-bold text-[#f5f5ef]">{children}</h4>,
                        h5: ({ children }) => <h5 className="font-bold text-[#f5f5ef]">{children}</h5>,
                        h6: ({ children }) => <h6 className="font-bold text-[#f5f5ef]">{children}</h6>,
                      }}
                    >
                      {problem.specMd}
                    </ReactMarkdown>
                  </div>
                </>
              ) : activeTab === 'logs' && turns.length ? (
                <div className="grid gap-4">
                  <div className="flex items-end justify-between border border-[#3f3f3f] bg-[#111] px-4 py-3">
                    <div>
                      <p className="m-0 font-mono text-[9px] font-bold tracking-[0.12em] text-[#777]">
                        TOTAL TOKEN USAGE
                      </p>
                      <p className="mt-1 mb-0 text-[12px] text-[#f5f5ef]">
                        전체 프롬프트 토큰 사용량
                      </p>
                    </div>
                    <strong className="font-mono text-xl text-[#d6ff50]">
                      {formatUsageValue(hasTokenUsage ? totalTokenUsage : null)}
                    </strong>
                  </div>

                  {turns.map((turn, index) => (
                    <article
                      className="border-l-2 border-[#d6ff50] pl-3"
                      key={`turn-${index + 1}`}
                    >
                      <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 font-mono text-[11px] font-bold">
                        <span className="text-[#d6ff50]">
                          TURN {String(index + 1).padStart(2, '0')}
                        </span>
                        <span className="flex gap-3 text-[#777]">
                          <span>
                            TOKENS{' '}
                            <strong className="text-[#c7c7c2]">
                              {formatUsageValue(turnTokenUsages[index])}
                            </strong>
                          </span>
                          <span>
                            LATENCY{' '}
                            <strong className="text-[#c7c7c2]">
                              {formatUsageValue(turn.usage?.latencyMs, 'ms')}
                            </strong>
                          </span>
                        </span>
                      </div>
                      <p className="my-2 whitespace-pre-wrap text-[12px] leading-[1.6] text-[#f5f5ef]">
                        {turn.prompt}
                      </p>
                      <p className="m-0 whitespace-pre-wrap text-[11px] leading-[1.6] text-[#8f8f8f]">
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
                <div className="grid min-h-[160px] place-items-center text-center font-mono text-[11px] leading-[1.7] text-[#666]">
                  실행한 프롬프트가 없습니다.
                </div>
              ) : !attempt || turns.length === 0 ? (
                <div className="grid min-h-[160px] place-items-center text-center font-mono text-[11px] leading-[1.7] text-[#666]">
                  프롬프트 실행 후 테스트할 수 있습니다.
                </div>
              ) : codeRunError ? (
                <div className="grid min-h-[160px] place-items-center text-center font-mono text-[11px] leading-[1.7] text-[#ff786b]">
                  <div>
                    <p className="m-0">{codeRunError}</p>
                    <button
                      className="mt-3 cursor-pointer border border-[#666] bg-transparent px-3 py-1.5 text-[10px] text-[#d0d0ca] hover:border-[#d6ff50] hover:text-[#d6ff50]"
                      onClick={() => void handleCodeRunRequest()}
                      type="button"
                    >
                      다시 채점하기
                    </button>
                  </div>
                </div>
              ) : isCodeRunLoading || codeRun?.status === 'QUEUED' ? (
                <div className="grid min-h-[160px] place-items-center text-center font-mono text-[11px] leading-[1.7] text-[#a3a3a3]">
                  <div>
                    <div className="text-[#d6ff50]">채점 중…</div>
                    <div className="mt-2 text-[9px] text-[#666]">
                      완료될 때까지 잠시 기다려주세요.
                    </div>
                  </div>
                </div>
              ) : codeRun ? (
                <div className="[font-family:Arial,'Noto_Sans_KR',sans-serif]">
                  <div className="border border-[#3f3f3f] bg-[#111] px-4 py-3">
                    <div className="flex items-end justify-between gap-4">
                      <div>
                        <p className="m-0 font-mono text-[9px] font-bold tracking-[0.12em] text-[#777]">
                          TEST RESULT
                        </p>
                        <p className="mt-1 mb-0 text-[12px] text-[#f5f5ef]">
                          테스트 케이스 채점 결과
                        </p>
                      </div>
                      {visibleCodeRunTally ? (
                        <div className="flex shrink-0 items-baseline gap-1 leading-none">
                          <strong className="font-mono text-xl text-[#d6ff50]">
                            {visibleCodeRunTally.passed}/{visibleCodeRunTally.total}
                          </strong>
                          <span className="text-[10px] leading-none text-[#8b8b8b]">
                            개 통과
                          </span>
                        </div>
                      ) : (
                        <strong
                          className={`font-mono text-[11px] ${
                            codeRun.status === 'SUCCEEDED'
                              ? 'text-[#d6ff50]'
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
                        className="mt-4 h-1.5 overflow-hidden bg-[#303030]"
                        role="progressbar"
                      >
                        <div
                          className="h-full bg-[#d6ff50]"
                          style={{
                            width: `${(visibleCodeRunTally.passed / visibleCodeRunTally.total) * 100}%`,
                          }}
                        />
                      </div>
                    )}

                    <div className="mt-3 flex flex-wrap gap-x-3 gap-y-1 font-mono text-[9px] text-[#777]">
                      <span>STATUS {codeRunStatusLabels[codeRun.status]}</span>
                      {codeRun.turnOrdinal !== null && (
                        <span>TURN {String(codeRun.turnOrdinal + 1).padStart(2, '0')}</span>
                      )}
                      {codeRun.durationMs !== null && (
                        <span>{codeRun.durationMs.toLocaleString('ko-KR')}ms</span>
                      )}
                    </div>
                  </div>

                  {codeRun.cases.length > 0 ? (
                    <div className="mt-3 grid border-x border-t border-[#343434]">
                      {codeRun.cases.map((testCase, index) => (
                        <article
                          className="border-b border-[#343434] px-3 py-2.5"
                          key={`${testCase.className ?? 'case'}-${testCase.name}-${index}`}
                        >
                          <div className="flex items-start justify-between gap-3">
                            <div className="min-w-0">
                              <p className="m-0 break-words text-[11px] leading-[1.5] text-[#d0d0ca]">
                                {testCase.name}
                              </p>
                              <div className="mt-1 flex flex-wrap gap-2 font-mono text-[8px] text-[#666]">
                                {testCase.className && <span>{testCase.className}</span>}
                                {testCase.durationMs !== null && (
                                  <span>{testCase.durationMs.toLocaleString('ko-KR')}ms</span>
                                )}
                              </div>
                            </div>
                            <strong
                              className={`shrink-0 font-mono text-[10px] ${codeRunCaseColorClasses[testCase.status]}`}
                            >
                              {codeRunCaseLabels[testCase.status]}
                            </strong>
                          </div>
                          {(testCase.status === 'FAILED' ||
                            testCase.status === 'ERROR') &&
                            testCase.message && (
                              <p className="mt-2 mb-0 break-words border-l-2 border-[#555] pl-2 font-mono text-[9px] leading-[1.5] text-[#999]">
                                {testCase.message}
                              </p>
                            )}
                        </article>
                      ))}
                    </div>
                  ) : (
                    <div className="mt-3 border border-[#343434] px-3 py-4 text-center text-[11px] text-[#777]">
                      테스트 케이스 기록이 없습니다.
                    </div>
                  )}

                  {(codeRun.stdout || codeRun.stderr) && (
                    <details className="mt-3 border border-[#343434] bg-[#111]">
                      <summary className="cursor-pointer px-3 py-2 font-mono text-[10px] text-[#a3a3a3] hover:text-[#f5f5ef]">
                        전체 실행 로그
                      </summary>
                      {codeRun.stderr && (
                        <pre className="workspace-scrollbar m-0 max-h-48 overflow-auto border-t border-[#343434] p-3 font-mono text-[9px] leading-[1.6] whitespace-pre-wrap text-[#ff786b]">
                          {codeRun.stderr}
                        </pre>
                      )}
                      {codeRun.stdout && (
                        <pre className="workspace-scrollbar m-0 max-h-48 overflow-auto border-t border-[#343434] p-3 font-mono text-[9px] leading-[1.6] whitespace-pre-wrap text-[#aaa]">
                          {codeRun.stdout}
                        </pre>
                      )}
                    </details>
                  )}
                </div>
              ) : (
                <div className="grid min-h-[160px] place-items-center text-center font-mono text-[11px] leading-[1.7] text-[#666]">
                  테스트 결과가 없습니다.
                </div>
              )}
            </div>
          </section>

          <section className="flex min-h-0 flex-col overflow-hidden pt-2 max-[1080px]:overflow-visible max-[1080px]:pt-0 max-[700px]:pt-[22px]">
            <div className={labelClasses}>PROMPT / MAX 4,000</div>
            <div className="relative mt-2 shrink-0 border border-[#555] bg-[#131313] focus-within:border-[#d6ff50]">
              <textarea
                aria-keyshortcuts="Control+Enter Meta+Enter"
                className="workspace-scrollbar block h-[108px] w-full resize-none overflow-y-auto border-0 bg-transparent py-3 pr-16 pl-3.5 text-[13px] leading-[1.6] text-[#f5f5ef] outline-0 [scrollbar-gutter:stable] disabled:cursor-not-allowed disabled:opacity-60"
                disabled={isRunning || isSubmitting || isSubmitted}
                maxLength={4000}
                onChange={(event) => setPrompt(event.target.value)}
                onKeyDown={handlePromptKeyDown}
                placeholder={
                  isSubmitted
                    ? '제출이 완료된 어템프트입니다.'
                    : '문제를 해결할 프롬프트를 입력하세요.'
                }
                rows={1}
                value={prompt}
              />
              <button
                aria-label="프롬프트 실행"
                className="absolute right-4 bottom-2 grid size-7 cursor-pointer place-items-center rounded-full border border-[#d6ff50] bg-[#d6ff50] text-[#090909] transition-colors hover:bg-transparent hover:text-[#d6ff50] disabled:cursor-not-allowed disabled:opacity-45"
                disabled={isRunning || isSubmitting || isSubmitted || !prompt.trim()}
                onClick={handleRun}
                title="프롬프트 실행"
                type="button"
              >
                {isRunning ? (
                  '…'
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
            <div className="mt-1 flex items-center justify-between gap-3 px-3.5 font-mono text-[9px] text-[#777]">
              <span>Ctrl/Cmd + Enter 전송 · Enter 줄바꿈</span>
              <span>
                <b>{prompt.length.toLocaleString('ko-KR')}</b> / 4,000
              </span>
            </div>

            <div className="mt-auto pt-2">
              {status && (
                <div
                  className={[
                    'mb-2 border border-[#484848] p-2 font-mono text-[10px] leading-[1.5] text-[#a3a3a3]',
                    status.type === 'error'
                      ? 'border-[#ff786b] text-[#ff786b]'
                      : '',
                  ].join(' ')}
                  role="status"
                >
                  {status.message}
                </div>
              )}

              <Button
                disabled={isRunning || isSubmitting || !(canSubmit || isSubmitted)}
                fullWidth
                onClick={handleSubmit}
              >
                <span className="text-[14px]">
                  {isSubmitting ? 'LOADING…' : '최종 제출 & 피드백 확인하기 ↗'}
                </span>
              </Button>
            </div>

          </section>
        </aside>
      </main>
    </div>
  );
}
