package com.promptstudio.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 요청 단위 추적 ID를 응답과 로그에 남긴다. 요청 본문, 인증 헤더, 쿠키는 기록하지 않는다.
 *
 * <p>실패뿐 아니라 <b>모든</b> 요청을 한 줄씩 남긴다. "에러가 났다는데 로그에 에러가 없다"는
 * 신고가 들어왔을 때 2xx로 처리됐는지, 느렸는지, 서버에 닿지도 않았는지를 여기서 가른다.</p>
 *
 * <p>필드는 메시지 문자열이 아니라 KeyValuePair로 싣는다. 문자열에 박으면 grep으로 한 줄을
 * 찾을 수는 있어도 거르고·묶고·정렬하는 게 안 된다. KVP는 자바 타입을 보존하므로
 * {@code status}가 JSON number로 나가 수치 비교가 그대로 된다.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_REQUEST_ID = "requestId";
    private static final String CATCH_ALL_PATTERN = "/**";

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    /**
     * static이 아니라 인스턴스 필드다 — static이면 스프링 테스트가 캐시한 컨텍스트 사이로 값이 샌다.
     */
    private final AtomicInteger inflight = new AtomicInteger();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // 추적 id는 서버가 만든다. 클라이언트가 정하게 두면 같은 값을 반복해 보내는 것만으로
        // 한 사용자의 모든 요청이 한 id로 뭉쳐 추적이 무의미해진다.
        String requestId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        int concurrent = inflight.incrementAndGet();
        boolean unhandled = false;

        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } catch (Exception exception) {
            unhandled = true;

            // 이 시점엔 컨테이너가 아직 500을 세팅하지 않아 getStatus()가 200이다 —
            // 거짓 상태 코드를 남기느니 status·durationMs를 싣지 않는다.
            requestFields(log.atError(), request)
                    .addKeyValue("inflight", concurrent)
                    .setCause(exception)
                    .log("Request failed");

            throw exception;
        } finally {
            if (!unhandled) {
                int status = response.getStatus();
                long durationMs = (System.nanoTime() - startedAt) / 1_000_000;

                requestFields(levelFor(request, status), request)
                        .addKeyValue("status", status)
                        .addKeyValue("durationMs", durationMs)
                        .addKeyValue("inflight", concurrent)
                        .log(messageFor(status));
            }

            inflight.decrementAndGet();
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    private LoggingEventBuilder requestFields(LoggingEventBuilder builder, HttpServletRequest request) {
        LoggingEventBuilder event = builder.addKeyValue("method", request.getMethod());
        String route = route(request);

        // 핸들러에 도달하지 못한 요청(시큐리티가 끊는 401·403, 정적 404)은 route를 아예 싣지 않는다.
        // null을 넣으면 콘솔에 route="null"이 찍히고, path로 폴백하면 /api/attempts/42 같은 값이
        // route 집계에 섞여 route를 만든 이유가 사라진다.
        if (route != null) {
            event = event.addKeyValue("route", route);
        }

        return event.addKeyValue("path", request.getRequestURI());
    }

    private String route(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);

        // 정적 리소스 매핑이 /**로 먼저 매칭한 뒤 404를 던지므로 매칭 패턴이 남아 있다.
        // 엔드포인트를 가리키는 값이 아니라 "핸들러 미도달"이라 없는 것으로 다룬다.
        if (!(pattern instanceof String route) || CATCH_ALL_PATTERN.equals(route)) {
            return null;
        }

        return route;
    }

    private LoggingEventBuilder levelFor(HttpServletRequest request, int status) {
        if (status >= 500) {
            return log.atError();
        }

        if (status >= 400) {
            return isAnonymousSessionCheck(request, status) ? log.atInfo() : log.atWarn();
        }

        return log.atInfo();
    }

    private String messageFor(int status) {
        if (status >= 500) {
            return "Request failed";
        }

        return status >= 400 ? "Request rejected" : "Request";
    }

    /**
     * SPA의 로그인 상태 확인은 비로그인일 때 401이 정상 응답이라 경고로 올리지 않는다.
     */
    private boolean isAnonymousSessionCheck(HttpServletRequest request, int status) {
        return status == HttpServletResponse.SC_UNAUTHORIZED
                && "GET".equals(request.getMethod())
                && "/api/me".equals(request.getRequestURI());
    }
}
