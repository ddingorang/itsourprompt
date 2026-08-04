package com.promptstudio.buildandtest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * JUnit 콘솔 런처가 {@code --reports-dir}에 남긴 XML을 케이스 목록으로 읽는다.
 *
 * <p><b>stdout을 파싱하지 않는 이유</b>: 콘솔 트리는 사람이 읽기 위한 출력이고 실행 출력의 <b>맨 끝</b>에
 * 나온다. 워커는 stdout을 앞에서부터 상한(64KB)까지만 보관하므로, 제출 코드가 출력을 조금 많이 하면
 * 트리가 통째로 잘려 나간다(실측: 테스트 2개 + 디버그 600줄 = 92KB, 앞 64KB 안에 트리 흔적 0건).
 * XML은 별도 파일이라 그 상한과 무관하고, 트리 테마·런처 버전이 바뀌어도 형태가 유지된다.
 *
 * <p>엔진마다 파일이 하나씩 생기므로({@code TEST-junit-jupiter.xml},
 * {@code TEST-junit-vintage.xml} 등) 디렉터리의 {@code TEST-*.xml}을 파일명 순으로 모두 읽는다.
 * 특정 파일명을 가정하면 엔진 구성이 바뀔 때 조용히 빈 목록이 된다.
 */
final class JUnitReportParser {

    /**
     * 실패 메시지 상한. 큰 문자열을 비교하는 단정이 깨지면 메시지가 수십 KB가 되고,
     * 그것이 그대로 메시지 큐와 DB 행으로 흘러간다.
     */
    private static final int MAX_MESSAGE_LENGTH = 2_000;

    private static final String REPORT_PREFIX = "TEST-";
    private static final String REPORT_SUFFIX = ".xml";

    private static final Logger log = LoggerFactory.getLogger(JUnitReportParser.class);

    private JUnitReportParser() {
    }

    /**
     * 리포트를 읽을 수 없으면 빈 목록을 돌려준다. 파싱 실패가 실행 판정을 바꾸면 안 되기 때문이다 —
     * 채점 결과는 종료 코드가 진실이고 케이스 목록은 그것을 설명하는 보조 자료다.
     */
    static List<RunCase> parse(Path reportsDirectory) {
        if (reportsDirectory == null || !Files.isDirectory(reportsDirectory)) {
            return List.of();
        }

        List<RunCase> cases = new ArrayList<>();

        try (Stream<Path> reports = Files.list(reportsDirectory)) {
            List<Path> ordered = reports
                    .filter(JUnitReportParser::isReport)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();

            for (Path report : ordered) {
                cases.addAll(parseReport(report));
            }
        } catch (IOException exception) {
            log.warn("[RUN] 리포트 디렉터리를 읽지 못했습니다 | {}", exception.getMessage());

            return List.of();
        }

        return cases;
    }

    private static boolean isReport(Path path) {
        String fileName = path.getFileName().toString();

        return fileName.startsWith(REPORT_PREFIX) && fileName.endsWith(REPORT_SUFFIX);
    }

    private static List<RunCase> parseReport(Path report) {
        List<RunCase> cases = new ArrayList<>();

        try {
            Document document = documentBuilder().parse(report.toFile());
            NodeList testcases = document.getElementsByTagName("testcase");

            for (int index = 0; index < testcases.getLength(); index++) {
                if (testcases.item(index) instanceof Element testcase) {
                    cases.add(toCase(testcase));
                }
            }
        } catch (ParserConfigurationException | SAXException | IOException exception) {
            log.warn("[RUN] 리포트를 파싱하지 못했습니다 | file={} | {}",
                    report.getFileName(), exception.getMessage());

            return List.of();
        }

        return cases;
    }

    /**
     * 리포트는 JUnit이 만든 파일이지만 그 안에 담기는 이름·메시지는 제출된 코드가 좌우한다.
     * 외부 엔티티와 DOCTYPE을 막아 파서가 파일·네트워크를 건드릴 여지를 없앤다.
     */
    private static DocumentBuilder documentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);

        return factory.newDocumentBuilder();
    }

    private static RunCase toCase(Element testcase) {
        Element outcome = firstChildOf(testcase, "failure", "error", "skipped");
        RunCaseStatus status = statusOf(outcome);

        return new RunCase(
                emptyToNull(testcase.getAttribute("classname")),
                testcase.getAttribute("name"),
                status,
                status == RunCaseStatus.PASSED || status == RunCaseStatus.SKIPPED ? null : messageOf(outcome),
                durationOf(testcase)
        );
    }

    private static RunCaseStatus statusOf(Element outcome) {
        if (outcome == null) {
            return RunCaseStatus.PASSED;
        }

        return switch (outcome.getTagName()) {
            case "failure" -> RunCaseStatus.FAILED;
            case "error" -> RunCaseStatus.ERROR;
            default -> RunCaseStatus.SKIPPED;
        };
    }

    /**
     * message 속성을 우선 쓴다. 그 안에 단정의 기대값·실제값이 한 줄로 들어 있어 사용자가 바로 읽을 수 있다.
     * 속성이 비어 있을 때만 본문(스택트레이스)의 첫 줄로 대체한다 — 전체 스택은 stdout에 이미 있다.
     */
    private static String messageOf(Element outcome) {
        String message = outcome.getAttribute("message");

        if (message.isBlank()) {
            String body = outcome.getTextContent();
            message = body == null ? "" : body.strip().lines().findFirst().orElse("");
        }

        if (message.isBlank()) {
            return null;
        }

        return message.length() > MAX_MESSAGE_LENGTH
                ? message.substring(0, MAX_MESSAGE_LENGTH) + "... (생략)"
                : message;
    }

    /** 리포트의 time은 초 단위 소수다. */
    private static Long durationOf(Element testcase) {
        String time = testcase.getAttribute("time");

        if (time.isBlank()) {
            return null;
        }

        try {
            return Math.round(Double.parseDouble(time) * 1_000);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Element firstChildOf(Element parent, String... tagNames) {
        NodeList children = parent.getChildNodes();

        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);

            if (!(child instanceof Element element)) {
                continue;
            }

            for (String tagName : tagNames) {
                if (tagName.equals(element.getTagName())) {
                    return element;
                }
            }
        }

        return null;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
