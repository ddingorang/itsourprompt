package com.promptstudio.ai;

/**
 * pattern 피드백이 이름을 붙일 때 쓰는 어휘. aihero.dev의 AI Coding Dictionary
 * (<a href="https://aicodingdictionary.com">aicodingdictionary.com</a>)의 <b>Patterns of Work</b> 절
 * 열한 개를 싣는다.
 *
 * <p>영어 원문을 복제하지 않는다 — 그 저장소에 LICENSE가 없다. 용어명은 업계 말이라 그대로 쓰고
 * 뜻풀이는 한 줄로 잘라 우리 문장으로 다시 썼다.
 *
 * <p><b>처음에는 69개 전부를 실었다가 줄였다.</b> 실제 호출에서 모델이 두 번 다 어휘를 하나로
 * 무너뜨렸는데, 한 번은 {@code primary source}였다 — 그건 원본 자체를 가리키는 명사이지 일하는
 * 방식이 아니다. 나머지 쉰여덟 개는 대부분 그런 기계 부품 이름({@code token}·{@code context window}·
 * {@code prefix cache})이라 애초에 이름이 될 수 없었는데, 목록에 있다는 것만으로 모델을 끌어당겼다.
 *
 * <p>지시로 막는 대신 <b>목록에서 빼서</b> 막는다. 쓸 수 없는 것을 주고 쓰지 말라고 하는 것보다
 * 주지 않는 쪽이 안정적이다. 입력 토큰도 3,000에서 600 남짓으로 준다.
 */
final class AiCodingDictionary {

    private static final String TERMS = """
            human-in-the-loop | 세션이 도는 동안 사람이 옆에 붙어 읽고 방향을 바꾸는 방식
            AFK | 세션을 걸어 두고 자리를 뜨는 방식. 에이전트가 혼자 끝까지 간다
            automated check | 환경이 스스로 돌리는 점검. 테스트·타입·린트처럼 통과/실패만 낸다
            automated review | 에이전트가 다른 에이전트의 결과를 읽고 판단하는 것. 통과/실패가 아니라 의견이 나온다
            human review | 사람이 바뀐 코드를 읽고 판단하는 것. diff를 읽어야 하고 요약을 읽는 건 아니다
            vibe coding | AI가 낸 코드를 읽지 않고 받아들이는 방식. diff를 안 열고 동작만 본다
            design concept | 무엇을 만드는지에 대해 사람과 AI가 공유한 그림
            grilling | design concept을 세우는 기법. AI가 한 번에 하나씩 되물어 사람에게서 결정을 끌어낸다
            prototyping | 말로는 안 잡힐 때 거칠게 한 번 만들어 놓고 그것을 놓고 이야기하는 것
            DX | 개발자 경험. 사람이 좋은 일을 하기 쉽게 코드베이스와 도구가 갖춰진 정도
            AX | 에이전트 경험. 에이전트가 좋은 일을 하기 쉽게 환경이 갖춰진 정도 — 자동 점검, 구조, 거저 들어오는 컨텍스트
            """;

    private AiCodingDictionary() {
    }

    static String terms() {
        return TERMS;
    }
}
