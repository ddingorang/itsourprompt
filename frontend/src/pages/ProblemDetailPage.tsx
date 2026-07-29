import { useEffect, useMemo, useRef, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import { useNavigate, useParams } from 'react-router-dom';

import { submitFeedback } from '../features/feedback/api';
import { getProblemDetail } from '../features/problem/api';
import type { ProblemDetail, RepositoryFile } from '../features/problem/types';
import { runProblem } from '../features/submission/api';
import { saveFeedbackResult, saveRunResult } from '../features/submission/storage';
import type {
  ChangedFile,
  ChangeType,
  RunProblemResponse,
} from '../features/submission/types';
import { ApiProblemError } from '../shared/api/apiClient';
import Button from '../shared/components/Button';
import Header from '../shared/components/Header';

type StatusType = 'normal' | 'error';

interface StatusMessage {
  message: string;
  type: StatusType;
}

interface PromptLog {
  id: number;
  prompt: string;
  response: string;
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
  'grid min-h-[calc(100vh_-_66px)] place-items-center bg-[#090909] p-10 ' +
  'font-mono text-xs leading-[1.7] text-[#a3a3a3]';

const changeColorClasses: Record<ChangeType, string> = {
  ADDED: 'text-[#d6ff50]',
  MODIFIED: 'text-white',
  DELETED: 'text-[#ff786b]',
};

function getErrorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiProblemError ? error.problem.detail : fallback;
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
  const [files, setFiles] = useState<RepositoryFile[]>([]);
  const [changedFiles, setChangedFiles] = useState<ChangedFile[]>([]);
  const [prompt, setPrompt] = useState('');
  const [selectedFile, setSelectedFile] = useState('');
  const [runResult, setRunResult] = useState<RunProblemResponse | null>(null);
  const [promptLogs, setPromptLogs] = useState<PromptLog[]>([]);
  const [activeTab, setActiveTab] = useState<DetailTab>('problem');
  const [expandedFolders, setExpandedFolders] = useState<Set<string>>(new Set());
  const [status, setStatus] = useState<StatusMessage | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRunning, setIsRunning] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const promptTextareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    let isMounted = true;

    getProblemDetail(problemId)
      .then((response) => {
        if (!isMounted) return;
        setProblem(response);
        setFiles(response.files);
        setSelectedFile(response.files[0]?.path ?? '');
        setExpandedFolders(
          new Set(getFolderPaths(createFileTree(response.files, []))),
        );
      })
      .catch((error: unknown) => {
        if (isMounted) {
          setStatus({
            type: 'error',
            message: getErrorMessage(error, '문제 상세를 불러오지 못했습니다.'),
          });
        }
      })
      .finally(() => {
        if (isMounted) setIsLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [problemId]);

  useEffect(() => {
    const textarea = promptTextareaRef.current;
    if (!textarea) return;

    textarea.style.height = 'auto';
    textarea.style.height = `${Math.min(textarea.scrollHeight, 96)}px`;
  }, [prompt]);

  const fileTree = useMemo(
    () => createFileTree(files, changedFiles),
    [files, changedFiles],
  );

  const selectedCode = useMemo(() => {
    if (!selectedFile) return '// 파일을 선택해주세요.';
    return findFile(files, selectedFile)?.content ?? '// 실행 결과에서 삭제된 파일입니다.';
  }, [files, selectedFile]);

  const showStatus = (message: string, type: StatusType = 'normal') => {
    setStatus({ message, type });
  };

  const handleRun = async () => {
    if (!problem) return;

    const trimmedPrompt = prompt.trim();
    if (!trimmedPrompt) {
      showStatus('프롬프트를 입력해주세요.', 'error');
      return;
    }

    if (trimmedPrompt.length > 4000) {
      showStatus('프롬프트는 4,000자 이하로 입력해주세요.', 'error');
      return;
    }

    setIsRunning(true);
    showStatus('프롬프트를 실행하고 있습니다. 완료될 때까지 잠시 기다려주세요.');

    try {
      const response = await runProblem(problem.id, { prompt: trimmedPrompt });
      setFiles(response.files);
      setChangedFiles(response.changedFiles);
      setRunResult(response);
      setExpandedFolders((folders) => {
        const nextFolders = new Set(folders);
        getFolderPaths(createFileTree(response.files, response.changedFiles)).forEach(
          (path) => nextFolders.add(path),
        );
        return nextFolders;
      });
      setPromptLogs((logs) => [
        ...logs,
        {
          id: logs.length + 1,
          prompt: trimmedPrompt,
          response: response.aiResponse,
        },
      ]);
      setActiveTab('logs');
      setPrompt('');
      setSelectedFile(response.files[0]?.path ?? '');
      saveRunResult({
        ...response,
        problemId: problem.id,
        problemTitle: problem.title,
        prompt: trimmedPrompt,
      });
      showStatus('실행이 완료되었습니다. 변경 파일과 AI 응답을 확인해주세요.');
    } catch (error: unknown) {
      showStatus(getErrorMessage(error, '실행에 실패했습니다. 다시 시도해주세요.'), 'error');
    } finally {
      setIsRunning(false);
    }
  };

  const handleSubmit = async () => {
    if (!problem || !runResult) return;

    const submittedPrompt = promptLogs
      .map((log, index) => `[${index + 1}차 프롬프트]\n${log.prompt}`)
      .join('\n\n');

    setIsSubmitting(true);
    showStatus('프롬프트 피드백을 생성하고 있습니다.');

    try {
      const response = await submitFeedback(problem.id, {
        prompt: submittedPrompt,
        aiResponse: runResult.aiResponse,
        changedFiles: runResult.changedFiles,
      });

      saveFeedbackResult({
        problemId: problem.id,
        problemTitle: problem.title,
        prompt: submittedPrompt,
        feedback: response.feedback,
      });

      navigate(`/feedback/${problem.id}`);
    } catch (error: unknown) {
      showStatus(
        getErrorMessage(error, '피드백 생성에 실패했습니다. 재실행 없이 다시 제출해주세요.'),
        'error',
      );
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

        <aside className="col-span-1 grid min-h-0 min-w-0 grid-rows-2 overflow-hidden px-6 py-[22px] max-[1080px]:col-span-full max-[1080px]:grid-rows-1 max-[1080px]:grid-cols-[minmax(0,0.8fr)_minmax(300px,1.2fr)] max-[1080px]:gap-7 max-[1080px]:overflow-visible max-[1080px]:border-t max-[1080px]:border-[#343434] max-[700px]:block max-[700px]:px-4 max-[700px]:pt-5 max-[700px]:pb-[30px]">
          <section className="workspace-scrollbar min-h-0 flex-1 overflow-y-auto overflow-x-hidden border-b border-[#343434] pb-[22px] max-[1080px]:overflow-visible max-[1080px]:border-b-0 max-[1080px]:pb-0">
            <div
              className="grid grid-cols-2 border border-[#3f3f3f]"
              role="tablist"
              aria-label="문제 상세 정보"
            >
              {([
                ['problem', 'PROBLEM'],
                ['logs', `PROMPT LOG ${promptLogs.length ? `(${promptLogs.length})` : ''}`],
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
              ) : promptLogs.length ? (
                <div className="grid gap-4">
                  {promptLogs.map((log) => (
                    <article className="border-l-2 border-[#d6ff50] pl-3" key={log.id}>
                      <div className="font-mono text-[9px] font-bold text-[#d6ff50]">
                        RUN {String(log.id).padStart(2, '0')}
                      </div>
                      <p className="my-2 whitespace-pre-wrap text-[12px] leading-[1.6] text-[#f5f5ef]">
                        {log.prompt}
                      </p>
                      <p className="m-0 whitespace-pre-wrap text-[11px] leading-[1.6] text-[#8f8f8f]">
                        {log.response}
                      </p>
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

          <section className="flex min-h-0 flex-col overflow-hidden pt-[22px] max-[1080px]:overflow-visible max-[1080px]:pt-0 max-[700px]:pt-[22px]">
            <div className={labelClasses}>PROMPT / MAX 4,000</div>
            <div className="mt-2.5 flex shrink-0 flex-col border border-[#555] bg-[#131313] focus-within:border-[#d6ff50]">
              <textarea
                ref={promptTextareaRef}
                className="workspace-scrollbar min-h-12 max-h-24 w-full resize-none overflow-y-auto border-0 bg-transparent px-3.5 py-3 text-[13px] leading-[1.6] text-[#f5f5ef] outline-0 disabled:cursor-not-allowed disabled:opacity-60"
                disabled={isRunning || isSubmitting}
                maxLength={4000}
                onChange={(event) => setPrompt(event.target.value)}
                placeholder="문제를 해결할 프롬프트를 입력하세요."
                rows={1}
                value={prompt}
              />
              <div className="flex min-h-10 shrink-0 items-center justify-end px-2 pb-2">
                <button
                  aria-label="프롬프트 실행"
                  className="grid size-9 cursor-pointer place-items-center rounded-full border border-[#d6ff50] bg-[#d6ff50] text-[#090909] transition-colors hover:bg-transparent hover:text-[#d6ff50] disabled:cursor-not-allowed disabled:opacity-45"
                  disabled={isRunning || isSubmitting || !prompt.trim()}
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
            <div className="mt-2 flex justify-end font-mono text-[9px] text-[#777]">
              <span>
                <b>{prompt.length.toLocaleString('ko-KR')}</b> / 4,000
              </span>
            </div>

            <div className="mt-auto pt-3">
              <Button
                disabled={isRunning || isSubmitting || !runResult}
                fullWidth
                onClick={handleSubmit}
              >
                {isSubmitting ? 'LOADING…' : 'GO TO FEEDBACK ↗'}
              </Button>

              {status && (
                <div
                  className={[
                    'mt-3.5 border border-[#484848] p-3 font-mono text-[10px] leading-[1.6] text-[#a3a3a3]',
                    status.type === 'error'
                      ? 'border-[#ff786b] text-[#ff786b]'
                      : '',
                  ].join(' ')}
                  role="status"
                >
                  {status.message}
                </div>
              )}
            </div>

          </section>
        </aside>
      </main>
    </div>
  );
}
