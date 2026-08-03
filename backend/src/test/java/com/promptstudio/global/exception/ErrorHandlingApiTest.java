package com.promptstudio.global.exception;

import com.promptstudio.support.DatabaseTest;
import com.promptstudio.support.FakeAiConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring이 상태 코드를 이미 아는 표준 웹 예외가 그 상태 코드 그대로 나가는지 확인한다.
 *
 * <p>{@link GlobalExceptionHandler}에 {@code @ExceptionHandler(Exception.class)} catch-all이 있고
 * {@code ErrorResponse} 가드가 없다. 아래 예외들은 전부 {@code ErrorResponse}를 구현하므로
 * catch-all이 먼저 잡아 500으로 바꿀 수 있다. 도메인 예외는 각자 전용 핸들러가 있어 여기 해당하지 않고,
 * 그래서 기존 테스트 어디에서도 이 경로가 검증되지 않는다.
 *
 * <p>{@code print()}를 붙여 둔 이유는 상태 코드뿐 아니라 <b>응답 본문 형태</b>도 계약이기 때문이다.
 * catch-all에 가드를 넣으면 본문이 {@code ApiErrorResponse}({@code code}/{@code message})에서
 * 스프링 기본 오류 본문({@code timestamp}/{@code status}/{@code error}/{@code path})으로 바뀐다.
 * 컨트롤러의 {@code @Schema(implementation = ApiErrorResponse.class)} 선언이 그 순간 거짓이 되므로,
 * 고치기 전과 후의 본문을 눈으로 비교할 수 있어야 한다.
 */
@AutoConfigureMockMvc
@Import(FakeAiConfiguration.class)
class ErrorHandlingApiTest extends DatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 매핑되지_않은_경로는_404를_반환한다() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void 지원하지_않는_메서드는_405를_반환한다() throws Exception {
        // /api/problems 는 GET만 매핑되어 있다.
        mockMvc.perform(delete("/api/problems"))
                .andDo(print())
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void 본문이_깨진_JSON이면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/attempts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"problemId\": "))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void 지원하지_않는_컨텐츠_타입이면_415를_반환한다() throws Exception {
        mockMvc.perform(post("/api/attempts")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("problemId=1"))
                .andDo(print())
                .andExpect(status().isUnsupportedMediaType());
    }
}
