package com.promptstudio.ai;

import com.promptstudio.ai.CarryLineSessions.Run;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 페이즈 하나가 어떤 실행 목록을 내는지 고정한다.
 *
 * <p>여기서 실호출은 일어나지 않는다 — 실행 목록이 틀리면 <b>돈이 나간 뒤에야</b> 알게 되므로
 * 목록 자체를 미리 잰다.
 */
class CarryLineSessionsTest {

    /**
     * S1·S2를 한 실행에 싣는 결합 페이즈. 팔을 따로 돌리면 Δ의 분모가 실행마다 달라져
     * 다른 시각·다른 레이트 리밋의 대조군을 비교하게 된다.
     */
    @Test
    void s1s2_페이즈는_대조군_하나와_s1a_s1b_s2a를_돌린다() {
        List<Run> runs = CarryLineSessions.runsFor("s1s2");

        assertThat(runs).extracting(Run::arm)
                .containsExactly(CarryLineSessions.CONTROL, "s1a", "s1b", "s2a");
        assertThat(runs).extracting(run -> run.session().name())
                .containsOnly("N");
        assertThat(runs.get(0).rule()).isNull();
    }

    @Test
    void 팔_페이즈는_그_팔과_같은_세션_대조군을_돌린다() {
        List<Run> runs = CarryLineSessions.runsFor("s3a");

        assertThat(runs).extracting(Run::arm).containsExactly(CarryLineSessions.CONTROL, "s3a");
        assertThat(runs).extracting(run -> run.session().name()).containsOnly("C");
    }

    @Test
    void 모르는_페이즈는_설정_오류로_죽는다() {
        assertThatThrownBy(() -> CarryLineSessions.runsFor("s9"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("s1s2");
    }
}
