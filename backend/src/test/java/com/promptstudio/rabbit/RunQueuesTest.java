package com.promptstudio.rabbit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunQueuesTest {

    @Test
    void java_요청은_java_큐로_라우팅된다() {
        assertThat(RunQueues.requestRoutingKey("java")).isEqualTo(RunQueues.REQUEST_QUEUE_JAVA);
    }

    /** 언어 필드 도입 전의 메시지는 null이며 java로 해석한다(계약 규칙). */
    @Test
    void 언어가_null이면_java_큐로_라우팅된다() {
        assertThat(RunQueues.requestRoutingKey(null)).isEqualTo(RunQueues.REQUEST_QUEUE_JAVA);
    }

    @Test
    void python_요청은_python_큐로_라우팅된다() {
        assertThat(RunQueues.requestRoutingKey("python")).isEqualTo(RunQueues.REQUEST_QUEUE_PYTHON);
    }

    /** 라우팅 없는 언어를 조용히 java 워커로 흘려보내면 그럴듯한 오채점이 된다 — 발행 시점에 터뜨린다. */
    @Test
    void 라우팅이_정의되지_않은_언어는_예외를_던진다() {
        assertThatThrownBy(() -> RunQueues.requestRoutingKey("cobol"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
