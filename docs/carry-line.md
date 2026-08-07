# carry line 조사 — 상시 지시의 준수·감쇠, 지시 파일 관행, 확실히 세지는 신호

작성일: 2026-08-07
기준: BE-main `24c2e5c` (`BE/pattern-run-signal` 머지 후)

> 목적: 제출 후 사용자가 **다음 문제로 가져갈 규칙 한 줄**(carry line)을 만들려 한다. 이 문서는 그 앞의
> 세 질문에 답한다 — 상시 지시가 실제로 지켜지는가(Q1), 그 줄을 어디에 어떻게 쓰라고 말할 것인가(Q2),
> 어떤 신호를 근거로 그 줄을 고를 것인가(Q3).
>
> 1차 자료는 본문까지 열어 읽었다. 본문에 닿지 못한 것은 [접근 불가 목록](#접근-불가-목록)에 어디까지
> 읽었는지 적었고, 본문을 못 읽은 자료의 주장은 근거 강도를 낮춰 표기했다.

---

## 이 문서가 정한 것

| 항목 | 값 | 근거 강도 |
|---|---|---|
| **X** — 대조군 대비 준수율 증분 하한 | **20%p** | 약 (문헌은 양의 효과크기를 주지 않는다. 우리 표본의 분해능에서 역산) |
| **Y** — 후반(3라운드 이후) 준수율 하한 | **70%** | 중 (측정된 세션 내 감쇠율 OR=0.944와 단일 지시 준수율 ~96%에서 유도, 여유 폭을 크게 둠) |
| **파일 이름 표기** | **`AGENTS.md`** (Claude Code는 `CLAUDE.md`에서 import) | 강 (도구별 공식 문서로 지원 여부 확인) |
| 카탈로그 문안 어투 | **변경 없음** — 10문안 전부 Q2 스타일 규칙 통과 | 강 |

---

## Q1 상시 지시의 준수·감쇠 — 축은 "루프 안 컨텍스트 성장"

### 이 실험대가 실제로 재는 것

먼저 축을 좁힌다. 코드 생성 호출에는 **턴 히스토리가 없다** —
`CodeGenerationPrompts.messages`의 히스토리 루프가 주석 처리돼 있어(`CodeGenerationPrompts.java:75-78`)
매 턴이 SystemMessage 1 + UserMessage 1의 독립 대화로 시작한다. 그래서 "턴이 쌓이면 지시가 흐려진다"는
축은 이 실험대에 **없다**.

컨텍스트가 자라는 곳은 한 턴 안이다. `OpenAiCodeGenerator`는 툴을 실은 호출을 최대 10라운드 돌리고
라운드마다 assistant/tool 메시지를 누적한다(`OpenAiCodeGenerator.java:41,110-120`). 즉 이 실험이 재는
감쇠는 **한 턴 안의 툴 루프에서 입력이 길어지는 동안의 감쇠**다. 목적지인 실제 에이전트 세션(수십 턴,
수백 툴콜)의 감쇠보다 훨씬 짧은 구간이고, 이 문서의 어떤 수치도 그 긴 구간으로 외삽하지 않는다.

### 문헌이 말하는 것

**① 우리 질문과 가장 가까운 연구는 "구조는 안 듣고, 세션 안에서 흐른다"고 말한다.**

McMillan, *Instruction Adherence in Coding Agent Configuration Files: A Factorial Study of Four
File-Structure Variables* (arXiv:2605.10039, 2026-05-11). 코딩 에이전트 설정 파일(CLAUDE.md /
AGENTS.md / Cursor Rules)의 준수율을 요인 설계로 잰 연구다. 초록 원문:

> "We report a systematic factorial study of these choices using four manipulated variables, measuring
> compliance with a trivial target annotation across 1,650 Claude Code CLI sessions (16,050
> function-level observations) on two TypeScript codebases... **None of the four structural variables
> or three two-way interactions produces a detectable contrast after multiple-testing correction.**
> Size and conflict nulls are supported by affirmative-null Bayes factors (BF10 between 0.05 and 0.10)...
> **The largest effect we measured is within-session: each additional function the agent generates is
> associated with approximately 5.6% lower odds of compliance per step (OR = 0.944)** within the
> session-length range we tested, though the relationship is non-monotonic rather than a constant
> per-step effect."

우리에게 주는 것 둘.

- **파일 크기·지시 위치·파일 구조·인접 파일 모순 — 넷 다 검출 가능한 차이를 못 냈다.** 크기와 모순은
  귀무를 **적극적으로 지지하는** 베이즈 인자까지 붙었다. 그러니 "어떻게 배치하느냐"를 우리가 실험할
  이유가 없다. 우리 실험은 **무엇을 쓰느냐**(문안)만 흔든다 — 플랜이 이미 그렇게 잡혀 있고, 이 연구가
  그 선택을 뒷받침한다.
- **감쇠는 세션 안에서, 생성 단계마다 일어난다.** 우리가 라운드 번호로 편집 이벤트를 가르는 설계와
  같은 모양의 축이다. 이 연구는 "함수 하나 더 생성할 때마다"로 셌고 우리는 "툴 라운드 하나 더"로 센다 —
  단위가 다르므로 계수를 그대로 옮기지는 않고, **감쇠가 세션 안에 있다**는 방향만 가져온다.

**② 지시를 쌓으면 비선형으로 무너진다. 다만 우리는 한 줄만 더한다.**

Anand & Chattaraj, *Instruction Stacking Collapse: A Benchmark and the Capability-Dependent Value of
Prompt Compilation* (arXiv:2608.02639, 2026-07-31). 초록 원문:

> "We introduce a benchmark that stacks 24 verifier-checked instructions, one to twenty at a time, and
> evaluate three production-tier LLMs (Claude Sonnet 4.6, GPT-5-mini, Gemini 2.5 Flash).
> **Instruction-following degrades non-linearly: the follow rate falls from ~96% to as low as 20%**,
> driven by a structured and reproducible set of pairwise conflicts. A single "output JSON" constraint,
> for example, is jointly unsatisfiable with nine others."

**지시 1개일 때 준수율 ~96%** — 이 값이 Y의 출발점이다. 우리 carry line은 이미 제약 6줄을 든 시스템
프롬프트에 **한 줄을 더하는** 것이라 붕괴 구간이 아니라 높은 구간에 있다. 다만 이 연구가 붕괴의 원인으로
지목한 것이 "쌍쌍 충돌"이므로, 우리가 더하는 줄이 기존 제약과 같은 말을 하거나 어긋나는지는
[설계 문서에서 한 줄씩 대조](../backend/docs/carry-line-design.md)한다.

**③ 긴 툴 사용 구간에서 상시 문서는 권위로 작동하지 않는다.**

Panavas et al., *HANDBOOK.md: A Benchmark for Long-Context Agentic Instruction Following*
(arXiv:2607.25398, 2026-07-28, v3 2026-08-03). 초록 원문 일부:

> "Language-model agents are increasingly deployed under standing instructions: a system prompt, a
> policy file, or a skills document is placed in context, and the agent is trusted to let that document
> govern every action that follows... **Under strict grading, where a trial passes only if every
> criterion is satisfied, the strongest evaluated model passes 36.2% of trials, and most frontier
> models remain below 25%.** Failures follow consistent patterns: agents let a plausible but
> unauthorized in-environment request override the standing policy, perform a required check and then
> act against its result, **lose rule details over long horizons**, and report compliance they did not
> achieve."

본문까지 확인한 사실:

- 과제 하나가 평균 **약 17 에이전트 스텝·30 툴콜**이고, 정책 문서는 20~124쪽이다.
- 채점은 전부 결정적이다 — 과제마다 프로그램 기준 루브릭(총 824개)이 필수 행동과 금지 행동을 함께 본다.
- **"긴 구간에서 규칙 세부를 잃는다"는 실패 유형은 정성 서술이고, 턴 수·툴콜 수에 대한 상관 분석은
  결과 절에 없다.** 재읽기·리마인더가 도움이 되는지의 실험도 없다. 저자들은 "the standing document does
  not function for current models as a persistent authority"라며 모델 밖의 하드 컨트롤을 권한다.

우리에게 주는 것: **경고이지 눈금이 아니다.** 20~124쪽 정책의 전 기준 동시 만족률(36.2%)은 한 줄짜리
규칙의 이벤트별 준수율과 분모가 다르다. Y의 근거로 쓰지 않는다.

**④ 입력이 길어지면 성능이 비균일하게 떨어진다.**

Chroma Research, *Context Rot: How Increasing Input Tokens Impacts LLM Performance* (2025).
18개 모델(Claude Opus 4·Sonnet 4·3.7·3.5·Haiku 3.5, o3, GPT-4.1 계열, GPT-4o, Gemini 2.5 Pro/Flash,
Qwen3 등)을 확장 NIAH·LongMemEval(306 프롬프트, 전체 ~113k 토큰 vs 초점 ~300 토큰)·반복 단어 과제로
쟀다. 결론 문단 원문:

> "Through our experiments, we demonstrate that LLMs do not maintain consistent performance across
> input lengths. Even on tasks as simple as non-lexical retrieval or text replication, we see
> increasing non-uniformity in performance as input length grows... Whether relevant information is
> present in a model's context is not all that matters; what matters more is how that information is
> presented."

**한계를 분명히 적는다.** 이 보고서는 본문에 집계 수치를 거의 싣지 않는다 — 저하는 그래프로 제시되고,
텍스트로 확인되는 수치는 거부율(GPT-4.1 2.55%, Claude Opus 4 2.89%, GPT-3.5 Turbo 60.29%로 제외)과
과제 규모 정도다. 그래서 이 자료는 **방향 근거**로만 쓰고 X·Y에 산술로 쓰지 않는다. 다만 반복 단어
과제에서 "Accuracy is highest when the unique word is placed near the beginning of the sequence,
especially as input length increases"라고 적은 대목은 시스템 프롬프트(맨 앞)에 규칙을 두는 우리 주입
지점과 방향이 맞는다.

**⑤ 보조 — 위치 효과와 다중 턴 저하.**

- Liu et al., *Lost in the Middle* (TACL 2024, arXiv:2307.03172). **초록까지만 확인했다**(본문 접근 실패,
  아래 참조). 초록 원문: "performance is often highest when relevant information occurs at the beginning
  or end of the input context, and significantly degrades when models must access relevant information
  in the middle of long contexts, even for explicitly long-context models." 우리 주입 지점(맨 앞)을
  지지하는 방향이지만, 수치를 못 읽었으므로 **주장 없이 방향만** 인용한다.
- Laban et al., *LLMs Get Lost in Multi-Turn Conversation* (arXiv:2505.06120). 본문 확인:
  "Model aptitude degrades in a non-significant way between the full and sharded settings, with an
  average drop of 16%. On the other hand, **unreliability skyrockets with an average increase of 112%**
  (more than doubling)." aptitude = percentile₉₀(S), unreliability = percentile₉₀(S) − percentile₁₀(S).
  RECAP(대화 전체를 다시 실어 줌) 조건은 sharded를 일부만 회복시킨다 — GPT-4o에서 full 93.0 / sharded
  59.1 / recap 76.6.
  **우리 실험대에는 턴 사이 컨텍스트 누적이 없으므로 이 연구의 턴 축 주장은 쓰지 않는다.** 가져오는 것은
  하나 — 성장하는 컨텍스트에서 무너지는 것은 평균 실력이 아니라 **분산**이라는 사실이다. 그래서 우리도
  준수율 한 숫자만 보지 않고 반복(reps)과 원문을 함께 남긴다.
- Li et al., *Measuring and Controlling Instruction (In)Stability in Language Model Dialogs*
  (COLM 2024, arXiv:2402.10962). 본문 확인: 두 챗봇 self-chat을 **N=8 라운드(16턴)** 돌리며 라운드마다
  프로브 질문으로 시스템 프롬프트 준수를 0~1 안정성 점수로 잰다. LLaMA2-chat-70B에서 1라운드 ~0.8이
  8라운드에 ~0.5~0.55로 내려간다. 원인 분석은 **attention decay** — 시스템 프롬프트 토큰에 가는 어텐션
  합 π(t)가 "sharp drops in attention between turns and rough plateaus within turns"를 보인다.
  **대응 한계를 분명히 한다**: 이 연구의 축은 사용자·에이전트가 번갈아 말하는 대화 턴이고, 우리 축은
  한 턴 안의 툴 라운드다. 누적되는 메시지가 어텐션을 나눠 간다는 기제는 같지만 단위가 다르므로,
  이 연구의 감쇠 곡선을 우리 라운드에 그대로 얹지 않는다.

### 그래서 X는 얼마인가

**X = 대조군 대비 준수율 증분 20%p. 근거 강도 약.**

솔직하게 적는다. **문헌은 X를 주지 못한다.** 우리 질문에 가장 가까운 연구(arXiv:2605.10039)가 낸 결론은
구조 변수 넷 전부에서 **효과가 검출되지 않았다**는 것이고, 그중 둘은 귀무를 적극 지지하는 베이즈 인자까지
붙었다. 양의 효과크기를 빌려올 곳이 없다.

그래서 X는 **우리 표본이 분해할 수 있는 크기**에서 역산한다. 팔 페이즈 × `-PllmReps=3`이면 팔 하나가
턴 실행 12회다(4턴 × 3회). 턴마다 편집 이벤트가 2건쯤 나오면 팔당 표본 n≈24다. 두 비율의 차이의 표준오차는
p≈0.8에서

    SE = sqrt(2 × 0.8 × 0.2 / 24) ≈ 0.115 → 11.5%p

이고 2SE ≈ 23%p다. 즉 **이 규모에서 20%p 아래의 Δ는 잡음과 구분되지 않는다.** 20%p를 넘겨야 "봤다"고
말할 수 있다는 뜻이지, 20%p가 의미 있는 효과의 경계라는 뜻이 아니다.

두 가지 읽기 규칙을 함께 둔다.

1. **요약 단위 검출기(D1·D5)는 분모가 더 작다.** reps=3에서 요약은 팔당 12개뿐이라 SE ≈ 16.3%p,
   2SE ≈ 33%p다. 요약 단위 팔이 20%p는 넘고 33%p는 못 넘으면 **채택이 아니라 `미확정`**으로 적고, 채택하려면
   reps를 올려야 한다. 하네스가 팔마다 n을 함께 찍으므로 읽는 사람이 이 규칙을 적용할 수 있다.
2. **가지치기 단계(스모크, reps=1)에는 X를 쓰지 않는다.** 그 단계의 기준은 플랜대로 "Δ ≤ 0이면 탈락"과
   "대조군이 이미 ≥90%면 아무 일도 안 함"뿐이다. reps=1로 20%p를 논하는 것은 잡음을 읽는 것이다.

### 그래서 Y는 얼마인가

**Y = 후반(3라운드 이후) 편집 이벤트 준수율 70%. 근거 강도 중.**

유도 과정:

- 출발점은 arXiv:2608.02639의 **지시 1개 준수율 ~96%**다. 우리는 시스템 프롬프트에 한 줄을 더하는
  저부하 구간에 있다.
- 감쇠율은 arXiv:2605.10039의 **OR = 0.944 / step**을 쓴다. 편집이 5라운드에서 일어났다면 첫 편집으로부터
  네 스텝이므로 오즈가 0.944⁴ ≈ 0.795배가 된다. 초반 준수율이 0.90(오즈 9)이면 후반은 오즈 7.16 → **0.877**,
  초반이 0.80(오즈 4)이면 후반은 오즈 3.18 → **0.761**이다.
- 즉 **문헌이 측정한 감쇠율을 우리 10라운드 지평에 적용하면 후반은 초반보다 몇 %p 낮은 데 그친다.**
  70%는 그 예측보다 한참 아래에 둔 하한이다 — 단위가 다르고(함수 생성 vs 툴 라운드) 모델도 다르므로
  여유를 크게 뒀다.

읽기 보조 하나를 덧붙인다(게이트가 아니다). 하네스가 초반·후반 준수율을 나란히 찍으므로,
**후반/초반 비율이 0.79 미만이면** 문헌이 측정한 감쇠(0.944⁴ = 0.795)보다 빠르게 무너지고 있다는 신호이니
숫자로 판정하지 말고 그 팔의 원문부터 읽는다.

Y가 걸리지 않는 경우도 규칙을 정해 둔다: **후반 라운드 편집이 아예 없는 실행은 unscored로 분모에서 뺀다**
(플랜 5절). 후반 표본이 전체 편집의 10% 미만이면 과제가 얕은 것이므로 T4를 교체하고 다시 돌린다.

---

## Q2 지시 파일 관행·표기

### (a) 문안 스타일 규칙

도구 세 곳의 공식 문서가 같은 말을 한다.

| 출처 | 원문 | 규칙 |
|---|---|---|
| Claude Code 메모리 문서 | "write instructions that are concrete enough to verify" — 예: "Use 2-space indentation" instead of "Format code properly" | **검증 가능한 조건** |
| Claude Code 메모리 문서 | "target under 200 lines per CLAUDE.md file. Longer files consume more context and reduce adherence" | **짧게** |
| Claude Code 메모리 문서 | "if two rules contradict each other, Claude may pick one arbitrarily" | **기존 지시와 모순 금지** |
| Cursor Rules 문서 | rules should be "focused, actionable, and scoped"; "Avoid vague guidance"; "Keep rules under 500 lines" | **초점·실행 가능·범위** |
| aider conventions 문서 | 예시 전문: "- Prefer httpx over requests for making http requests.\n- Use types everywhere possible." | **한 줄 명령형** |
| agents.md | "AGENTS.md is just standard Markdown. Use any headings you like; the agent simply parses the text you provide." | 형식 자유 |

정리하면 **한 줄 · 명령형 · 검증 가능한 조건 · 기존 지시와 모순 없음** 넷이다.

한 가지 덧붙일 것. Claude Code 문서는 CLAUDE.md가 **시스템 프롬프트가 아니라 그 뒤의 사용자 메시지로**
전달된다고 적는다 — "CLAUDE.md content is delivered as a user message after the system prompt, not as
part of the system prompt itself... there's no guarantee of strict compliance." Cursor는 반대로
"When applied, rule contents are included at the start of the model context"라고 적는다. 도구마다
주입 위치가 다르지만, arXiv:2605.10039가 **지시 위치에서 검출 가능한 차이를 못 찾았으므로** 우리
실험이 위치를 흔들 이유는 없다. 우리는 시스템 프롬프트 뒤에 붙이는 한 가지만 쓴다.

### (b) 파일 이름 표기 권고 — `AGENTS.md`

지원 근거(전부 각 도구 공식 문서):

- **agents.md**: "A simple, open format for guiding coding agents", "a README for agents". "used by over
  60k open-source projects". 지원 도구로 Codex, Jules, Factory, Aider, goose, opencode, Zed, Warp,
  VS Code, Devin, UiPath, Junie, Amp, **Cursor**, RooCode, Gemini CLI, Kilo Code, Phoenix, Semgrep,
  GitHub Copilot coding agent, Ona, Windsurf, Augment Code를 나열한다. 우선순위 규칙도 명시한다 —
  "The closest AGENTS.md to the edited file wins; **explicit user chat prompts override everything**."
- **Cursor**: `AGENTS.md`를 "Agent instructions in markdown format. Simple alternative to
  `.cursor/rules`"로 문서화하고 하위 디렉터리 중첩까지 지원한다.
- **Claude Code**: 명시적으로 **읽지 않는다** — "Claude Code reads `CLAUDE.md`, not `AGENTS.md`."
  다만 같은 문서가 다리를 안내한다: CLAUDE.md 첫 줄에 `@AGENTS.md`를 쓰거나
  `ln -s AGENTS.md CLAUDE.md` 심볼릭 링크를 만든다.

**권고: 사용자에게 보여줄 때 `AGENTS.md`를 이름으로 쓰고, Claude Code 사용자를 위해 `CLAUDE.md`에서
`@AGENTS.md`로 불러오면 된다는 한 줄을 괄호로 붙인다.**

이유 셋.

1. 지원 폭이 가장 넓고, 유일한 큰 예외(Claude Code)가 **자기 문서에 다리를 적어 뒀다.** 반대 방향
   (`CLAUDE.md`를 권하고 다른 도구가 읽게 하기)에는 공식 다리가 없다.
2. 도구 무관 표현("당신이 쓰는 도구의 상시 지시 파일에 넣으세요")은 안전하지만 **행동을 지시하지 못한다.**
   사용자는 제출 직후 한 줄을 받는 사람이고, 그가 다음에 할 일은 파일을 열어 붙여넣는 것이다. 열 파일
   이름을 말해 주는 편이 낫다.
3. **파일 선택은 준수율 문제가 아니라 지원 문제다.** arXiv:2605.10039가 파일 구조·크기·위치·모순 어디서도
   준수율 차이를 못 찾았으므로, 어느 파일에 넣느냐로 지켜지고 안 지켜지고가 갈리지 않는다. 남는 기준은
   "그 도구가 그 파일을 읽는가" 하나뿐이고, 거기서는 AGENTS.md가 이긴다.

**근거가 없는 지점을 표시해 둔다.** 우리 사용자가 실제로 어떤 도구를 쓰는지는 **모른다.** 사용자 조사도
텔레메트리도 없다. 위 권고는 "도구 생태계에서 어느 이름이 가장 널리 읽히는가"에 대한 답이지 "우리
사용자에게 어느 이름이 맞는가"에 대한 답이 아니다. 화면 문구를 확정하기 전에 이 구멍을 메우거나,
못 메우면 메우지 못했다는 사실과 함께 확정해야 한다.

### (c) 카탈로그 문안 어투 — 변경 없음

플랜의 10문안을 (a)의 네 규칙에 하나씩 대조했다.

| 규칙 | 결과 |
|---|---|
| 한 줄 | 10/10 통과 (s1b·s3b·s4b·s5b는 두 문장이지만 줄바꿈이 없다 — 규칙은 "한 줄"이지 "한 문장"이 아니다) |
| 명령형 | 10/10 통과 |
| 검증 가능한 조건 | 10/10 통과 — 각 문안이 검출기 하나에 1:1로 대응하고, 그 검출기가 문자열·시퀀스 대조만 한다는 것이 곧 검증 가능성이다 |
| 기존 지시와 모순 없음 | 모순 0건. 다만 **중복 2건**을 찾았고 설계 문서에 옮겼다 |

**어투는 바꾸지 않았다.** 이유 둘. 첫째, 네 규칙을 전부 통과하므로 Q2가 변경을 요구하지 않는다. 둘째,
문안은 시스템 프롬프트 바로 뒤에 붙어 같은 화면에서 읽히는데 그 프롬프트가 `~하세요`체이고 문안이
`~하라`체다. 여기서 어투를 손대면 **문안 내용이 아니라 어투가 준수율을 움직였을 가능성**이 생기고,
그것을 가려낼 대조군이 실험에 없다. 어투를 고정하는 편이 측정을 지킨다.

찾은 중복 2건은 실험 해석에 직접 걸리므로 설계 문서 「기존 시스템 프롬프트와의 중복」 절로 옮겼다 —
요약하면 s4(읽고 고쳐라)는 시스템 프롬프트가 이미 하는 말이고, s2(대상을 먼저 밝혀라)는 그 일부와 겹친다.
둘 다 **대조군이 이미 높게 나올 것**을 예고하므로, 그렇게 나오면 모델 탓이 아니라 예상된 결과다.

---

## Q3 확실히 세지는 신호

"확실히 세진다"의 뜻을 먼저 고정한다. **판정이 문자열 대조와 시퀀스 순서만으로 끝나고, 모델도 사람도
해석을 끼워 넣을 자리가 없어야 한다.** 이 기준은 새로 만든 것이 아니라 이미 운영 코드가 서 있는 자리다 —
pattern 렌즈의 이름 판정은 원래 모델이 했는데 "같은 세션 다섯이 mini에서는 전부 `vibe coding`,
luna에서는 전부 이름 없음, 문구를 넓히자 전부 `human review`"가 되는 것을 보고 코드로 옮겼다
(`PatternPrompts.java:285-297`).

3종 표기 대조(전체 경로 / 파일명 / 확장자 뗀 이름)는 그 정본
`PatternPrompts.namesPreviousChange`(`PatternPrompts.java:330-345`)를 그대로 따른다.

> **도달률 추정의 성격.** 로컬에 실데이터 DB가 없다. 아래 도달률은 **손으로 짠 픽스처
> `LiveSessions.java`와 설계 문서의 실측 기록**에서 센 것이고, 픽스처는 관찰이 아니라 저자가 그럴듯하다고
> 판단해 적은 것이다. 실배포 데이터로 검증하는 것은 다음 MR 몫이다.

### S1 `prompt_names_previous_changed_file` (기존 계산 재사용)

- **무엇을 세는가**: 앞 턴이 파일을 바꿨는데 이 턴 프롬프트가 그 이름을 하나도 부르지 않은 턴.
- **왜 추측이 아닌가**: `String.contains` 3종뿐이고, 이미 운영 코드가 매 제출에서 돌린다
  (`PatternPrompts.java:298-345`). 새로 만들 것이 없다.
- **도달률 — 높음**: `LiveSessions`에서 pattern 기대값이 명시된 20턴 중 **17턴(85%)이 `vibe coding`
  (= named=false)**이다. 세션 여덟 중 다섯이 원래 프롬프트 렌즈용으로 만들어졌는데도 이 비율이다.

### S2 `prompt_names_edited_file` (신규)

- **무엇을 세는가**: 이 턴이 편집한 파일을 이 턴 프롬프트가 하나도 부르지 않은 턴 — 대상 선정을 AI가 한 턴.
- **왜 추측이 아닌가**: `changes` 경로 × 프롬프트 `contains` 3종. S1과 같은 연산이고 비교 대상만 다르다
  (앞 턴 → 이 턴).
- **도달률 — 높음**: 목표 서술형 프롬프트에서 항상 발화한다. `LiveSessions`의 S6·S7·S8 턴들이 그 모양이고
  (`"요구사항\n- 배송이 시작된 주문은 IllegalStateException으로 취소를 거부함"` 처럼 파일 이름이 없다),
  우리 실험 세션 N의 T1~T4도 전부 그 모양으로 짰다.

### S3 `change_outside_prompt` (신규)

- **무엇을 세는가**: 프롬프트가 부르지 않은 파일이 변경에 포함된 턴.
- **왜 추측이 아닌가**: `changes` 경로 × `contains` + `ChangeType`. 새 파일 생성(ADDED)이면 특히 또렷하다.
- **도달률 — 낮~중**: 픽스처에 실례가 하나 있다 — S2-범위초과의 턴 2에서 AI가 아무도 요청하지 않은
  `OrderController`를 새로 만든다(`LiveSessions.java:217-228`). 실험대에서는 `edit_file`이 기존 파일만
  고칠 수 있어 ADDED가 나오지 않으므로, 스켈레톤에 `OrderController`를 미리 넣어 "부르지 않은 파일을
  고치는" 형태로만 관찰된다. **안 걸리면 안 보여주면 된다** — 빈 절을 만들지 않는다는 원칙과 정합한다.

### S4 `edit_without_read` (신규)

- **무엇을 세는가**: 같은 턴 `toolCalls`에서 앞선 같은 경로 `read_file` 없이 `edit_file`이 나온 편집.
- **왜 추측이 아닌가**: `ToolCallEntry` 시퀀스의 순서와 경로 대조뿐이다. 트레이스는 어댑터가
  기록하므로(`CodeGenerationTools.java:90-123`) 모델의 자기 보고가 아니다.
- **도달률 — 중간**: `LiveSessions`의 편집 이벤트 **30건 중 12건(40%)**이 같은 턴에 그 경로의 `read_file`
  없이 일어난다. 내역은 `LiveSessions.java:170-172`(INVENTORY만 읽고 ORDER_SERVICE 편집),
  `226-228`(list_files 뒤 바로 편집 둘), `244-246`(같은 모양),
  `395-396`·`461-462`·`553-554`·`627-628`(읽기 없이 두 파일 편집)이다. 나머지 16건은 `readEdit` 헬퍼로
  만들어져 전부 읽고 고친다.
- **성질이 다르다**: S1~S3의 근거는 플레이어가 쓴 문장이지만 S4의 근거는 **우리 에이전트의 툴 호출**이다.
  이 처방에는 "우리 시스템 프롬프트·툴 셋에서 본 습관이 플레이어의 다른 에이전트에서도 같은 방향으로
  나타난다"는 검증되지 않은 가정이 하나 더 낀다. 설계 문서가 그 가정의 1차 관문을 정해 뒀다.

### S5 `turn_has_no_finished_run` (BE-main의 `RUN_TAG` 재사용)

- **무엇을 세는가**: `TurnTestResults`로 계산한 `ran=false`인 턴.
- **왜 추측이 아닌가**: 채점 인프라의 상태값이지 프롬프트 해석이 아니다. `PatternPrompts.hasFinishedRun`이
  이미 그 계산이고(`PatternPrompts.java:381-391`), 판단 규칙은 `TurnTestResults.TurnTestResult#ran()`
  한 곳에만 있다. `RUNNER_ERROR`는 실행 없음으로 센다.
- **도달률 — 중간~높음**: 실행은 사용자가 눌러야 일어나는 별도 행동이라 안 누른 턴이 흔하다.
  픽스처 S6(안짚음)는 실행 기록을 통째로 비워 이 상황을 재현한다(`LiveSessions.java:557-571` 주석).
- **문안을 쓰는 방식이 다르다**: 실험대의 에이전트는 툴이 `list_files`/`read_file`/`edit_file` 셋뿐이라
  **코드를 실행할 수 없다**. "테스트를 돌려라" 류는 준수할 방법이 없어 검출기가 항상 실패하고 실험이
  성립하지 않는다. 그래서 s5a·s5b는 실행을 시키지 않고 **확인 방법을 산출물로 남기게** 쓴다.

### 기각한 후보

| 후보 | 기각 사유 |
|---|---|
| 프롬프트가 실행 결과를 언급했는지 키워드로 감지 | 키워드 추측이다. "돌려 봤어요"·"에러 났어요"·"화면이 안 떠요"는 무한히 변주되고, 어느 목록을 써도 목록이 지표를 정하게 된다. 같은 실패를 pattern 렌즈가 이미 겪었다(`PatternPrompts.java:285-297`) |
| 요약 문장이 "제대로 됐다"고 주장하는지 감지 | 위와 같은 문제에 더해, **판정 대상이 자연어 주장**이라 사람이 읽어도 갈린다 |
| 프롬프트 길이·문장 수 | 세지기는 확실히 세지지만 **처방이 안 나온다.** "더 길게 쓰세요"는 검증 가능한 조건이 아니고, 길이와 결과의 인과도 우리에게 근거가 없다 |
| 턴 사이 시간 간격 | 데이터 표면에 없고(`TurnView`에 시각 없음), 있어도 자리를 비운 것인지 생각한 것인지 못 가른다 |
| 코드 품질·설계 판정 | 두 렌즈의 명시적 경계 밖이다("Do not grade code quality, style, or design" — `PatternPrompts.java:76`) |

---

## 접근 불가 목록

| 자료 | 어디까지 읽었나 | 왜 |
|---|---|---|
| McMillan, arXiv:2605.10039 | **초록 전문만**. 본문 실패 | arXiv HTML판 없음(404), PDF는 FlateDecode라 도구가 디코드 못 함, papers.cool은 초록만 노출. 조건별 원 준수율·신뢰구간·비단조성 서술·논의 절을 **읽지 못했다**. 이 문서가 인용한 수치(OR=0.944, ~5.6%/step, BF10 0.05~0.10, 1,650 세션 / 16,050 관측)는 **전부 초록에 직접 적힌 값**이고 본문에서 확인하지 않았다 |
| Liu et al., *Lost in the Middle*, arXiv:2307.03172 | **초록 전문만**. 본문 실패 | ACL Anthology 페이지는 초록만, PDF(2.5MB)는 디코드 불가, 저자 페이지는 GitHub 리다이렉트. U자 곡선의 크기·모델 목록·key-value 검색 결과를 **읽지 못했다.** 그래서 이 문서는 이 논문을 **방향 근거로만** 쓰고 수치를 인용하지 않는다 |
| Chroma, *Context Rot* | 본문 전체 | 접근은 됐으나 **보고서 자체가 본문에 집계 수치를 싣지 않는다**(그래프로만 제시). 자료의 한계이지 접근 한계가 아니다 |
| Panavas et al., arXiv:2607.25398 | 초록 + 본문(HTML) | "긴 구간에서 규칙 세부를 잃는다"의 **정량 뒷받침이 논문에 없다**는 것까지 확인했다 |
| Anand & Chattaraj, arXiv:2608.02639 | 초록 전문 | 인용한 값(~96% → ~20%, 지시 1~20개, 모델 3종)이 전부 초록에 있다. 쌍쌍 충돌 목록은 본문에 있고 읽지 않았으나, 이 문서가 그 목록에 기대는 주장은 없다 |
| aider conventions 문서 | 본문 전체 | 접근됨. 다만 **분량·준수 이탈에 대한 언급이 문서에 아예 없다** — 예시 문안의 모양만 근거로 쓴다 |

1차 자료 URL:
Chroma <https://www.trychroma.com/research/context-rot> ·
arXiv:2605.10039 <https://arxiv.org/abs/2605.10039> ·
arXiv:2607.25398 <https://arxiv.org/abs/2607.25398> ·
arXiv:2608.02639 <https://arxiv.org/abs/2608.02639> ·
arXiv:2505.06120 <https://arxiv.org/abs/2505.06120> ·
arXiv:2402.10962 <https://arxiv.org/abs/2402.10962> ·
TACL 2024 <https://aclanthology.org/2024.tacl-1.9/> ·
agents.md <https://agents.md/> ·
Claude Code 메모리 <https://code.claude.com/docs/en/memory> ·
Cursor Rules <https://cursor.com/docs/context/rules> ·
aider conventions <https://aider.chat/docs/usage/conventions.html>
