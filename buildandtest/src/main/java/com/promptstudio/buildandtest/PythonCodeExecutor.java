package com.promptstudio.buildandtest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * python으로 문법 검사 후 pytest(테스트가 있으면) 또는 main 스크립트를 실행한다.
 *
 * <p>단계 구조와 판정 규칙은 {@link ProcessCodeExecutor}와 같은 모양을 유지한다. 특히 문법 오류를
 * 실행 전에 py_compile로 걸러 COMPILE_ERROR로 판정한다 — 파이썬은 문법 오류도 실행 시점에
 * 드러나지만, 백엔드·프론트·릴레이 점수 계산이 기대는 상태 모델(컴파일/테스트/런타임 구분)을
 * 언어와 무관하게 보존하기 위해서다. 케이스 목록은 pytest의 JUnit XML 리포트에서 읽는다 —
 * 자바 워커와 같은 포맷이라 {@link JUnitReportParser}를 그대로 쓴다.
 *
 * <p>메모리 상한은 여기서 걸지 않는다. 자바의 -Xmx에 해당하는 인터프리터 스위치가 없어
 * 컨테이너 메모리 제한(compose)이 그 역할을 대신한다. 나머지 격리 규칙(환경변수 차단, 타임아웃 후
 * 프로세스 트리 강제 종료, 출력 상한, 실행별 임시 디렉토리)은 {@link ProcessRunner}가 공유한다.
 */
@Component
@ConditionalOnProperty(name = "worker.language", havingValue = "python")
public class PythonCodeExecutor implements CodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(PythonCodeExecutor.class);

    private static final String PREFERRED_MAIN_FILE = "main.py";
    private static final String MAIN_BLOCK = "__main__";

    /** pytest 종료 코드. 0은 전원 통과, 1은 테스트 실패, 5는 테스트를 못 찾은 경우다. */
    private static final int PYTEST_EXIT_TESTS_FAILED = 1;

    /** {@link JUnitReportParser}가 읽는 TEST-*.xml 규약에 맞춘 리포트 파일명. */
    private static final String REPORT_FILE = "TEST-pytest.xml";

    private final ProcessRunner processRunner;

    private final String pythonBinary;
    private final long compileTimeoutSeconds;
    private final long runTimeoutSeconds;
    private final long testTimeoutSeconds;

    public PythonCodeExecutor(
            @Value("${worker.compile-timeout-seconds:30}") long compileTimeoutSeconds,
            @Value("${worker.run-timeout-seconds:10}") long runTimeoutSeconds,
            @Value("${worker.test-timeout-seconds:30}") long testTimeoutSeconds,
            @Value("${worker.max-output-bytes:65536}") int maxOutputBytes,
            @Value("${worker.python-binary:python3}") String pythonBinary
    ) {
        this.compileTimeoutSeconds = compileTimeoutSeconds;
        this.runTimeoutSeconds = runTimeoutSeconds;
        this.testTimeoutSeconds = testTimeoutSeconds;
        this.processRunner = new ProcessRunner(maxOutputBytes);
        this.pythonBinary = pythonBinary;
    }

    @Override
    public RunOutcome execute(
            List<RunRequestMessage.RunFileMessage> files,
            List<RunRequestMessage.RunFileMessage> testFiles
    ) {
        long startedAt = System.nanoTime();

        if (files == null || files.isEmpty()) {
            return RunOutcome.failure(RunStatus.COMPILE_ERROR, "실행할 파일이 없습니다.", elapsedMillis(startedAt));
        }

        try (Workspace workspace = Workspace.create()) {
            return runIn(workspace, files, testFiles, startedAt);
        } catch (IllegalArgumentException exception) {
            // 경로 검증 실패. 워커 장애가 아니라 잘못된 요청이므로 그대로 알린다.
            log.warn("[RUN] 잘못된 파일 경로 | {}", exception.getMessage());

            return RunOutcome.failure(RunStatus.RUNNER_ERROR, exception.getMessage(), elapsedMillis(startedAt));
        } catch (IOException | ExecutionException exception) {
            log.error("[RUN] 실행 준비 중 오류", exception);

            return RunOutcome.failure(RunStatus.RUNNER_ERROR,
                    "워커에서 오류가 발생했습니다: " + exception.getMessage(), elapsedMillis(startedAt));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            return RunOutcome.failure(RunStatus.RUNNER_ERROR, "실행이 중단되었습니다.", elapsedMillis(startedAt));
        }
    }

    private RunOutcome runIn(
            Workspace workspace,
            List<RunRequestMessage.RunFileMessage> files,
            List<RunRequestMessage.RunFileMessage> testFiles,
            long startedAt
    ) throws IOException, InterruptedException, ExecutionException {
        Workspace.Sources sources = workspace.writeFiles(files, testFiles, ".py");

        if (sources.main().isEmpty()) {
            return RunOutcome.failure(RunStatus.COMPILE_ERROR, ".py 파일이 없습니다.", elapsedMillis(startedAt));
        }

        // 문법 검사는 제출 파일만 본다. 테스트 파일의 문법 오류는 출제 실수라 사용자의
        // COMPILE_ERROR가 아니라 아래 pytest 수집 실패(RUNNER_ERROR)로 드러나야 한다.
        List<String> syntaxCheck = new ArrayList<>(List.of(pythonBinary, "-X", "utf8", "-m", "py_compile"));
        syntaxCheck.addAll(sources.main());

        ProcessRunner.Outcome syntax =
                processRunner.run(workspace.root(), syntaxCheck, compileTimeoutSeconds, passThroughEnvironment());

        if (syntax.timedOut()) {
            return RunOutcome.failure(RunStatus.TIMEOUT,
                    "문법 검사가 " + compileTimeoutSeconds + "초를 초과했습니다.", elapsedMillis(startedAt));
        }

        if (syntax.exitCode() != 0) {
            return new RunOutcome(RunStatus.COMPILE_ERROR, syntax.exitCode(),
                    syntax.stdout(), syntax.stderr(), elapsedMillis(startedAt));
        }

        if (sources.hasTests()) {
            return runTests(workspace, sources, startedAt);
        }

        return runMain(workspace, sources, startedAt);
    }

    /**
     * pytest를 별도 프로세스로 띄워 테스트 파일들을 실행한다. 대상 파일을 명시해서 넘긴다 —
     * 디렉토리 스캔에 맡기면 conftest.py 같은 보조 파일만 있을 때 조용히 0건 수집으로 통과한다.
     */
    private RunOutcome runTests(Workspace workspace, Workspace.Sources sources, long startedAt)
            throws IOException, InterruptedException, ExecutionException {
        List<String> testTargets = sources.test().stream().filter(PythonCodeExecutor::isTestFile).toList();

        // 자바 워커의 --fail-if-no-tests와 같은 정신 — 테스트가 없는데 통과로 판정하면 안 된다.
        if (testTargets.isEmpty()) {
            return RunOutcome.failure(RunStatus.RUNNER_ERROR,
                    "실행할 테스트 파일(test_*.py)이 없습니다. 문제의 테스트 구성을 확인하세요.",
                    elapsedMillis(startedAt));
        }

        // 리포트 디렉터리는 pytest가 만들어 주지 않으므로 여기서 만든다.
        Files.createDirectories(workspace.reportsDir());

        List<String> command = new ArrayList<>(List.of(
                pythonBinary,
                "-X", "utf8",
                "-m", "pytest",
                // 케이스별 결과는 XML에서 읽는다. stdout은 사람용이고 출력 상한에 잘린다(자바 워커와 동일).
                "--junit-xml=" + workspace.reportsDir().resolve(REPORT_FILE),
                // 터미널 종류에 따라 출력이 달라지지 않게 색을 끈다.
                "--color=no",
                // 캐시 디렉터리(.pytest_cache)를 남기지 않는다. 실행마다 새 임시 디렉토리라 쓸모가 없다.
                "-p", "no:cacheprovider"
        ));
        command.addAll(testTargets);

        ProcessRunner.Outcome execution =
                processRunner.run(workspace.root(), command, testTimeoutSeconds, passThroughEnvironment());

        if (execution.timedOut()) {
            // 강제 종료된 러너의 리포트는 신뢰할 수 없다. 끝까지 돈 케이스만 담긴 목록을 내보내면
            // "일부는 통과했다"로 읽히므로 케이스를 싣지 않는다.
            return new RunOutcome(RunStatus.TIMEOUT, null, execution.stdout(),
                    "테스트 실행이 " + testTimeoutSeconds + "초를 초과해 강제 종료했습니다.", elapsedMillis(startedAt));
        }

        // 판정은 종료 코드가 진실이고 케이스 목록은 그것을 설명하는 보조 자료다.
        // 리포트가 없거나 깨져도 status는 그대로 두고 목록만 비운다.
        return new RunOutcome(testStatus(execution.exitCode()), execution.exitCode(),
                execution.stdout(), execution.stderr(), elapsedMillis(startedAt),
                JUnitReportParser.parse(workspace.reportsDir()));
    }

    private RunOutcome runMain(Workspace workspace, Workspace.Sources sources, long startedAt)
            throws IOException, InterruptedException, ExecutionException {
        Optional<String> mainScript = findMainScript(workspace.root(), sources.main());

        if (mainScript.isEmpty()) {
            return RunOutcome.failure(RunStatus.RUNTIME_ERROR,
                    "main.py 또는 __main__ 블록이 있는 파일을 찾을 수 없습니다.", elapsedMillis(startedAt));
        }

        ProcessRunner.Outcome execution = processRunner.run(
                workspace.root(),
                List.of(pythonBinary, "-X", "utf8", mainScript.get()),
                runTimeoutSeconds,
                passThroughEnvironment()
        );

        if (execution.timedOut()) {
            return new RunOutcome(RunStatus.TIMEOUT, null, execution.stdout(),
                    "실행이 " + runTimeoutSeconds + "초를 초과해 강제 종료했습니다.", elapsedMillis(startedAt));
        }

        RunStatus status = execution.exitCode() == 0 ? RunStatus.SUCCEEDED : RunStatus.RUNTIME_ERROR;

        return new RunOutcome(status, execution.exitCode(), execution.stdout(), execution.stderr(),
                elapsedMillis(startedAt));
    }

    private RunStatus testStatus(int exitCode) {
        if (exitCode == 0) {
            return RunStatus.SUCCEEDED;
        }

        // 5(수집 0건)·2(수집 오류) 등은 사용자 코드가 아니라 문제·워커 구성의 잘못이다.
        return exitCode == PYTEST_EXIT_TESTS_FAILED ? RunStatus.TEST_FAILED : RunStatus.RUNNER_ERROR;
    }

    /** pytest의 기본 수집 규칙과 같은 파일명 규약. */
    private static boolean isTestFile(String path) {
        String fileName = Path.of(path).getFileName().toString();

        return fileName.startsWith("test_") || fileName.endsWith("_test.py");
    }

    /**
     * 실행할 스크립트를 고른다. main.py를 우선하고, 없으면 __main__ 블록이 있는 파일을
     * 정렬 순서로 골라 실행이 재현 가능하게 한다({@link ProcessCodeExecutor#findMainClass}와 같은 정신).
     */
    private Optional<String> findMainScript(Path root, List<String> pythonSources) throws IOException {
        List<String> candidates = new ArrayList<>();

        for (String source : pythonSources) {
            String fileName = Path.of(source).getFileName().toString();

            if (fileName.equals(PREFERRED_MAIN_FILE)
                    || Files.readString(root.resolve(source), StandardCharsets.UTF_8).contains(MAIN_BLOCK)) {
                candidates.add(source);
            }
        }

        return candidates.stream()
                .min(java.util.Comparator
                        .comparing((String path) -> !Path.of(path).getFileName().toString().equals(PREFERRED_MAIN_FILE))
                        .thenComparing(java.util.Comparator.naturalOrder()));
    }

    /**
     * 자식에게 다시 넣어 줄 최소 환경변수. 윈도우의 CPython은 SYSTEMROOT 없이는 난수 초기화가
     * 실패해 기동조차 못 한다 — 리눅스 컨테이너에는 이 변수가 없으므로 빈 맵과 같다.
     */
    private Map<String, String> passThroughEnvironment() {
        Map<String, String> environment = new HashMap<>();
        String systemRoot = System.getenv("SYSTEMROOT");

        if (systemRoot != null) {
            environment.put("SYSTEMROOT", systemRoot);
        }

        return environment;
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    @PreDestroy
    void shutdown() {
        processRunner.close();
    }
}
