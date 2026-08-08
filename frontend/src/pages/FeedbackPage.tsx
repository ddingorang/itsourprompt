import {
  useEffect,
  useRef,
  useState,
  type KeyboardEvent,
  type ReactNode,
} from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';

import { getAttempt, getAttemptFeedback } from '../features/attempt/api';
import type {
  Attempt,
  AttemptFeedback,
  CarryLine,
} from '../features/attempt/types';
import { useAuth } from '../features/auth/AuthContext';
import PromptFeedback from '../features/feedback/PromptFeedback';
import { getProblemDetail } from '../features/problem/api';
import { useTheme } from '../features/theme/ThemeContext';
import CodeViewer from '../features/workspace/CodeViewer';
import { nextTabIndex } from '../shared/a11y/tabKeyboard';
import { ApiError, API_ERROR_CODES, isAbortError } from '../shared/api/apiClient';
import Button from '../shared/components/Button';
import Footer from '../shared/components/Footer';
import Header from '../shared/components/Header';

/**
 * 피드백을 열지 못했을 때 화면에 남길 안내. 아직 제출하지 않은 어템프트처럼
 * 오류가 아닌 경우도 있어, 색과 돌아갈 곳을 함께 담는다.
 */
interface LoadNotice {
  message: string;
  actionLabel: string;
  /** 돌아갈 경로. null이면 브라우저 히스토리로 한 걸음 되돌아간다. */
  actionTo: string | null;
  isError: boolean;
}

/**
 * 화면이 쓰는 pattern 피드백 한 벌. 응답에서는 자리마다 null일 수 있지만 여기까지 온 것은
 * 총평과 모든 턴이 다 찬 것뿐이라, 렌더 자리에서 다시 null을 묻지 않는다.
 */
interface PatternFeedback {
  overallMd: string;
  /** 턴 번호 → 그 턴의 pattern 본문. 정규화가 모든 턴을 채웠으므로 빠진 번호가 없다. */
  turnMd: Record<number, string>;
}

/**
 * pattern은 전부 있거나 전부 없다 — 제출이 두 스타일을 함께 저장하거나 아예 실패하기 때문이다.
 * 그 계약을 화면 밖 경계에서 한 번 확인하고, 어긋나면 pattern을 통째로 없는 것으로 본다.
 *
 * <p>반쪽을 그리면 사용자는 설명 없는 빈 칸을 본다. 그 칸이 pattern 도입 전 제출이라 비어 있는
 * 것인지 생성이 반쯤 실패한 것인지 화면으로는 구분할 수 없어, 없는 쪽으로 모아 둔다.
 */
function toPatternFeedback(
  feedback: AttemptFeedback | null,
): PatternFeedback | null {
  const overallMd = feedback?.patternOverallMd ?? null;

  if (feedback === null || overallMd === null) {
    return null;
  }

  const turnMd: Record<number, string> = {};

  for (const turn of feedback.turns) {
    if (turn.patternMd === null) {
      // 계약이 깨진 자리다. 화면은 조용히 접지만 원인은 남긴다.
      console.warn(`턴 ${turn.turn}에 pattern 피드백이 없어 pattern 자리를 숨깁니다.`);
      return null;
    }

    turnMd[turn.turn] = turn.patternMd;
  }

  return { overallMd, turnMd };
}

/**
 * 세션 단위 칸 하나. 테두리와 제목 줄을 여기서만 그린다 — 이 화면에 세션 칸이 셋이라
 * 각자 그리면 하나를 손볼 때 나머지가 조용히 어긋난다.
 *
 * 바깥 여백만은 부르는 쪽 몫이다. 두 총평은 격자 칸에 들어가 격자가 간격을 주고,
 * 규칙 한 줄은 그 아래 홀로 서서 자기 여백이 필요하다.
 */
function SessionPanel({
  bodyClassName = '',
  children,
  className = '',
  subtitle,
  title,
}: {
  bodyClassName?: string;
  children: ReactNode;
  className?: string;
  subtitle?: string;
  title: string;
}) {
  return (
    <section className={`min-w-0 border border-[var(--feedback-border)] bg-transparent ${className}`}>
      {/* min-h만 두면 줄이 위로 붙는다 — content-center로 여백을 위아래 같게 나눈다.
          머리줄은 릴레이 로비 섹션 머리줄과 같은 조합(surface 배경 + label 글씨)이다. */}
      <div
        className="flex min-h-[58px] flex-wrap content-center items-baseline gap-x-3 gap-y-1 border-b border-[var(--feedback-border)] bg-[var(--feedback-surface)] px-6 py-2 max-[760px]:px-5"
      >
        <h2 className="m-0 text-xl leading-[1.4] font-bold text-[var(--feedback-label)]">
          {title}
        </h2>
        {subtitle !== undefined && (
          <p className="m-0 text-[13px] leading-[1.6] text-[var(--feedback-muted)]">
            {subtitle}
          </p>
        )}
      </div>
      <div className={`px-6 pb-7 max-[760px]:px-5 max-[760px]:pb-5 ${bodyClassName}`}>
        {children}
      </div>
    </section>
  );
}

/** 세션 전체를 다루는 총평 한 벌. 렌즈마다 하나씩, 격자 칸에 나란히 선다. */
function OverallPanel({
  description,
  markdown,
  title,
}: {
  /** 이 렌즈가 무엇을 보는지. 제목 옆에 붙어 총평과 턴별 피드백 양쪽을 함께 설명한다. */
  description: string;
  markdown: string;
  title: string;
}) {
  return (
    <SessionPanel
      bodyClassName="[&>div>h2:first-child]:border-t-0 [&>div>h2:first-child]:pt-0"
      subtitle={description}
      title={title}
    >
      <PromptFeedback feedback={markdown} />
    </SessionPanel>
  );
}

/**
 * 다음 문제로 가져갈 규칙 한 줄.
 *
 * 두 총평과 나란히 서지만 일부러 다르게 생겼다. 총평은 사용자에게 하는 조언이고 이 줄은
 * 사용자의 AI에게 할 지시라, 읽고 넘길 문단이 아니라 **복사해 갈 물건**으로 그린다.
 * 그래서 규칙은 붙여넣을 그대로의 한 줄로 두고 근거는 그 아래 한 문장으로 줄인다.
 *
 * 파일 이름(AGENTS.md)은 여기 라벨에만 쓴다 — 규칙 문장 자체는 도구 중립이라 어느 지시 파일에
 * 붙여넣어도 그대로 쓴다.
 */
function CarryLinePanel({ carry }: { carry: CarryLine }) {
  // 눌린 횟수를 센다. 불리언으로 두면 2초 안에 다시 눌러도 값이 그대로라 타이머가
  // 새로 걸리지 않고, 첫 타이머가 만료되며 방금 누른 표시를 지운다.
  const [copyCount, setCopyCount] = useState(0);

  useEffect(() => {
    if (copyCount === 0) return;

    const timer = window.setTimeout(() => setCopyCount(0), 2000);

    return () => window.clearTimeout(timer);
  }, [copyCount]);

  const copyRule = async () => {
    try {
      await navigator.clipboard.writeText(carry.rule);
      setCopyCount((count) => count + 1);
    } catch (error: unknown) {
      // 클립보드를 막아 둔 브라우저다. 규칙 줄은 그대로 보이므로 손으로 골라 복사하면 된다.
      console.warn('클립보드에 규칙 줄을 넣지 못했습니다.', error);
      setCopyCount(0);
    }
  };

  return (
    <SessionPanel
      className="mt-4"
      subtitle="AGENTS.md·CLAUDE.md 같은 상시 지시 파일에 붙여넣으세요"
      title="AI에게 전달할 규칙 한 줄"
    >
      <div className="mt-6 flex items-center gap-3 border border-[var(--feedback-border)] bg-[var(--feedback-prompt-bg)] p-[18px] max-[760px]:flex-col max-[760px]:items-stretch">
        {/*
          복사해 갈 물건이지만 모노로 두지 않는다 — 한글 문장이 모노 폭에 걸리면 낱자마다
          벌어져 읽기 어렵다. 테두리와 복사 버튼만으로도 "가져갈 것"으로 읽힌다.

          Markdown으로 렌더하지 않는 것도 일부러다. 이 문자열은 사용자가 자기 설정 파일에
          그대로 붙여넣으므로 화면에 보이는 것과 클립보드에 담기는 것이 같아야 한다.
        */}
        <p className="m-0 min-w-0 flex-1 text-[15px] leading-[1.9] text-[var(--feedback-text)] [word-break:keep-all]">
          {carry.rule}
        </p>
        <Button
          className="feedback-page-secondary-action shrink-0"
          onClick={() => void copyRule()}
          variant="secondary"
        >
          <span className="text-[14px]">
            {copyCount > 0 ? '복사했어요' : '복사하기'}
          </span>
        </Button>
      </div>

      {/* 복사 결과는 버튼 문구만 바뀌므로 화면을 안 보는 사용자에게도 읽히게 한다. */}
      <p
        aria-live="polite"
        className="m-0 mt-3.5 text-[13px] leading-[1.7] text-[var(--feedback-muted)] [word-break:keep-all]"
      >
        {copyCount > 0 ? '규칙 한 줄을 복사했어요. ' : ''}
        {carry.reason}
      </p>
    </SessionPanel>
  );
}

/**
 * 제출된 어템프트의 프롬프트 피드백 화면.
 *
 * 주소(/attempts/{id}/feedback)에 담긴 ID만 보고 서버에서
 * 피드백(GET /api/attempts/{id}/feedback)을 가져온다 — 브라우저 저장소에 기대지
 * 않으므로 새로고침해도 남고, 링크를 그대로 열어도 동작한다.
 * 프롬프트 원문은 피드백 응답에 없으므로 어템프트도 함께 조회해 짝지어 보여준다.
 */
export default function FeedbackPage() {
  const { colorMode } = useTheme();
  const { attemptId: attemptIdParam } = useParams();
  const attemptId = Number(attemptIdParam);
  const location = useLocation();
  const navigate = useNavigate();
  const { refresh } = useAuth();

  const [attempt, setAttempt] = useState<Attempt | null>(null);
  /** 어떤 문제의 피드백인지. 어템프트 응답에는 problemId만 있어 따로 읽어 온다. */
  const [problemTitle, setProblemTitle] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<AttemptFeedback | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [notice, setNotice] = useState<LoadNotice | null>(null);
  const [selectedTurn, setSelectedTurn] = useState(1);
  const turnNavRef = useRef<HTMLDivElement>(null);
  // 방향키가 옮긴 선택을 눈이 따라가려면 포커스도 같이 옮겨야 한다.
  const turnTabRefs = useRef<
    Partial<Record<number, HTMLButtonElement | null>>
  >({});

  useEffect(() => {
    if (!Number.isInteger(attemptId) || attemptId <= 0) {
      setNotice({
        message: '잘못된 주소입니다.',
        actionLabel: '이전 페이지로 돌아가기',
        actionTo: null,
        isError: true,
      });
      setIsLoading(false);
      return;
    }

    const controller = new AbortController();
    const { signal } = controller;

    void (async () => {
      try {
        const [loadedAttempt, loadedFeedback] = await Promise.all([
          getAttempt(attemptId, signal),
          getAttemptFeedback(attemptId, signal),
        ]);

        setAttempt(loadedAttempt);
        setFeedback(loadedFeedback);
        setSelectedTurn(loadedFeedback.turns[0]?.turn ?? 1);
      } catch (error: unknown) {
        if (isAbortError(error)) return;

        if (
          error instanceof ApiError &&
          error.code === API_ERROR_CODES.unauthenticated
        ) {
          await refresh();
          if (signal.aborted) return;

          navigate('/login', {
            replace: true,
            state: { from: location.pathname },
          });
          return;
        }

        // 제출 전이면 피드백이 아직 없다 — 오류가 아니라 작업장으로 돌아가라는 안내다.
        if (
          error instanceof ApiError &&
          error.code === API_ERROR_CODES.feedbackNotFound
        ) {
          setNotice({
            message: '아직 제출하지 않았습니다.',
            actionLabel: '작업장으로 돌아가기 ↗',
            actionTo: `/attempts/${attemptId}`,
            isError: false,
          });
          return;
        }

        setNotice({
          message:
            error instanceof ApiError
              ? error.message
              : '피드백을 불러오지 못했습니다.',
          actionLabel: '이전 페이지로 돌아가기',
          actionTo: null,
          isError: true,
        });
      } finally {
        // 뒤늦게 끊긴 요청이 새 로드의 로딩 표시를 끄지 않도록 한다.
        if (!signal.aborted) setIsLoading(false);
      }
    })();

    return () => {
      controller.abort();
    };
  }, [attemptId, location.pathname, navigate, refresh]);

  // 문제 이름은 어템프트를 읽은 뒤에야 어느 문제인지 알 수 있어 두 번째 요청으로 받는다.
  // 실패해도 이름만 비우고 피드백 본문은 그대로 보여 준다.
  useEffect(() => {
    const problemId = attempt?.problemId;
    if (problemId === undefined) return;

    const controller = new AbortController();

    void (async () => {
      try {
        const problem = await getProblemDetail(problemId, controller.signal);
        setProblemTitle(problem.title);
      } catch {
        // 제목은 부가 정보라 화면을 오류로 덮지 않는다.
      }
    })();

    return () => {
      controller.abort();
    };
  }, [attempt?.problemId]);

  const turnSections = (feedback?.turns ?? []).map((turnFeedback) => ({
    ...turnFeedback,
    prompt: attempt?.turns[turnFeedback.turn - 1]?.prompt ?? '',
    changedFiles: attempt?.turns[turnFeedback.turn - 1]?.changedFiles ?? [],
  }));
  const selectedSection =
    turnSections.find((section) => section.turn === selectedTurn) ??
    turnSections[0];
  // 판정은 여기 한 번뿐이다. 아래로는 pattern이 있거나(모든 자리가 찼거나) 없거나 둘뿐이다.
  const pattern = toPatternFeedback(feedback);

  const scrollTurnNav = (direction: -1 | 1) => {
    turnNavRef.current?.scrollBy({
      left: direction * turnNavRef.current.clientWidth * 0.7,
      behavior: 'smooth',
    });
  };

  const handleTurnTabKeyDown = (
    event: KeyboardEvent<HTMLButtonElement>,
    currentTurn: number,
  ) => {
    const currentIndex = turnSections.findIndex(
      (section) => section.turn === currentTurn,
    );
    const targetIndex = nextTabIndex(
      event.key,
      currentIndex,
      turnSections.length,
    );

    if (targetIndex === null) return;

    event.preventDefault();
    const nextTurn = turnSections[targetIndex].turn;
    setSelectedTurn(nextTurn);
    // 가로로 스크롤되는 탭 줄이라, 포커스가 옮겨 가면 브라우저가 보이는 자리까지 끌어온다.
    turnTabRefs.current[nextTurn]?.focus();
  };

  return (
    <div
      className="feedback-page flex min-h-screen min-w-80 flex-col overflow-x-clip bg-[var(--feedback-bg)] text-[var(--feedback-text)] [font-family:Arial,'Noto_Sans_KR',sans-serif]"
      data-color-mode={colorMode}
    >
      <Header mobileBreakpoint="760" />

      <main className="mx-auto w-[calc(100%_-_10vw)] max-w-[1840px] flex-1 pt-[clamp(32px,5vw,56px)] pb-20 max-[760px]:w-[min(calc(100%_-_32px),680px)] max-[760px]:pt-8">
        {/*
          이 화면은 랭킹에서 남의 기록으로도 열린다. 주인이 어디에도 없으면 링크를
          받아 바로 들어온 사람은 누구 기록인지 알 수 없다 — 제목 배너 오른쪽에
          함께 둔다. 좁은 화면에서는 제목 아래로 내려온다.
        */}
        {/* 오른쪽 정보 묶음이 제목보다 높아 items-end로는 제목이 아래로 붙는다 —
            위아래 여백이 같아 보이도록 가운데로 맞춘다. */}
        <section className="flex flex-wrap items-center justify-between gap-x-6 gap-y-3 bg-[var(--feedback-acid)] px-[22px] py-5 text-[#090909]">
          <h1 className="m-0 text-[clamp(34px,6vw,62px)] leading-[0.82] font-bold tracking-[-0.04em]">
            프롬프트 피드백
          </h1>
          {/* 묶음 자체는 배너 오른쪽에 두되, 안쪽 세 줄은 왼쪽 끝을 맞춘다 —
              줄마다 오른쪽으로 붙이면 문제 이름 길이에 따라 시작점이 흔들린다. */}
          {attempt && (
            <div
              aria-label="이 기록의 주인"
              className="grid justify-items-start gap-1"
            >
              {/* 두 줄이 같은 결로 읽히도록 라벨만 굵게, 값은 보통 굵기로 맞춘다. */}
              {problemTitle && (
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                  <span className="text-[15px] font-bold tracking-[-0.01em]">
                    문제
                  </span>
                  <span className="text-[15px] tracking-[-0.02em]">
                    {problemTitle}
                  </span>
                </div>
              )}
              <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                <span className="text-[15px] font-bold tracking-[-0.01em]">
                  작성자
                </span>
                <span className="text-[15px] tracking-[-0.02em]">
                  {attempt.ownerLabel ?? '(이름을 불러오지 못했습니다.)'}
                </span>
                {attempt.mine && (
                  <span className="border border-[#090909] px-1.5 py-0.5 font-mono text-[10px] leading-none font-bold tracking-[0.08em]">
                    YOU
                  </span>
                )}
              </div>
              <span className="text-[11px] leading-[1.5] tracking-[-0.01em]">
                제출된 기록은 누구나 볼 수 있습니다
              </span>
            </div>
          )}
        </section>

        {isLoading && (
          <section className="border-b border-[var(--feedback-border)] py-12 font-mono text-xs leading-[1.7] text-[var(--feedback-muted)]">
            피드백을 불러오는 중입니다...
          </section>
        )}

        {!isLoading && notice && (
          <section
            className={[
              'mt-4 border p-8 text-sm leading-[1.7]',
              notice.isError
                ? 'border-[#ff786b] text-[#ff786b]'
                : 'border-[var(--feedback-border)] text-[var(--feedback-muted)]',
            ].join(' ')}
          >
            <div>{notice.message}</div>
            {notice.actionTo !== null ? (
              <Button
                className="feedback-page-primary-action mt-5"
                to={notice.actionTo}
              >
                {notice.actionLabel}
              </Button>
            ) : (
              <Button
                className="feedback-page-primary-action mt-5"
                onClick={() => {
                  // 링크로 바로 열려 히스토리에 이전 페이지가 없으면(key가 초기값
                  // 'default') 뒤로 갈 곳이 없다 — 문제 목록으로 보낸다.
                  if (location.key !== 'default') navigate(-1);
                  else navigate('/problems');
                }}
              >
                {notice.actionLabel}
              </Button>
            )}
          </section>
        )}

        {!isLoading && !notice && feedback && (
          <>
            {/* 두 총평은 같은 세션을 다른 각도에서 본 것이라 나란히 두고 견주게 한다.
                pattern이 없는 옛 제출은 총평이 하나뿐이라 그때는 한 칸을 다 쓴다. */}
            <div
              className={`mt-4 grid gap-4 ${
                pattern ? 'grid-cols-2 max-[760px]:grid-cols-1' : 'grid-cols-1'
              }`}
            >
              <OverallPanel
                description="프롬프트에서 무엇을 전달했고, 무엇이 부족한지"
                markdown={feedback.overallMd}
                title="프롬프트 총평"
              />

              {pattern && (
                <OverallPanel
                  description="이 턴에 드러난 작업 패턴과, 다음에 써 볼 기법"
                  markdown={pattern.overallMd}
                  title="작업 패턴 총평"
                />
              )}
            </div>

            {/*
              세션 단위 산출물이라 두 총평 곁에 둔다. 턴 패널 아래로 내리면 스크롤 끝에 묻히는데,
              이 줄은 읽고 끝나는 것이 아니라 가지고 나가야 하는 것이다.
            */}
            {feedback.carry && <CarryLinePanel carry={feedback.carry} />}

            {selectedSection && (
              <section
                className="mt-4 border border-[var(--feedback-border)] bg-[var(--feedback-bg)]"
                aria-label="턴별 피드백"
              >
                {/* 겹쳐 놓인 제목·좌우 화살표가 이 상자를 기준으로 배치되므로 relative는
                    남겨 둔다 — sticky만 걷어내 스크롤에 따라 함께 올라가게 한다. */}
                <div className="relative border-b border-[var(--feedback-border)] bg-[var(--feedback-surface)]">
                  {/* 랭킹 상단바의 「문제 선택」 라벨과 같은 조합 — surface 배경 + label 글씨. */}
                  <span className="absolute top-0 bottom-0 left-0 z-20 grid w-[220px] place-items-center border-r border-[var(--feedback-border)] bg-[var(--feedback-surface)] text-xl leading-[1.4] font-bold text-[var(--feedback-label)] max-[760px]:hidden">
                    턴별 피드백
                  </span>
                  <button
                    aria-label="이전 턴 보기"
                    className="absolute top-0 bottom-0 left-[220px] z-20 w-10 cursor-pointer border-0 border-r border-[var(--feedback-border)] bg-[var(--feedback-surface)] font-mono text-2xl font-bold text-[var(--feedback-label)] hover:bg-[var(--feedback-surface-hover)] focus-visible:outline-2 focus-visible:outline-[var(--feedback-acid)] focus-visible:outline-offset-[-3px] max-[760px]:left-0"
                    onClick={() => scrollTurnNav(-1)}
                    type="button"
                  >
                    ‹
                  </button>
                  <div
                    aria-label="턴 선택"
                    className="turn-tab-scrollbar flex items-stretch overflow-x-auto pr-12 pl-[260px] scroll-smooth max-[760px]:px-[42px]"
                    ref={turnNavRef}
                    role="tablist"
                  >
                    {turnSections.map((section) => {
                      const isSelected = section.turn === selectedSection.turn;

                      return (
                        <button
                          aria-controls="feedback-turn-panel"
                          aria-selected={isSelected}
                          className={`relative min-h-[58px] min-w-[130px] shrink-0 cursor-pointer border-0 bg-transparent px-[22px] text-sm font-bold hover:text-[var(--feedback-text)] focus-visible:outline-2 focus-visible:outline-[var(--feedback-acid)] focus-visible:outline-offset-[-4px] after:absolute after:right-3.5 after:-bottom-px after:left-3.5 after:z-10 after:h-[3px] ${
                            isSelected
                              ? 'text-[var(--feedback-text)] after:bg-[var(--feedback-text)]'
                              : 'text-[var(--feedback-muted)] after:bg-transparent'
                          } max-[760px]:min-w-[110px] max-[760px]:px-3.5`}
                          id={`feedback-turn-tab-${section.turn}`}
                          key={section.turn}
                          onClick={() => setSelectedTurn(section.turn)}
                          onKeyDown={(event) =>
                            handleTurnTabKeyDown(event, section.turn)
                          }
                          ref={(node) => {
                            turnTabRefs.current[section.turn] = node;
                          }}
                          role="tab"
                          tabIndex={isSelected ? 0 : -1}
                          type="button"
                        >
                          턴 {section.turn}
                        </button>
                      );
                    })}
                  </div>
                  <button
                    aria-label="다음 턴 보기"
                    className={[
                      'absolute top-0 right-0 bottom-0 z-20 w-10 border-0 border-l border-[var(--feedback-border)] bg-[var(--feedback-surface)] font-mono text-2xl font-bold focus-visible:outline-2 focus-visible:outline-[var(--feedback-acid)] focus-visible:outline-offset-[-3px]',
                      turnSections.length >= 8
                        ? 'cursor-pointer text-[var(--feedback-label)] hover:bg-[var(--feedback-surface-hover)]'
                        : 'cursor-not-allowed text-[var(--feedback-subtle)]',
                    ].join(' ')}
                    disabled={turnSections.length < 8}
                    onClick={() => scrollTurnNav(1)}
                    type="button"
                  >
                    ›
                  </button>
                </div>

                <div
                  aria-labelledby={`feedback-turn-tab-${selectedSection.turn}`}
                  className="grid grid-cols-[minmax(0,1fr)_minmax(360px,1fr)] max-[760px]:grid-cols-1"
                  id="feedback-turn-panel"
                  role="tabpanel"
                  tabIndex={0}
                >
                  <article className="min-w-0 p-[22px]">
                    <h2 className="m-0 text-lg leading-[1.4] font-bold text-[var(--feedback-label)]">
                      생성된 코드
                    </h2>
                    {selectedSection.changedFiles.length > 0 ? (
                      <div className="workspace-scrollbar mt-[18px] -mr-[22px] grid max-h-[520px] gap-3 overflow-y-auto pr-[22px] max-[760px]:mr-0 max-[760px]:max-h-none max-[760px]:overflow-y-visible max-[760px]:pr-0">
                        {selectedSection.changedFiles.map((file) => (
                          <div
                            className="overflow-hidden border border-[var(--feedback-border)] bg-[var(--feedback-code-bg)]"
                            key={file.path}
                          >
                            <div className="border-b border-[var(--feedback-border)] px-4 py-3 font-mono text-xs font-bold text-[var(--feedback-muted)]">
                              {file.path}
                            </div>
                            <div className="workspace-scrollbar min-h-[280px] max-w-full overflow-x-auto max-[760px]:min-h-[220px]">
                              <CodeViewer
                                code={file.content ?? '// 이 턴에서 삭제된 파일입니다.'}
                                path={file.path}
                              />
                            </div>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <div className="mt-[18px] grid min-h-[280px] place-items-center border border-[var(--feedback-border)] bg-[var(--feedback-code-bg)] p-[18px] text-xs text-[var(--feedback-subtle)]">
                        바뀐 파일이 없습니다
                      </div>
                    )}
                  </article>

                  <article className="min-w-0 border-l border-[var(--feedback-border)] p-[22px] max-[760px]:border-t max-[760px]:border-l-0">
                    <h2 className="m-0 text-lg leading-[1.4] font-bold text-[var(--feedback-label)]">
                      작성한 프롬프트
                    </h2>
                    <p className="workspace-scrollbar mt-[18px] max-h-[520px] min-h-[110px] overflow-y-auto whitespace-pre-wrap border border-[var(--feedback-border)] bg-[var(--feedback-prompt-bg)] p-[18px] font-mono text-[15px] leading-[1.9] text-[var(--feedback-code-text)] [word-break:keep-all] max-[760px]:max-h-none max-[760px]:min-h-40 max-[760px]:overflow-y-visible">
                      {selectedSection.prompt || '(프롬프트 원문을 불러오지 못했습니다.)'}
                    </p>
                  </article>

                  <article
                    className={`min-w-0 border-t border-[var(--feedback-border)] p-[22px] ${pattern ? '' : 'col-span-full'}`}
                  >
                    <h2 className="m-0 text-lg leading-[1.4] font-bold text-[var(--feedback-label)]">
                      프롬프트 진단
                    </h2>
                    {/* 본문 길이에 따라 페이지가 끝없이 늘어나던 자리다 — 옆의 생성된
                        코드와 같은 높이로 묶고 넘치는 만큼은 안에서 스크롤시킨다.
                        음수 마진으로 칸의 오른쪽 패딩을 걷어내 스크롤바를 테두리에 붙이고,
                        같은 크기의 패딩을 안쪽에 다시 줘 본문은 스크롤바와 떨어뜨린다. */}
                    <div className="workspace-scrollbar mt-[18px] -mr-[22px] max-h-[520px] overflow-y-auto pr-[22px] max-[760px]:mr-0 max-[760px]:max-h-none max-[760px]:overflow-y-visible max-[760px]:pr-0">
                      <PromptFeedback feedback={selectedSection.feedbackMd} />
                    </div>
                  </article>

                  {pattern && (
                    <article className="min-w-0 border-t border-l border-[var(--feedback-border)] p-[22px] max-[760px]:border-l-0">
                      <h2 className="m-0 text-lg leading-[1.4] font-bold text-[var(--feedback-label)]">
                        작업 패턴
                      </h2>
                      <div className="workspace-scrollbar mt-[18px] -mr-[22px] max-h-[520px] overflow-y-auto pr-[22px] max-[760px]:mr-0 max-[760px]:max-h-none max-[760px]:overflow-y-visible max-[760px]:pr-0">
                        <PromptFeedback feedback={pattern.turnMd[selectedSection.turn]} />
                      </div>
                    </article>
                  )}
                </div>
              </section>
            )}

            <div className="mt-5 flex justify-end gap-3 max-[760px]:flex-col max-[760px]:justify-stretch">
              {attempt && (
                <Button
                  className="feedback-page-secondary-action max-[760px]:w-full"
                  to={`/ranking?problem=${attempt.problemId}`}
                  variant="secondary"
                >
                  <span className="text-[14px]">이 문제 랭킹 보기 ↗</span>
                </Button>
              )}
              {attempt && (
                <Button
                  className="max-[760px]:w-full"
                  to={`/problems/${attempt.problemId}`}
                >
                  {/*
                    목적지는 같은 작업장이지만 남의 기록에서는 "돌아갈" 곳이 아니다 —
                    거기서 열리는 것은 그 사람의 풀이가 아니라 내 새 어템프트다.
                  */}
                  <span className="text-[14px]">
                    {attempt.mine ? '이전 문제로 돌아가기 ↗' : '이 문제 풀어보기 ↗'}
                  </span>
                </Button>
              )}
            </div>
          </>
        )}
      </main>
      <Footer />
    </div>
  );
}
