package com.promptstudio.buildandtest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 콘솔 런처가 --reports-dir에 남기는 형태를 그대로 쓴다(속성 이름·CDATA 위치·time 단위).
 */
class JUnitReportParserTest {

    @TempDir
    Path reports;

    @Test
    void 통과와_실패를_케이스별로_읽는다() throws IOException {
        writeReport("TEST-junit-jupiter.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="JUnit Jupiter" tests="2" skipped="0" failures="1" errors="0" time="0.069">
                  <testcase name="통과하는_케이스()" classname="NoisyTest" time="0.03"/>
                  <testcase name="실패하는_케이스()" classname="NoisyTest" time="0.017">
                    <failure message="expected: &lt;XY&gt; but was: &lt;AB&gt;" type="org.opentest4j.AssertionFailedError"><![CDATA[org.opentest4j.AssertionFailedError: expected: <XY> but was: <AB>
                	at NoisyTest.실패하는_케이스(NoisyTest.java:8)]]></failure>
                  </testcase>
                </testsuite>
                """);

        List<RunCase> cases = JUnitReportParser.parse(reports);

        assertThat(cases).hasSize(2);
        assertThat(cases.getFirst().name()).isEqualTo("통과하는_케이스()");
        assertThat(cases.getFirst().className()).isEqualTo("NoisyTest");
        assertThat(cases.getFirst().status()).isEqualTo(RunCaseStatus.PASSED);
        assertThat(cases.getFirst().message()).isNull();
        // time은 초 단위 소수다.
        assertThat(cases.getFirst().durationMs()).isEqualTo(30L);

        assertThat(cases.get(1).status()).isEqualTo(RunCaseStatus.FAILED);
        assertThat(cases.get(1).message()).isEqualTo("expected: <XY> but was: <AB>");
        assertThat(cases.get(1).durationMs()).isEqualTo(17L);
    }

    @Test
    void error와_skipped를_실패와_구분한다() throws IOException {
        writeReport("TEST-junit-jupiter.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="JUnit Jupiter" tests="2">
                  <testcase name="예외로_죽는다()" classname="T" time="0.001">
                    <error message="java.lang.IllegalStateException: boom" type="java.lang.IllegalStateException"/>
                  </testcase>
                  <testcase name="건너뛴다()" classname="T" time="0">
                    <skipped message="disabled"/>
                  </testcase>
                </testsuite>
                """);

        List<RunCase> cases = JUnitReportParser.parse(reports);

        assertThat(cases.getFirst().status()).isEqualTo(RunCaseStatus.ERROR);
        assertThat(cases.getFirst().message()).isEqualTo("java.lang.IllegalStateException: boom");
        assertThat(cases.get(1).status()).isEqualTo(RunCaseStatus.SKIPPED);
        // 스킵은 실패가 아니므로 사유를 싣지 않는다.
        assertThat(cases.get(1).message()).isNull();
    }

    /**
     * 엔진마다 파일이 하나씩 생긴다. 특정 파일명을 가정하면 엔진 구성이 바뀔 때 조용히 빈 목록이 된다.
     */
    @Test
    void 엔진별_리포트를_파일명_순으로_모두_읽는다() throws IOException {
        writeReport("TEST-junit-jupiter.xml", """
                <testsuite name="JUnit Jupiter" tests="1">
                  <testcase name="jupiter()" classname="A" time="0.01"/>
                </testsuite>
                """);
        writeReport("TEST-junit-vintage.xml", """
                <testsuite name="JUnit Vintage" tests="1">
                  <testcase name="vintage()" classname="B" time="0.02"/>
                </testsuite>
                """);
        writeReport("무관한파일.txt", "리포트가 아니다");

        List<RunCase> cases = JUnitReportParser.parse(reports);

        assertThat(cases).extracting(RunCase::name).containsExactly("jupiter()", "vintage()");
    }

    @Test
    void 리포트_디렉터리가_없으면_빈_목록이다() {
        assertThat(JUnitReportParser.parse(reports.resolve("없는디렉터리"))).isEmpty();
        assertThat(JUnitReportParser.parse(null)).isEmpty();
    }

    /**
     * 파싱 실패가 실행 판정을 바꾸면 안 된다. 깨진 XML은 빈 목록으로 삼킨다.
     */
    @Test
    void 깨진_리포트는_빈_목록으로_삼킨다() throws IOException {
        writeReport("TEST-junit-jupiter.xml", "<testsuite><testcase name=\"닫히지 않은");

        assertThat(JUnitReportParser.parse(reports)).isEmpty();
    }

    /**
     * 큰 문자열 비교가 깨지면 메시지가 수십 KB가 되고 그대로 큐와 DB로 흘러간다.
     */
    @Test
    void 지나치게_긴_메시지는_잘라낸다() throws IOException {
        writeReport("TEST-junit-jupiter.xml", """
                <testsuite name="JUnit Jupiter" tests="1">
                  <testcase name="긴_메시지()" classname="T" time="0.01">
                    <failure message="%s" type="X"/>
                  </testcase>
                </testsuite>
                """.formatted("가".repeat(5_000)));

        String message = JUnitReportParser.parse(reports).getFirst().message();

        assertThat(message).hasSizeLessThan(3_000).endsWith("... (생략)");
    }

    /**
     * 리포트는 JUnit이 쓰지만 안에 담기는 이름·메시지는 제출된 코드가 좌우한다.
     * 외부 엔티티를 확장하지 않고 파싱을 포기해야 한다(파일 읽기·SSRF 방지).
     */
    @Test
    void 외부_엔티티를_선언한_리포트는_파싱하지_않는다() throws IOException {
        writeReport("TEST-junit-jupiter.xml", """
                <?xml version="1.0"?>
                <!DOCTYPE testsuite [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
                <testsuite name="JUnit Jupiter" tests="1">
                  <testcase name="&xxe;" classname="T" time="0.01"/>
                </testsuite>
                """);

        assertThat(JUnitReportParser.parse(reports)).isEmpty();
    }

    private void writeReport(String fileName, String content) throws IOException {
        Files.writeString(reports.resolve(fileName), content, StandardCharsets.UTF_8);
    }
}
