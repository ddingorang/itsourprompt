package com.promptstudio.global.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import com.promptstudio.global.security.AppUserDetails;
import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.repository.ProblemRepository;
import com.promptstudio.support.DatabaseTest;
import com.promptstudio.support.FakeAiConfiguration;
import com.promptstudio.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 액세스 로그가 어떤 필드를 어떤 타입으로 싣는지 확인한다.
 *
 * <p>출력 문자열이 아니라 logback {@link ListAppender}로 {@code ILoggingEvent}를 직접 본다.
 * {@code logging.pattern.console}은 테스트 application.yml이 main을 가려 적용되지 않으므로,
 * 설정과 무관하게 필터만 검증할 수 있는 이음매가 여기뿐이다.</p>
 */
@AutoConfigureMockMvc
@Import(FakeAiConfiguration.class)
class AccessLogApiTest extends DatabaseTest {

    private static final ProblemFile SKELETON = new ProblemFile("src/main/java/Main.java", "class Main {}");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FakeAiConfiguration.FakeCodeGenerator codeGenerator;

    private ch.qos.logback.classic.Logger filterLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void 액세스_로그를_수집한다() {
        filterLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(RequestLogFilter.class);
        appender = new ListAppender<>();
        appender.start();
        filterLogger.addAppender(appender);
    }

    @AfterEach
    void 수집을_멈춘다() {
        filterLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void 정상_요청도_한_줄_남는다() throws Exception {
        mockMvc.perform(get("/api/problems"))
                .andExpect(status().isOk());

        ILoggingEvent event = 액세스_로그();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getMessage()).isEqualTo("Request");
        assertThat(kvp(event, "method")).isEqualTo("GET");
        assertThat(kvp(event, "path")).isEqualTo("/api/problems");
        // status가 문자열이면 파일 JSON에서 .app.status >= 500이 성립하지 않는다.
        assertThat(kvp(event, "status")).isEqualTo(200);
        assertThat(kvp(event, "durationMs")).isInstanceOf(Long.class);
        assertThat(kvp(event, "inflight")).isInstanceOf(Integer.class);
    }

    @Test
    void 경로_변수는_route에_템플릿으로_남는다() throws Exception {
        mockMvc.perform(get("/api/attempts/{id}", 999_999L))
                .andExpect(status().isNotFound());

        ILoggingEvent event = 액세스_로그();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getMessage()).isEqualTo("Request rejected");
        // path는 어템프트 id가 박혀 있어 엔드포인트별 집계가 안 된다. route가 그 자리를 메운다.
        assertThat(kvp(event, "route")).isEqualTo("/api/attempts/{id}");
        assertThat(kvp(event, "path")).isEqualTo("/api/attempts/999999");
    }

    @Test
    void 핸들러에_도달하지_못한_요청은_route를_싣지_않는다() throws Exception {
        // 정적 리소스 매핑이 /**로 먼저 매칭해 BEST_MATCHING_PATTERN에 값이 남는 것을 실측했다.
        // route == null이 "핸들러 미도달"을 뜻하려면 그 값을 없는 것으로 다뤄야 한다.
        mockMvc.perform(get("/api/does-not-exist"));

        assertThat(kvp(액세스_로그(), "route")).isNull();
    }

    @Test
    void 비로그인_401에도_요청_id가_실리고_경고로_올리지_않는다() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(result.getResponse().getHeader(RequestLogFilter.REQUEST_ID_HEADER)).isNotBlank();

        ILoggingEvent event = 액세스_로그();
        // SPA의 로그인 상태 확인은 비로그인일 때 401이 정상 응답이다.
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getMessage()).isEqualTo("Request rejected");
    }

    @Test
    void 서버_오류에도_요청_id가_실린다() throws Exception {
        Long attemptId = 어템프트를_만든다();
        codeGenerator.failNextWith(new IllegalStateException("예상하지 못한 실패"));
        appender.list.clear();

        MvcResult result = mockMvc.perform(post("/api/attempts/{id}/turns", attemptId)
                        .with(소유자())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Hello 출력해줘\"}"))
                .andExpect(status().isInternalServerError())
                .andReturn();

        assertThat(result.getResponse().getHeader(RequestLogFilter.REQUEST_ID_HEADER)).isNotBlank();

        ILoggingEvent event = 액세스_로그();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getMessage()).isEqualTo("Request failed");
        assertThat(kvp(event, "status")).isEqualTo(500);
    }

    @Test
    void 클라이언트가_보낸_X_Request_Id는_무시된다() throws Exception {
        // 고정값을 계속 보내면 그 사용자의 모든 요청이 한 id로 뭉쳐 추적 id의 유일성이 깨진다.
        MvcResult result = mockMvc.perform(get("/api/problems")
                        .header(RequestLogFilter.REQUEST_ID_HEADER, "client-supplied-id"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader(RequestLogFilter.REQUEST_ID_HEADER))
                .isNotBlank()
                .isNotEqualTo("client-supplied-id");
    }

    private ILoggingEvent 액세스_로그() {
        assertThat(appender.list).hasSize(1);

        return appender.list.getFirst();
    }

    private Object kvp(ILoggingEvent event, String key) {
        List<KeyValuePair> pairs = event.getKeyValuePairs();

        if (pairs == null) {
            return null;
        }

        return pairs.stream()
                .filter(pair -> pair.key.equals(key))
                .map(pair -> pair.value)
                .findFirst()
                .orElse(null);
    }

    private Long 어템프트를_만든다() throws Exception {
        String response = mockMvc.perform(post("/api/attempts")
                        .with(소유자())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"problemId\":" + newProblem().id() + "}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.parse(response).read("$.id", Long.class);
    }

    private RequestPostProcessor 소유자() {
        return user(new AppUserDetails(userRepository.findById(ownerId).orElseThrow()));
    }

    private Problem newProblem() {
        return problemRepository.save(new Problem("hello-world", "Hello World 출력", "# Hello World 출력", List.of(SKELETON)));
    }
}
