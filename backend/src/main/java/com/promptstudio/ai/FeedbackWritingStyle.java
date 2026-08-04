package com.promptstudio.ai;

/**
 * 사용자에게 보일 문장의 문체. 피드백 스타일 두 개가 같이 끼워 쓴다.
 *
 * <p>공유하는 것은 <b>규칙뿐</b>이고 예문은 렌즈마다 자기 소재로 갖는다. 예문까지 공유하면 프롬프트
 * 코치의 6칸 프레임 문장이 pattern 프롬프트에 그대로 실린다 — pattern 쪽은 "프롬프트를 라벨로
 * 분류하지 마라"고 규칙으로 말해 놓고 바로 아래 예문 전부가 그 프레임이 된다.
 * <b>예문이 규칙보다 세게 가르친다.</b>
 *
 * <p>규칙을 공유하는 이유는 그대로다 — 사용자가 한 화면에서 두 피드백을 나란히 읽으므로 한쪽만
 * 고치면 같은 화면 안에서 목소리가 갈린다.
 */
final class FeedbackWritingStyle {

    private static final String TEMPLATE = """
            # Writing style
            The reader is the person who wrote these prompts. Apply every rule below to every sentence the user sees.

            ## 어미
            Write in 해요체. End statements with `~해요` and requests with `~하세요`. Never mix in `~합니다`.
            %1$s

            ## 주어
            Name the actor. What the user did is `~하셨어요`, what the AI did is `AI가 ~했어요`.
            Never make a prompt, a file or the system the subject of an action.
            %2$s
            Never use the passive voice.
            %3$s

            ## 단어
            Use a verb where a derived noun would do. Drop 수행·진행·실시·처리.
            %4$s
            Never write these fillers: 다음으로 / 앞서 설명했듯이 / 이제 살펴보겠습니다 / 결론적으로 / 사실은 / 아시다시피
            Never hedge: 가능성이 있다 / 일부 경우 / ~할 수도 있다. Write only what the changed files show.
            One thought per sentence. Do not join two conditions with `~하고`.
            %5$s

            ## 구체성
            Call files, methods and values by name.
            %6$s
            Say what is missing by name instead of calling it insufficient.
            %7$s

            ## 시제
            An observation is a past fact. A prescription says what to write next time.
            Never phrase a prescription as an obligation the user missed — drop `~했어야 해요`.
            %8$s

            ## 배치
            Put one line above every code block saying what the block is. Never open with the block.
            Open each section with its own substance. Never spend a sentence announcing what the section will do.
            Keep each section to four sentences or fewer.%9$s
            """;

    private FeedbackWritingStyle() {
    }

    /**
     * @param sentenceCapNote 네 문장 상한에서 무엇을 빼는지에 대한 렌즈별 단서. 셀 것이 없는 렌즈는 빈 문자열
     */
    static String section(Examples examples, String sentenceCapNote) {
        return TEMPLATE.formatted(
                examples.politeEnding().render(),
                examples.actor().render(),
                examples.passiveVoice().render(),
                examples.derivedNoun().render(),
                examples.oneThought().render(),
                examples.callByName().render(),
                examples.nameWhatIsMissing().render(),
                examples.tense().render(),
                sentenceCapNote
        );
    }

    /**
     * 규칙 하나를 가르치는 Don't/Do 쌍.
     */
    record Example(String avoid, String prefer) {

        /**
         * 두 줄의 들여쓰기는 고정이다. `이렇게:` 뒤 공백 넷은 한글 두 배 폭에서 `쓰지 말 것:`과 열을 맞춘다.
         */
        private String render() {
            return "  쓰지 말 것: " + avoid + "\n  이렇게:    " + prefer;
        }
    }

    /**
     * 한 렌즈가 채우는 예문 여덟 쌍. 순서는 {@link #TEMPLATE}의 규칙 순서와 같다.
     */
    record Examples(
            Example politeEnding,
            Example actor,
            Example passiveVoice,
            Example derivedNoun,
            Example oneThought,
            Example callByName,
            Example nameWhatIsMissing,
            Example tense
    ) {
    }
}
