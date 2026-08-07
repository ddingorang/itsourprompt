package com.promptstudio.ai;

import com.promptstudio.attempt.domain.ToolCallEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * carry line 실험의 준수 검출기. 상시 지시 한 줄이 실제로 지켜졌는지를 코드가 센다.
 *
 * <p>판정은 전부 문자열 대조와 시퀀스 순서뿐이다 — 모델도 사람도 해석을 끼워 넣을 자리가 없다.
 * 판정을 모델에게 맡겼다가 같은 세션이 모델마다 다른 라벨을 받은 선례가 있어
 * ({@link PatternPrompts} 이름 판정) 여기서는 처음부터 코드가 센다.
 *
 * <p><b>형식은 채점하지 않는다.</b> 백틱 하나 때문에 절이 없는 것으로 세어 지표가 모델이 아니라
 * 마크업을 잰 사고가 있었다({@link LiveFeedbackHarnessTest} termAfter 주석). 그래서 어느 검출기도
 * 특정 접두사·머리표를 요구하지 않는다.
 *
 * <p>설계 근거는 {@code backend/docs/carry-line-design.md}에 있다.
 */
final class CarryLineChecks {

    /** 확인을 말하는 낱말. 하나라도 없으면 요약이 확인을 아예 언급하지 않은 것이다. */
    private static final Pattern CHECK_TERM = Pattern.compile("확인|검증|점검|테스트|실행|재현|돌려");

    /**
     * 독자에게 하는 말의 어미. 이것이 붙으면 요약이 읽는 사람에게 방법을 준 것이다.
     *
     * <p>{@code 수 있}은 {@code 수 없}과 겹치지 않는다 — 앞뒤 한 글자가 다르다.
     */
    private static final Pattern DIRECTIVE =
            Pattern.compile("세요|시면|(면|해)\\s*(됩니다|돼요|된다|돼)|수\\s*있");

    /**
     * AI가 자기가 한 일을 보고하는 종결. 문장 <b>끝</b>에서만 본다 — 문장 안 어딘가에 과거형이
     * 섞였다는 이유로 자기 보고로 몰면, 보고와 안내를 한 문장에 담은 요약이 잘못 실패한다.
     */
    private static final Pattern REPORT_ENDING =
            Pattern.compile("(했|였|됐|되었|봤|하였)(습니다|어요|다)[.!?]?$");

    /** 줄바꿈과 문장부호로 자른다. 요약은 불릿 줄이 흔해 줄바꿈이 문장 경계 노릇을 한다. */
    private static final Pattern SENTENCE_BREAK = Pattern.compile("\\R|(?<=[.!?])\\s+");

    private CarryLineChecks() {
    }

    /**
     * D5의 3값 판정. 애매한 요약은 {@link #UNSCORED}로 떨어뜨려 사람이 원문을 읽게 한다.
     */
    enum Verdict {
        PASS,
        FAIL,
        UNSCORED
    }

    /**
     * 툴 루프 한 턴에서 일어난 일 하나.
     *
     * <p>텍스트 이벤트는 {@code tool}·{@code path}가 null이고, 툴 이벤트는 {@code text}가 null이다.
     * 한 라운드의 어시스턴트 텍스트는 그 라운드의 툴 호출보다 <b>앞에</b> 놓는다 — 모델이 같은
     * 메시지에서 말을 먼저 하고 툴을 부르기 때문이다.
     *
     * @param round 이 이벤트가 일어난 툴 라운드. 1부터 시작한다
     */
    record Event(int round, String text, String tool, String path) {
    }

    /**
     * {@code edit_file} 한 건의 감사 결과. 편집마다 하나씩 나온다.
     *
     * @param announced    이 편집 이전 어시스턴트 텍스트에 그 파일 이름이 나왔는가 (D2)
     * @param readFirst    이 편집 이전 같은 턴에 그 경로 {@code read_file}이 있었는가 (D4)
     * @param withinPrompt 이 턴 프롬프트가 그 파일을 이름으로 불렀는가 (D3)
     */
    record EditAudit(int round, String path, boolean announced, boolean readFirst, boolean withinPrompt) {
    }

    /**
     * D2·D3·D4. 편집 이벤트를 순서대로 훑어 편집마다 감사 한 건을 낸다.
     *
     * <p>{@code list_files}는 대상이 없어 무시한다. 트레이스에 남은 {@code edit_file}은 전부 센다 —
     * 미존재 경로나 정책 위반으로 반영되지 않은 편집도 "모델이 그 파일에 손을 뻗었다"는 사실이다.
     * 실제로 내용이 바뀐 파일만 필요한 D1은 {@link #summaryNamesEveryEditedFile}이 따로 받는다.
     */
    static List<EditAudit> auditEdits(List<Event> events, String prompt) {
        List<EditAudit> audits = new ArrayList<>();
        StringBuilder spoken = new StringBuilder();
        Set<String> readPaths = new LinkedHashSet<>();
        String promptText = prompt == null ? "" : prompt;

        for (Event event : events) {
            if (event.tool() == null) {
                if (event.text() != null) {
                    spoken.append(event.text()).append("\n");
                }

                continue;
            }

            if (ToolCallEntry.READ_FILE.equals(event.tool())) {
                if (event.path() != null) {
                    readPaths.add(event.path());
                }

                continue;
            }

            if (!ToolCallEntry.EDIT_FILE.equals(event.tool())) {
                continue;
            }

            audits.add(new EditAudit(
                    event.round(),
                    event.path(),
                    namesFile(spoken.toString(), event.path()),
                    event.path() != null && readPaths.contains(event.path()),
                    namesFile(promptText, event.path())));
        }

        return List.copyOf(audits);
    }

    /**
     * D1. 요약이 편집된 파일을 하나도 빠뜨리지 않고 3종 표기 중 하나로 담는가.
     *
     * <p>편집이 없으면 담을 것이 없어 참이다. <b>편집 0회 턴을 분모에 넣으면 준수율이 부풀므로</b>
     * 호출자가 그 턴을 따로 세야 한다 — 아무것도 안 고치고 "준수"하는 퇴화를 이 함수는 못 가른다.
     */
    static boolean summaryNamesEveryEditedFile(Collection<String> editedPaths, String summary) {
        String text = summary == null ? "" : summary;

        for (String path : editedPaths) {
            if (!namesFile(text, path)) {
                return false;
            }
        }

        return true;
    }

    /**
     * D5. 요약이 "사람이 어떻게 확인하는지"를 독자에게 말하는가.
     *
     * <p><b>접두사로 채점하지 않는다.</b> {@code 확인 방법:}은 한쪽 문안만 지시하는 형식이라,
     * 접두사를 요구하면 다른 문안이 내용과 무관하게 구조적으로 진다. 두 문안 공통으로 잴 수 있는
     * 것만 본다 — 확인을 말하는가, 그리고 그 말이 독자를 향하는가.
     *
     * <p>확실한 두 끝만 기계가 판정한다.
     * <ul>
     *   <li>확인 낱말이 아예 없다 → {@code FAIL}. 어느 문안으로 읽어도 위반이다
     *   <li>확인 문장에 독자를 향한 어미가 있다 → {@code PASS}
     *   <li>확인 문장이 전부 자기 보고로 끝난다 → {@code FAIL}. {@code 동작을 확인했습니다} 류는
     *       AI가 자기가 했다고 말했을 뿐 독자에게 방법을 주지 않았다
     *   <li>그 밖 → {@code UNSCORED}. {@code 확인 방법: cancel 호출 후 값 비교}처럼 어미가 없는
     *       안내 줄이 여기 온다. 진짜 확인 방법인데 기계가 못 가르는 자리라 사람에게 넘긴다
     * </ul>
     *
     * <p>빈 요약도 {@code UNSCORED}다 — 읽을 것이 없으면 판정할 것도 없다.
     */
    static Verdict summaryStatesHowToCheck(String summary) {
        if (summary == null || summary.isBlank()) {
            return Verdict.UNSCORED;
        }

        boolean sawCheck = false;
        boolean allReported = true;

        for (String sentence : SENTENCE_BREAK.split(summary)) {
            if (!CHECK_TERM.matcher(sentence).find()) {
                continue;
            }

            sawCheck = true;

            if (DIRECTIVE.matcher(sentence).find()) {
                return Verdict.PASS;
            }

            if (!REPORT_ENDING.matcher(sentence.trim()).find()) {
                allReported = false;
            }
        }

        if (!sawCheck) {
            return Verdict.FAIL;
        }

        return allReported ? Verdict.FAIL : Verdict.UNSCORED;
    }

    /**
     * 파일 하나를 부르는 세 표기 — 전체 경로, 파일명, 확장자를 뗀 이름.
     *
     * <p>{@code PatternPrompts.namesPreviousChange}의 규약을 그대로 옮긴 것이다. 사람은
     * {@code src/main/java/com/shop/OrderService.java}보다 {@code OrderService}라고 쓴다.
     *
     * <p>경로가 없는 이벤트(툴콜 인자가 어긋난 경우)는 빈 집합이라 어느 대조도 통과하지 못한다.
     */
    static Set<String> nameForms(String path) {
        if (path == null || path.isBlank()) {
            return Set.of();
        }

        String fileName = path.substring(path.lastIndexOf('/') + 1);
        int dot = fileName.lastIndexOf('.');
        String bareName = dot < 0 ? fileName : fileName.substring(0, dot);

        return new LinkedHashSet<>(List.of(path, fileName, bareName));
    }

    private static boolean namesFile(String text, String path) {
        for (String form : nameForms(path)) {
            if (text.contains(form)) {
                return true;
            }
        }

        return false;
    }
}
