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
import { problemDetailPageStyles } from './ProblemDetailPage.style';

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
      <>
        <style>{problemDetailPageStyles}</style>
        <div className="page-state">문제 상세를 불러오는 중입니다…</div>
      </>
    );
  }

  if (!problem) {
    return (
      <>
        <style>{problemDetailPageStyles}</style>
        <div className="page-state error">
          <div>
            <p>{status?.message ?? '문제를 찾을 수 없습니다.'}</p>
            <Button to="/problems">BACK TO PROBLEMS ↗</Button>
          </div>
        </div>
      </>
    );
  }

  return (
    <>
      <style>{problemDetailPageStyles}</style>

      <header className="site-header">
        <Link className="logo" to="/" aria-label="홈으로 이동">
          prompt<i>.</i>practice
        </Link>
        <div className="header-title">
          {problem.title} / PROBLEM {String(problem.id).padStart(2, '0')}
        </div>
      </header>

      <main className="workspace">
        <aside className="column file-column">
          <div className="label">FILE EXPLORER</div>
          <h2 className="section-title">problem-{problem.id}</h2>

          <div className="file-tree">
            {treeItems.map((item) => (
              <button
                className={`tree-row${selectedFile === item.path ? ' active' : ''}${
                  item.deleted ? ' deleted' : ''
                }`}
                key={item.path}
                onClick={() => setSelectedFile(item.path)}
                style={{ paddingLeft: `${7 + Math.min(item.depth, 3) * 14}px` }}
                type="button"
              >
                <span className="path">{item.path.split('/').pop()}</span>
                <span className={`change ${item.changeType ?? ''}`}>
                  {item.changeType?.[0] ?? ''}
                </span>
              </button>
            ))}
          </div>

          <div className="legend">
            <span>A / ADDED</span>
            <span>M / MODIFIED</span>
            <span>D / DELETED</span>
          </div>
        </aside>

        <section className="column viewer-column">
          <div className="viewer-top">
            <div>
              <div className="label">
                {selectedFile.split('/').pop()?.toUpperCase() || 'FILE'} /{' '}
                {runResult ? 'RUN RESULT' : 'SKELETON'}
              </div>
            </div>
            <span className="read-only">READ ONLY</span>
          </div>

          <div className="code-shell">
            <div className="code-head">
              <span>{selectedFile}</span>
              <span>{runResult ? 'RESULT' : 'ORIGINAL'}</span>
            </div>
            <pre>
              <code>{selectedCode}</code>
            </pre>
          </div>
        </section>

        <aside className="column action-column">
          <section className="mission-card">
            <div className="label">CURRENT PROBLEM</div>
            <div className="spec-content">
              <ReactMarkdown>{problem.specMd}</ReactMarkdown>
            </div>
            <div className="chips">
              <span className="chip">READ ONLY</span>
              <span className="chip">STATELESS</span>
            </div>
          </section>

          <section className="prompt-block">
            <div className="label">PROMPT / MAX 4,000</div>
            <textarea
              disabled={isRunning || isSubmitting}
              maxLength={4000}
              onChange={(event) => setPrompt(event.target.value)}
              placeholder="문제를 해결할 프롬프트를 입력하세요."
              value={prompt}
            />
            <div className="prompt-meta">
              <span>
                <b>{prompt.length.toLocaleString('ko-KR')}</b> / 4,000
              </span>
            </div>

            <Button
              className="run-button"
              disabled={isRunning || isSubmitting}
              fullWidth
              onClick={handleRun}
            >
              {isRunning ? 'RUNNING…' : 'RUN PROMPT ↗'}
            </Button>

            {status && (
              <div className={`status${status.type === 'error' ? ' error' : ''}`} role="status">
                {status.message}
              </div>
            )}

            {runResult && (
              <div className="result-card">
                <div className="label">AI RESPONSE / RUN COMPLETE</div>
                <h3>변경 사항이 준비되었습니다.</h3>
                <p className="result-copy">
                  생성된 전체 파일과 변경 목록을 확인한 뒤 프롬프트 피드백을 요청할 수 있습니다.
                </p>
                <div className="ai-response">{runResult.aiResponse}</div>
                <div className="result-actions">
                  <Button disabled={isSubmitting} onClick={handleSubmit}>
                    {isSubmitting ? 'SUBMITTING…' : 'SUBMIT FOR FEEDBACK ↗'}
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
    </>
  );
}
