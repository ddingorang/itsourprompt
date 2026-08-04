package com.promptstudio.buildandtest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * javac로 컴파일하고 java로 실행한다. 워커 컨테이너 안에서 자식 프로세스로 돌린다.
 *
 * <p>채점용 테스트가 함께 오면 main 대신 JUnit 콘솔 런처로 테스트를 돌린다. 런처는 문제마다 바뀌지 않는
 * 고정 의존이라 이미지에 구워 두고(Dockerfile 참고) 경로만 주입받는다 — 매 실행 바뀌는 테스트 코드를
 * 메시지로 받는 것과 정반대 판단이다.
 *
 * <p>격리가 프로세스 경계뿐이라는 점을 전제로 다음 제약을 모두 적용한다: 환경변수 차단, 힙 상한,
 * 타임아웃 후 프로세스 트리 강제 종료, 출력 바이트 상한, 실행마다 새 임시 디렉토리.
 * 자식 프로세스는 워커의 네트워크 네임스페이스를 공유하므로 이것으로 충분하지 않다 —
 * 강화가 필요해지면 {@link CodeExecutor}를 일회용 컨테이너 구현으로 교체한다.
 */
@Component
public class ProcessCodeExecutor implements CodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProcessCodeExecutor.class);

    /** 클래스 선언이 없는 파일도 있으니 main 선언 자체를 찾는다. */
    private static final Pattern MAIN_METHOD =
            Pattern.compile("static\\s+(?:final\\s+)?void\\s+main\\s*\\(");
    private static final Pattern PACKAGE_DECLARATION =
            Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final String PREFERRED_MAIN_CLASS = "Main";

    /**
     * JUnit 콘솔 런처의 종료 코드. 0은 전원 통과, 1은 테스트 실패, 2는 테스트를 못 찾은 경우다.
     * 2는 사용자 잘못이 아니라 문제 설정이나 워커 설정이 잘못된 것이라 RUNNER_ERROR로 간다.
     */
    private static final int JUNIT_EXIT_TESTS_FAILED = 1;

    private final ExecutorService streamReaders = Executors.newVirtualThreadPerTaskExecutor();

    private final Path javacBinary;
    private final Path javaBinary;
    private final Path junitConsoleJar;
    private final long compileTimeoutSeconds;
    private final long runTimeoutSeconds;
    private final long testTimeoutSeconds;
    private final int maxOutputBytes;
    private final String heapLimit;

    public ProcessCodeExecutor(
            @Value("${worker.compile-timeout-seconds:30}") long compileTimeoutSeconds,
            @Value("${worker.run-timeout-seconds:10}") long runTimeoutSeconds,
            @Value("${worker.test-timeout-seconds:30}") long testTimeoutSeconds,
            @Value("${worker.max-output-bytes:65536}") int maxOutputBytes,
            @Value("${worker.heap-limit:256m}") String heapLimit,
            @Value("${worker.junit-console-jar}") String junitConsoleJar
    ) {
        this.compileTimeoutSeconds = compileTimeoutSeconds;
        this.runTimeoutSeconds = runTimeoutSeconds;
        this.testTimeoutSeconds = testTimeoutSeconds;
        this.maxOutputBytes = maxOutputBytes;
        this.heapLimit = heapLimit;
        this.junitConsoleJar = Path.of(junitConsoleJar);

        // 환경변수를 비우고 실행하므로 PATH에 의존할 수 없다. 절대 경로로 고정한다.
        Path javaBin = Path.of(System.getProperty("java.home"), "bin");
        this.javacBinary = javaBin.resolve("javac");
        this.javaBinary = javaBin.resolve("java");
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
        Workspace.Sources sources = workspace.writeFiles(files, testFiles);

        // 테스트만 있고 제출 코드가 없으면 채점할 대상이 없다.
        if (sources.main().isEmpty()) {
            return RunOutcome.failure(RunStatus.COMPILE_ERROR, ".java 파일이 없습니다.", elapsedMillis(startedAt));
        }

        ProcessOutcome compile = run(workspace.root(), compileCommand(workspace, sources), compileTimeoutSeconds);

        if (compile.timedOut()) {
            return RunOutcome.failure(RunStatus.TIMEOUT,
                    "컴파일이 " + compileTimeoutSeconds + "초를 초과했습니다.", elapsedMillis(startedAt));
        }

        if (compile.exitCode() != 0) {
            return new RunOutcome(RunStatus.COMPILE_ERROR, compile.exitCode(),
                    compile.stdout(), compile.stderr(), elapsedMillis(startedAt));
        }

        if (sources.hasTests()) {
            return runTests(workspace, startedAt);
        }

        Optional<String> mainClass = findMainClass(workspace.root(), sources.main());

        if (mainClass.isEmpty()) {
            return RunOutcome.failure(RunStatus.RUNTIME_ERROR,
                    "main 메서드를 가진 클래스를 찾을 수 없습니다.", elapsedMillis(startedAt));
        }

        ProcessOutcome execution = run(
                workspace.root(),
                List.of(
                        javaBinary.toString(),
                        "-Xmx" + heapLimit,
                        "-XX:ActiveProcessorCount=1",
                        // 환경변수를 비워 로케일이 없으므로 인코딩을 명시한다(한국어 출력 깨짐 방지).
                        "-Dfile.encoding=UTF-8",
                        "-Dstdout.encoding=UTF-8",
                        "-Dstderr.encoding=UTF-8",
                        "-cp", workspace.outputDir().toString(),
                        mainClass.get()
                ),
                runTimeoutSeconds
        );

        if (execution.timedOut()) {
            return new RunOutcome(RunStatus.TIMEOUT, null, execution.stdout(),
                    "실행이 " + runTimeoutSeconds + "초를 초과해 강제 종료했습니다.", elapsedMillis(startedAt));
        }

        RunStatus status = execution.exitCode() == 0 ? RunStatus.SUCCEEDED : RunStatus.RUNTIME_ERROR;

        return new RunOutcome(status, execution.exitCode(), execution.stdout(), execution.stderr(),
                elapsedMillis(startedAt));
    }

    /**
     * 테스트 소스는 JUnit API를 import하므로 런처 팻자를 컴파일 클래스패스에 올려야 한다.
     * 테스트가 없을 때는 올리지 않는다 — 제출 코드가 JUnit에 의존할 이유가 없다.
     */
    private List<String> compileCommand(Workspace workspace, Workspace.Sources sources) throws IOException {
        List<String> command = new ArrayList<>(List.of(
                javacBinary.toString(),
                "-encoding", "UTF-8",
                "-d", workspace.outputDir().toString()
        ));

        if (sources.hasTests()) {
            command.add("-cp");
            command.add(junitConsoleJar.toString());
        }

        command.add(workspace.writeSourceList(sources.all()));

        return command;
    }

    /**
     * JUnit 콘솔 런처를 별도 프로세스로 띄워 컴파일된 테스트를 전부 실행한다.
     *
     * <p>제출 코드는 런처와 같은 JVM에서 돌아간다. 제출 코드가 {@code System.exit()}을 부르면 런처째
     * 죽어서 그 종료 코드로 판정되므로, 테스트가 깨진 것과 구분되지 않을 수 있다.
     */
    private RunOutcome runTests(Workspace workspace, long startedAt)
            throws IOException, InterruptedException, ExecutionException {
        ProcessOutcome execution = run(
                workspace.root(),
                List.of(
                        javaBinary.toString(),
                        "-Xmx" + heapLimit,
                        "-XX:ActiveProcessorCount=1",
                        "-Dfile.encoding=UTF-8",
                        "-Dstdout.encoding=UTF-8",
                        "-Dstderr.encoding=UTF-8",
                        "-jar", junitConsoleJar.toString(),
                        "execute",
                        "--class-path", workspace.outputDir().toString(),
                        "--scan-class-path", workspace.outputDir().toString(),
                        "--disable-ansi-colors",
                        "--disable-banner",
                        // summary는 통과 시 개수만 내서 어떤 케이스를 통과했는지 알 수 없다.
                        // tree는 케이스 이름을 모두 나열하고 실패 사유를 그 자리에 붙여 준다.
                        "--details=tree",
                        // 테마를 고정한다. 터미널 종류에 따라 ascii로 떨어지면 출력이 환경마다 달라진다.
                        "--details-theme=unicode",
                        // 테스트를 하나도 못 찾으면 조용히 통과하지 않고 2번으로 실패한다.
                        "--fail-if-no-tests",
                        // 케이스별 결과는 이 XML에서 읽는다. stdout의 트리는 사람용이고 출력 상한에 잘린다.
                        "--reports-dir=" + workspace.reportsDir()
                ),
                testTimeoutSeconds,
                // 런처는 터미널 폭을 알아내려 stty를 실행한다. tty가 없는 컨테이너에서 stderr에
                // 오류가 찍히므로 폭을 직접 알려준다. 환경변수를 비운 뒤 이것만 다시 넣는다.
                Map.of("COLUMNS", "120")
        );

        if (execution.timedOut()) {
            // 강제 종료된 런처의 리포트는 신뢰할 수 없다. 끝까지 돈 케이스만 담긴 목록을 내보내면
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

    private RunStatus testStatus(int exitCode) {
        if (exitCode == 0) {
            return RunStatus.SUCCEEDED;
        }

        return exitCode == JUNIT_EXIT_TESTS_FAILED ? RunStatus.TEST_FAILED : RunStatus.RUNNER_ERROR;
    }

    /**
     * main 메서드를 선언한 소스에서 완전한 클래스명을 만든다. 여러 개면 Main을 우선하고,
     * 그래도 정해지지 않으면 정렬 순서로 결정해 실행이 재현 가능하게 한다.
     */
    private Optional<String> findMainClass(Path root, List<String> javaSources) throws IOException {
        List<String> candidates = new ArrayList<>();

        for (String source : javaSources) {
            String body = Files.readString(root.resolve(source), StandardCharsets.UTF_8);

            if (!MAIN_METHOD.matcher(body).find()) {
                continue;
            }

            String fileName = Path.of(source).getFileName().toString();
            String simpleName = fileName.substring(0, fileName.length() - ".java".length());
            Matcher packageMatcher = PACKAGE_DECLARATION.matcher(body);

            candidates.add(packageMatcher.find()
                    ? packageMatcher.group(1) + "." + simpleName
                    : simpleName);
        }

        return candidates.stream()
                .min(Comparator
                        .comparing((String name) -> !simpleNameOf(name).equals(PREFERRED_MAIN_CLASS))
                        .thenComparing(Comparator.naturalOrder()));
    }

    private String simpleNameOf(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');

        return lastDot < 0 ? qualifiedName : qualifiedName.substring(lastDot + 1);
    }

    private ProcessOutcome run(Path workingDirectory, List<String> command, long timeoutSeconds)
            throws IOException, InterruptedException, ExecutionException {
        return run(workingDirectory, command, timeoutSeconds, Map.of());
    }

    /**
     * @param environment 상속을 끊은 뒤 다시 넣을 환경변수. 꼭 필요한 것만 넣는다.
     */
    private ProcessOutcome run(
            Path workingDirectory,
            List<String> command,
            long timeoutSeconds,
            Map<String, String> environment
    ) throws IOException, InterruptedException, ExecutionException {
        ProcessBuilder builder = new ProcessBuilder(command).directory(workingDirectory.toFile());

        // 환경변수 상속을 끊는다. 다만 이것만으로 비밀값이 지켜지지는 않는다 —
        // 자식이 워커와 같은 uid라 /proc/1/environ으로 부모의 환경변수를 읽어낼 수 있다(실측 확인).
        // 그래서 컨테이너에 DB 자격증명을 아예 주지 않는다(docker-compose.yml 참고).
        builder.environment().clear();
        builder.environment().putAll(environment);

        Process process = builder.start();
        Future<String> stdout = streamReaders.submit(() -> readCapped(process.getInputStream()));
        Future<String> stderr = streamReaders.submit(() -> readCapped(process.getErrorStream()));

        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            destroyTree(process);

            return new ProcessOutcome(true, null, stdout.get(), stderr.get());
        }

        return new ProcessOutcome(false, process.exitValue(), stdout.get(), stderr.get());
    }

    /**
     * 손자 프로세스까지 함께 죽인다. 자식만 destroy하면 그것이 띄운 프로세스가 남아 워커에 누적된다.
     */
    private void destroyTree(Process process) throws InterruptedException {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        process.waitFor();
    }

    /**
     * 상한까지만 메모리에 담고 나머지는 읽어서 버린다. 계속 읽어야 자식이 파이프에 막히지 않고,
     * 버려야 무한 출력이 워커 메모리를 잠식하지 않는다.
     */
    private String readCapped(InputStream stream) throws IOException {
        ByteArrayOutputStream kept = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;

        try (InputStream in = stream) {
            while ((read = in.read(buffer)) != -1) {
                total += read;
                int room = maxOutputBytes - kept.size();

                if (room > 0) {
                    kept.write(buffer, 0, Math.min(room, read));
                }
            }
        }

        String text = kept.toString(StandardCharsets.UTF_8);

        return total > maxOutputBytes
                ? text + System.lineSeparator() + "... (출력이 " + maxOutputBytes + "바이트에서 절단되었습니다)"
                : text;
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    @PreDestroy
    void shutdown() {
        streamReaders.shutdownNow();
    }

    private record ProcessOutcome(boolean timedOut, Integer exitCode, String stdout, String stderr) {
    }
}
