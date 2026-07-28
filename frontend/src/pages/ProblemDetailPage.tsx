import { useEffect, useMemo, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import { Link, useNavigate, useParams } from 'react-router-dom';

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

type StatusType = 'normal' | 'error';

interface StatusMessage {
  message: string;
  type: StatusType;
}

interface TreeItem {
  path: string;
  depth: number;
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
  return files.find((file) => file.path === path);
}

function createTreeItems(
  files: RepositoryFile[],
  changedFiles: ChangedFile[],
): TreeItem[] {
  const changes = new Map(changedFiles.map((file) => [file.path, file.changeType]));
  const paths = new Set(files.map((file) => file.path));

  changedFiles
    .filter((file) => file.changeType === 'DELETED')
    .forEach((file) => paths.add(file.path));

  return [...paths]
    .sort((a, b) => a.localeCompare(b))
    .map((path) => ({
      path,
      depth: Math.max(0, path.split('/').length - 1),
      changeType: changes.get(path),
      deleted: changes.get(path) === 'DELETED',
    }));
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
  const [status, setStatus] = useState<StatusMessage | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRunning, setIsRunning] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    let isMounted = true;

    getProblemDetail(problemId)
      .then((response) => {
        if (!isMounted) return;
        setProblem(response);
        setFiles(response.files);
        setSelectedFile(response.files[0]?.path ?? '');
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

  const treeItems = useMemo(
    () => createTreeItems(files, changedFiles),
    [files, changedFiles],
  );

  const selectedCode = useMemo(() => {
    if (!selectedFile) return '// 파일을 선택해주세요.';
    return findFile(files, selectedFile)?.content ?? '// 실행 결과에서 삭제된 파일입니다.';
  }, [files, selectedFile]);

  const showStatus = (message: string, type: StatusType = 'normal') => {
    setStatus({ message, type });
  };

  const resetToSkeleton = () => {
    if (!problem) return;
    setFiles(problem.files);
    setChangedFiles([]);
    setRunResult(null);
    setSelectedFile(problem.files[0]?.path ?? '');
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

    if (runResult) {
      const confirmed = window.confirm(
        '새로 실행하면 현재 결과가 사라지고 깨끗한 스켈레톤에서 다시 시작합니다. 계속할까요?',
      );
      if (!confirmed) return;
      resetToSkeleton();
    }

    setIsRunning(true);
    showStatus('프롬프트를 실행하고 있습니다. 완료될 때까지 잠시 기다려주세요.');

    try {
      const response = await runProblem(problem.id, { prompt: trimmedPrompt });
      setFiles(response.files);
      setChangedFiles(response.changedFiles);
      setRunResult(response);
      setSelectedFile(response.files[0]?.path ?? '');
      saveRunResult({
        ...response,
        problemId: problem.id,
        problemTitle: problem.title,
        prompt: trimmedPrompt,
      });
      showStatus('실행이 완료되었습니다. 변경 파일과 AI 응답을 확인해주세요.');
    } catch (error: unknown) {
      resetToSkeleton();
      showStatus(getErrorMessage(error, '실행에 실패했습니다. 다시 시도해주세요.'), 'error');
    } finally {
      setIsRunning(false);
    }
  };

  const handleSubmit = async () => {
    if (!problem || !runResult) return;

    setIsSubmitting(true);
    showStatus('프롬프트 피드백을 생성하고 있습니다.');

    try {
      const response = await submitFeedback(problem.id, {
        prompt: prompt.trim(),
        aiResponse: runResult.aiResponse,
        changedFiles: runResult.changedFiles,
      });

      saveFeedbackResult({
        problemId: problem.id,
        problemTitle: problem.title,
        prompt: prompt.trim(),
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
    <div className="min-h-screen min-w-80 bg-[#090909] text-[#f5f5ef] [font-family:Arial,'Noto_Sans_KR',sans-serif]">
      <header className="grid min-h-[66px] grid-cols-[auto_minmax(0,1fr)] items-center gap-6 border-b border-[#343434] bg-[#090909] px-6 font-mono text-[15px] max-[700px]:px-4">
        <Link
          className="text-xl leading-none font-black tracking-[-1.6px] [font-family:Arial,'Noto_Sans_KR',sans-serif]"
          to="/"
          aria-label="홈으로 이동"
        >
          prompt<i className="not-italic text-[#d6ff50]">.</i>practice
        </Link>
        <div className="overflow-hidden text-right text-ellipsis whitespace-nowrap text-[#a3a3a3] max-[700px]:hidden">
          {problem.title} / PROBLEM {String(problem.id).padStart(2, '0')}
        </div>
      </header>

      <main className="grid min-h-[calc(100vh_-_66px)] grid-cols-[230px_minmax(360px,1fr)_minmax(340px,390px)] max-[1080px]:grid-cols-[190px_minmax(0,1fr)] max-[700px]:block">
        <aside className="min-w-0 border-r border-[#343434] px-6 py-[22px] max-[700px]:border-r-0 max-[700px]:border-b max-[700px]:px-4 max-[700px]:py-[18px]">
          <div className={labelClasses}>FILE EXPLORER</div>
          <h2 className="mt-2.5 mb-[22px] text-[22px] font-bold tracking-[-0.04em]">
            problem-{problem.id}
          </h2>

          <div className="mt-[18px] grid select-none gap-[3px] font-mono text-xs leading-[1.5] text-[#a3a3a3]">
            {treeItems.map((item) => (
              <button
                className={[
                  'grid min-h-[34px] w-full cursor-pointer grid-cols-[minmax(0,1fr)_28px] items-center gap-2 border-0 bg-transparent px-[7px] text-left font-inherit text-inherit hover:text-[#f5f5ef]',
                  selectedFile === item.path
                    ? 'bg-[#d6ff50] text-[#090909] hover:text-[#090909]'
                    : '',
                  item.deleted ? 'opacity-60 line-through' : '',
                ]
                  .filter(Boolean)
                  .join(' ')}
                key={item.path}
                onClick={() => setSelectedFile(item.path)}
                style={{ paddingLeft: `${7 + Math.min(item.depth, 3) * 14}px` }}
                type="button"
              >
                <span className="overflow-hidden text-ellipsis whitespace-nowrap">
                  {item.path.split('/').pop()}
                </span>
                <span
                  className={[
                    'text-right font-black',
                    selectedFile === item.path
                      ? 'text-[#090909]'
                      : item.changeType
                        ? changeColorClasses[item.changeType]
                        : '',
                  ].join(' ')}
                >
                  {item.changeType?.[0] ?? ''}
                </span>
              </button>
            ))}
          </div>

          <div className="mt-7 grid gap-2 border-t border-[#343434] pt-4 font-mono text-[9px] text-[#767676]">
            <span>A / ADDED</span>
            <span>M / MODIFIED</span>
            <span>D / DELETED</span>
          </div>
        </aside>

        <section className="min-w-0 border-r border-[#343434] px-7 pt-[22px] pb-10 max-[1080px]:border-r-0 max-[700px]:border-b max-[700px]:px-4 max-[700px]:pt-5 max-[700px]:pb-[30px]">
          <div className="mb-5 flex items-start justify-between gap-[18px]">
            <div>
              <div className={labelClasses}>
                {selectedFile.split('/').pop()?.toUpperCase() || 'FILE'} /{' '}
                {runResult ? 'RUN RESULT' : 'SKELETON'}
              </div>
            </div>
            <span className="shrink-0 border border-[#494949] px-2 py-1.5 font-mono text-[9px] text-[#a3a3a3]">
              READ ONLY
            </span>
          </div>

          <div className="min-h-[360px] overflow-auto border border-[#292929] bg-[#202020]">
            <div className="flex justify-between gap-3 border-b border-[#333] px-3.5 py-[11px] font-mono text-[10px] text-[#a3a3a3]">
              <span>{selectedFile}</span>
              <span>{runResult ? 'RESULT' : 'ORIGINAL'}</span>
            </div>
            <pre className="m-0 min-w-max p-6 font-mono text-xs leading-[1.9] text-[#e3e3dd] [tab-size:2]">
              <code>{selectedCode}</code>
            </pre>
          </div>
        </section>

        <aside className="col-span-1 min-w-0 px-6 py-[22px] max-[1080px]:col-span-full max-[1080px]:grid max-[1080px]:grid-cols-[minmax(0,0.8fr)_minmax(300px,1.2fr)] max-[1080px]:gap-7 max-[1080px]:border-t max-[1080px]:border-[#343434] max-[700px]:block max-[700px]:px-4 max-[700px]:pt-5 max-[700px]:pb-[30px]">
          <section className="border-b border-[#343434] pb-[22px] max-[1080px]:border-b-0 max-[1080px]:pb-0">
            <div className={labelClasses}>CURRENT PROBLEM</div>
            <div className="m-0 whitespace-pre-wrap text-[13px] leading-[1.7] text-[#a3a3a3] [word-break:keep-all]">
              <ReactMarkdown
                components={{
                  h1: ({ children }) => (
                    <h1 className="my-3 mt-2.5 text-[30px] leading-[1.05] font-bold tracking-[-0.045em] text-[#f5f5ef]">
                      {children}
                    </h1>
                  ),
                  h2: ({ children }) => (
                    <h2 className="font-bold text-[#f5f5ef]">{children}</h2>
                  ),
                  h3: ({ children }) => (
                    <h3 className="font-bold text-[#f5f5ef]">{children}</h3>
                  ),
                  h4: ({ children }) => (
                    <h4 className="font-bold text-[#f5f5ef]">{children}</h4>
                  ),
                  h5: ({ children }) => (
                    <h5 className="font-bold text-[#f5f5ef]">{children}</h5>
                  ),
                  h6: ({ children }) => (
                    <h6 className="font-bold text-[#f5f5ef]">{children}</h6>
                  ),
                }}
              >
                {problem.specMd}
              </ReactMarkdown>
            </div>
            <div className="mt-[15px]">
              <span className="mr-1 mb-[5px] inline-block border border-[#4c4c4c] px-[7px] py-[5px] font-mono text-[9px] text-[#bdbdbd]">
                READ ONLY
              </span>
              <span className="mr-1 mb-[5px] inline-block border border-[#4c4c4c] px-[7px] py-[5px] font-mono text-[9px] text-[#bdbdbd]">
                STATELESS
              </span>
            </div>
          </section>

          <section className="pt-[22px] max-[1080px]:pt-0 max-[700px]:pt-[22px]">
            <div className={labelClasses}>PROMPT / MAX 4,000</div>
            <textarea
              className="mt-2.5 min-h-[150px] w-full resize-y border border-[#555] bg-[#131313] p-3.5 text-[13px] leading-[1.6] text-[#f5f5ef] outline-0 focus:border-[#d6ff50] disabled:cursor-not-allowed disabled:opacity-60"
              disabled={isRunning || isSubmitting}
              maxLength={4000}
              onChange={(event) => setPrompt(event.target.value)}
              placeholder="문제를 해결할 프롬프트를 입력하세요."
              value={prompt}
            />
            <div className="mt-2 flex justify-end font-mono text-[9px] text-[#777]">
              <span>
                <b>{prompt.length.toLocaleString('ko-KR')}</b> / 4,000
              </span>
            </div>

            <Button
              className="mt-3"
              disabled={isRunning || isSubmitting}
              fullWidth
              onClick={handleRun}
            >
              {isRunning ? 'RUNNING…' : 'RUN PROMPT ↗'}
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

            {runResult && (
              <div className="mt-[18px] border border-[#d6ff50] bg-[#121212] p-[18px]">
                <div className={labelClasses}>AI RESPONSE / RUN COMPLETE</div>
                <h3 className="my-2.5 text-[15px] font-bold">
                  변경 사항이 준비되었습니다.
                </h3>
                <p className="m-0 whitespace-pre-wrap text-[13px] leading-[1.7] text-[#a3a3a3] [word-break:keep-all]">
                  생성된 전체 파일과 변경 목록을 확인한 뒤 프롬프트 피드백을 요청할 수 있습니다.
                </p>
                <div className="mt-3.5 max-h-[180px] overflow-auto whitespace-pre-wrap border border-[#333] bg-[#0e0e0e] p-3 font-mono text-[11px] leading-[1.7] text-[#d8d8d2]">
                  {runResult.aiResponse}
                </div>
                <div className="mt-4 grid grid-cols-[1fr_auto] gap-2 max-[700px]:grid-cols-1">
                  <Button disabled={isSubmitting} onClick={handleSubmit}>
                    {isSubmitting ? 'SUBMITTING…' : 'SUBMIT & FEEDBACK ↗'}
                  </Button>
                  <Button
                    disabled={isRunning || isSubmitting}
                    onClick={handleRun}
                    variant="secondary"
                  >
                    RE-RUN
                  </Button>
                </div>
              </div>
            )}
          </section>
        </aside>
      </main>
    </div>
  );
}
