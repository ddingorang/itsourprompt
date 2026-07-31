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
  submitAttempt,
} from '../features/attempt/api';
import {
  clearAttemptId,
  getAttemptId,
  saveAttemptId,
} from '../features/attempt/storage';
import type {
  Attempt,
  ChangedFile,
  ChangeType,
  Turn,
} from '../features/attempt/types';
import { getProblemDetail } from '../features/problem/api';
import type { ProblemDetail, RepositoryFile } from '../features/problem/types';
import {
  ApiError,
  API_ERROR_CODES,
  createIdempotencyKey,
} from '../shared/api/apiClient';
import Button from '../shared/components/Button';
import Header from '../shared/components/Header';
import type { ErrorPageState } from '../shared/types/error';

type StatusType = 'normal' | 'error';

interface StatusMessage {
  message: string;
  type: StatusType;
}

type DetailTab = 'problem' | 'logs';

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

const toolLabels: Record<string, string> = {
  edit_file: 'EDIT',
  list_files: 'LIST',
  read_file: 'READ',
};

interface ErrorInfo {
  message: string;
  status?: number;
  code?: string;
}

function getErrorInfo(error: unknown, fallback: string): ErrorInfo {
  if (error instanceof ApiError) {
    return { code: error.code, message: error.message, status: error.status };
  }

  return { message: fallback };
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
  const { problemId: problemIdParam } = useParams();
  const problemId = Number(problemIdParam ?? 1);

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
  const [isLoading, setIsLoading] = useState(true);
  const [isRunning, setIsRunning] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const promptTextareaRef = useRef<HTMLTextAreaElement>(null);
  const isRunPendingRef = useRef(false);
  /**
   * 실패한 턴 요청의 Idempotency-Key를 기억한다. 같은 프롬프트로 다시 실행하면
   * 같은 키가 나가므로, 백엔드가 이미 AI를 호출해 둔 경우 재호출 없이 그 결과를
   * 돌려준다(AI 호출이 수 분 걸리므로 중복 호출 비용이 크다).
   */
  const pendingRunRef = useRef<{ prompt: string; key: string } | null>(null);

  const files = attempt?.files ?? problem?.files ?? [];
  const turns = attempt?.turns ?? [];
  const isSubmitted = attempt?.status === 'SUBMITTED';
  const canSubmit = turns.length > 0 && !isSubmitted;

  useEffect(() => {
    let isMounted = true;

    const load = async () => {
      try {
        const detail = await getProblemDetail(problemId);
        if (!isMounted) return;

        setProblem(detail);

        // 이전에 시작해 둔 어템프트가 있으면 서버 상태를 복원한다.
        const savedAttemptId = getAttemptId(problemId);
        let restored: Attempt | null = null;

        if (savedAttemptId !== null) {
          try {
            restored = await getAttempt(savedAttemptId);
          } catch (error: unknown) {
            // 어템프트가 사라졌으면(404) 저장된 ID를 버리고 새로 시작한다.
            if (
              error instanceof ApiError &&
              error.code === API_ERROR_CODES.attemptNotFound
            ) {
              clearAttemptId(problemId);
            } else {
              throw error;
            }
          }
        }

        if (!isMounted) return;

        const visibleFiles = restored?.files ?? detail.files;
        const changedFiles = restored ? getLatestChangedFiles(restored.turns) : [];

        setAttempt(restored);
        setSelectedFile(visibleFiles[0]?.path ?? '');
        setExpandedFolders(
          new Set(getFolderPaths(createFileTree(visibleFiles, changedFiles))),
        );

        if (restored?.turns.length) {
          setActiveTab('logs');
        }

        if (restored?.status === 'SUBMITTED') {
          setStatus({
            message: '이미 제출된 어템프트입니다. 피드백만 확인할 수 있습니다.',
            type: 'normal',
          });
        }
      } catch (error: unknown) {
        if (isMounted) {
          setStatus({
            type: 'error',
            message: getErrorInfo(error, '문제 상세를 불러오지 못했습니다.').message,
          });
        }
      } finally {
        if (isMounted) setIsLoading(false);
      }
    };

    void load();

    return () => {
      isMounted = false;
    };
  }, [problemId]);

  useEffect(() => {
    const textarea = promptTextareaRef.current;
    if (!textarea) return;

    const maxHeight = status ? 82 : 112;
    textarea.style.height = 'auto';
    textarea.style.height = `${Math.min(textarea.scrollHeight, maxHeight)}px`;
  }, [prompt, status]);

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

  /** 갱신된 어템프트 상태를 화면에 반영한다. */
  const applyAttempt = (next: Attempt) => {
    setAttempt(next);
    saveAttemptId(problemId, next.id);
    setExpandedFolders((folders) => {
      const nextFolders = new Set(folders);
      getFolderPaths(
        createFileTree(next.files, getLatestChangedFiles(next.turns)),
      ).forEach((path) => nextFolders.add(path));
      return nextFolders;
    });
    setSelectedFile((current) =>
      findFile(next.files, current) ? current : (next.files[0]?.path ?? ''),
    );
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

    setIsRunning(true);
    showStatus('프롬프트를 실행하고 있습니다. 완료될 때까지 잠시 기다려주세요.');

    try {
      // 첫 실행이면 어템프트를 먼저 만든다(AI 호출 없음).
      let current = attempt;
      if (!current) {
        current = await createAttempt(problem.id);
        setAttempt(current);
        saveAttemptId(problemId, current.id);
      }

      const updated = await addTurn(current.id, trimmedPrompt, idempotencyKey);

      applyAttempt(updated);
      setActiveTab('logs');
      setPrompt('');
      pendingRunRef.current = null;
      showStatus('실행이 완료되었습니다. 변경 파일과 AI 응답을 확인해주세요.');
    } catch (error: unknown) {
      const errorInfo = getErrorInfo(
        error,
        '실행에 실패했습니다. 다시 시도해주세요.',
      );

      // 이미 제출된 어템프트에는 턴을 더할 수 없다 — 상태를 서버와 맞춘다.
      if (errorInfo.code === API_ERROR_CODES.attemptAlreadySubmitted) {
        setAttempt((current) =>
          current ? { ...current, status: 'SUBMITTED' } : current,
        );
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

  const handleSubmit = async () => {
    if (!problem || !attempt) return;

    // 이미 제출된 어템프트는 저장된 피드백을 그대로 보여준다.
    if (isSubmitted) {
      navigate(`/feedback/${attempt.id}`);
      return;
    }

    setIsSubmitting(true);
    showStatus('프롬프트 피드백을 생성하고 있습니다.');

    try {
      await submitAttempt(attempt.id);

      setAttempt((current) =>
        current ? { ...current, status: 'SUBMITTED' } : current,
      );
      navigate(`/feedback/${attempt.id}`);
    } catch (error: unknown) {
      const errorInfo = getErrorInfo(
        error,
        '피드백 생성에 실패했습니다. 잠시 후 다시 제출해주세요.',
      );

      // 이미 제출됐다면 오류가 아니라 피드백 화면으로 보내는 편이 자연스럽다.
      if (errorInfo.code === API_ERROR_CODES.attemptAlreadySubmitted) {
        navigate(`/feedback/${attempt.id}`);
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

  if (isLoading) {
    return (
      <div className={pageStateClasses}>문제 상세를 불러오는 중입니다…</div>
    );
  }

  if (!problem) {
    return (
      <div className={`${pageStateClasses} text-[#ff786b]`}>
        <div>
          <p>{status?.message ?? '문제를 찾을 수 없습니다.'}</p>
          <Button className="mt-5" to="/problems">
            BACK TO PROBLEMS ↗
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

        <aside className="col-span-1 grid min-h-0 min-w-0 grid-rows-[11fr_9fr] overflow-hidden px-6 py-[22px] max-[1080px]:col-span-full max-[1080px]:grid-rows-1 max-[1080px]:grid-cols-[minmax(0,0.8fr)_minmax(300px,1.2fr)] max-[1080px]:gap-7 max-[1080px]:overflow-visible max-[1080px]:border-t max-[1080px]:border-[#343434] max-[700px]:block max-[700px]:px-4 max-[700px]:pt-5 max-[700px]:pb-[30px]">
          <section className="workspace-scrollbar min-h-0 flex-1 overflow-y-auto overflow-x-hidden border-b border-[#343434] pb-[22px] max-[1080px]:overflow-visible max-[1080px]:border-b-0 max-[1080px]:pb-0">
            <div
              className="grid grid-cols-2 border border-[#3f3f3f]"
              role="tablist"
              aria-label="문제 상세 정보"
            >
              {([
                ['problem', 'PROBLEM'],
                ['logs', `PROMPT LOG ${turns.length ? `(${turns.length})` : ''}`],
              ] as const).map(([tab, label]) => (
                <button
                  aria-selected={activeTab === tab}
                  className={[
                    'min-h-10 cursor-pointer border-0 bg-transparent px-3 font-mono text-sm leading-[1.5] font-bold tracking-[0.08em]',
                    tab === 'problem' ? 'border-r border-[#3f3f3f]' : '',
                    activeTab === tab
                      ? 'border-b-2 border-b-[#d6ff50] text-[#d6ff50]'
                      : 'text-[#8b8b8b] hover:text-[#b8b8b8]',
                  ].join(' ')}
                  key={tab}
                  onClick={() => setActiveTab(tab)}
                  role="tab"
                  type="button"
                >
                  {label}
                </button>
              ))}
            </div>

            <div className="min-h-[210px] pt-[18px]" role="tabpanel">
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
              ) : turns.length ? (
                <div className="grid gap-4">
                  {turns.map((turn, index) => (
                    <article
                      className="border-l-2 border-[#d6ff50] pl-3"
                      key={`turn-${index + 1}`}
                    >
                      <div className="font-mono text-[9px] font-bold text-[#d6ff50]">
                        TURN {String(index + 1).padStart(2, '0')}
                      </div>
                      <p className="my-2 whitespace-pre-wrap text-[12px] leading-[1.6] text-[#f5f5ef]">
                        {turn.prompt}
                      </p>
                      <p className="m-0 whitespace-pre-wrap text-[11px] leading-[1.6] text-[#8f8f8f]">
                        {turn.aiResponse}
                      </p>

                      {turn.toolCalls.length > 0 && (
                        <div className="mt-2 flex flex-wrap gap-1.5 font-mono text-[9px] text-[#767676]">
                          {turn.toolCalls.map((toolCall, toolIndex) => (
                            <span
                              className="border border-[#3f3f3f] px-1.5 py-0.5"
                              key={`tool-${index}-${toolIndex}`}
                              title={toolCall.path ?? toolCall.tool}
                            >
                              {toolLabels[toolCall.tool] ?? toolCall.tool}
                              {toolCall.path
                                ? ` ${toolCall.path.split('/').pop()}`
                                : ''}
                            </span>
                          ))}
                        </div>
                      )}

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
              ) : (
                <div className="grid min-h-[160px] place-items-center text-center font-mono text-[11px] leading-[1.7] text-[#666]">
                  실행한 프롬프트가 없습니다.
                </div>
              )}
            </div>
          </section>

          <section className="flex min-h-0 flex-col overflow-hidden pt-2 max-[1080px]:overflow-visible max-[1080px]:pt-0 max-[700px]:pt-[22px]">
            <div className={labelClasses}>PROMPT / MAX 4,000</div>
            <div className="mt-2 flex shrink-0 flex-col border border-[#555] bg-[#131313] focus-within:border-[#d6ff50]">
              <textarea
                ref={promptTextareaRef}
                aria-keyshortcuts="Control+Enter Meta+Enter"
                className="workspace-scrollbar min-h-12 w-full resize-none overflow-y-auto border-0 bg-transparent px-3.5 py-3 text-[13px] leading-[1.6] text-[#f5f5ef] outline-0 [scrollbar-gutter:stable] disabled:cursor-not-allowed disabled:opacity-60"
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
                style={{ maxHeight: status ? '82px' : '112px' }}
                value={prompt}
              />
              <div className="flex min-h-9 shrink-0 items-center justify-end px-2 pb-2">
                <button
                  aria-label="프롬프트 실행"
                  className="grid size-7 cursor-pointer place-items-center rounded-full border border-[#d6ff50] bg-[#d6ff50] text-[#090909] transition-colors hover:bg-transparent hover:text-[#d6ff50] disabled:cursor-not-allowed disabled:opacity-45"
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
                {isSubmitting ? 'LOADING…' : 'GO TO FEEDBACK ↗'}
              </Button>
            </div>

          </section>
        </aside>
      </main>
    </div>
  );
}
