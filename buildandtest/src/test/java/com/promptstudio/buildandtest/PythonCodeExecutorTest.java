package com.promptstudio.buildandtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 로컬에 python이 없으면 전부, pytest가 없으면 테스트 실행 케이스만 건너뛴다.
 * 워커 이미지(Dockerfile.python)에는 둘 다 구워져 있으므로 CI 컨테이너에서는 전부 돈다.
 * 인터프리터를 지정하려면 -Dworker.python-binary=<경로>로 재정의한다(build.gradle이 전달).
 */
class PythonCodeExecutorTest {

    private static final long COMPILE_TIMEOUT_SECONDS = 60;
    private static final long RUN_TIMEOUT_SECONDS = 5;
    private static final long TEST_TIMEOUT_SECONDS = 60;

    private static final String BINARY = resolveBinary();
    private static final boolean PYTEST_AVAILABLE = pytestAvailable();

    /** 채점용 테스트 자리를 대신한다. calculator.add가 있어야 통과한다. */
    private static final String TEST_SOURCE = """
            from calculator import add


            def test_두_수를_더한다():
                assert add(2, 3) == 5
            """;

    private final PythonCodeExecutor executor = new PythonCodeExecutor(
            COMPILE_TIMEOUT_SECONDS, RUN_TIMEOUT_SECONDS, TEST_TIMEOUT_SECONDS, 65536, BINARY);

    @BeforeEach
    void requiresPython() {
        assumeTrue(BINARY != null, "python 인터프리터가 없어 건너뜁니다.");
    }

    @Test
    void 정상_코드는_성공하고_표준출력을_돌려준다() {
        RunOutcome outcome = runWithoutTests(file("src/main/python/main.py", """
                print("Hello World")
                """));

        assertThat(outcome.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(outcome.exitCode()).isZero();
        assertThat(outcome.stdout()).contains("Hello World");
    }

    @Test
    void 한국어_출력이_깨지지_않는다() {
        RunOutcome outcome = runWithoutTests(file("src/main/python/main.py", """
                print("환영합니다")
                """));

        assertThat(outcome.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(outcome.stdout()).contains("환영합니다");
    }

    @Test
    void 구문_오류는_COMPILE_ERROR와_인터프리터_메시지를_돌려준다() {
        RunOutcome outcome = runWithoutTests(file("src/main/python/main.py", """
                def broken(
                print("끝나지 않는 괄호")
                """));

        assertThat(outcome.status()).isEqualTo(RunStatus.COMPILE_ERROR);
        assertThat(outcome.stderr()).contains("SyntaxError");
    }

    @Test
    void 실행_중_예외는_RUNTIME_ERROR다() {
        RunOutcome outcome = runWithoutTests(file("src/main/python/main.py", """
                raise RuntimeError("의도된 실패")
                """));

        assertThat(outcome.status()).isEqualTo(RunStatus.RUNTIME_ERROR);
        assertThat(outcome.stderr()).contains("의도된 실패");
    }

    @Test
    void 시간을_초과하면_TIMEOUT으로_강제_종료한다() {
        RunOutcome outcome = runWithoutTests(file("src/main/python/main.py", """
                while True:
                    pass
                """));

        assertThat(outcome.status()).isEqualTo(RunStatus.TIMEOUT);
    }

    @Test
    void main으로_실행할_파일이_없으면_RUNTIME_ERROR다() {
        RunOutcome outcome = runWithoutTests(file("src/main/python/helper.py", """
                def add(a, b):
                    return a + b
                """));

        assertThat(outcome.status()).isEqualTo(RunStatus.RUNTIME_ERROR);
        assertThat(outcome.stderr()).contains("main.py");
    }

    @Test
    void py_파일이_없으면_COMPILE_ERROR다() {
        RunOutcome outcome = runWithoutTests(file("README.md", "# 파이썬 파일이 아님"));

        assertThat(outcome.status()).isEqualTo(RunStatus.COMPILE_ERROR);
    }

    @Test
    void 테스트가_전부_통과하면_SUCCEEDED와_케이스_목록을_돌려준다() {
        assumeTrue(PYTEST_AVAILABLE, "pytest가 없어 건너뜁니다.");

        RunOutcome outcome = executor.execute(
                List.of(file("src/main/python/calculator.py", """
                        def add(a, b):
                            return a + b
                        """)),
                List.of(file("src/test/python/test_calculator.py", TEST_SOURCE),
                        file("src/test/python/conftest.py", CONFTEST))
        );

        assertThat(outcome.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(outcome.cases()).singleElement().satisfies(testCase -> {
            assertThat(testCase.name()).contains("두_수를_더한다");
            assertThat(testCase.status()).isEqualTo(RunCaseStatus.PASSED);
        });
    }

    @Test
    void 테스트가_실패하면_TEST_FAILED와_실패_사유를_돌려준다() {
        assumeTrue(PYTEST_AVAILABLE, "pytest가 없어 건너뜁니다.");

        RunOutcome outcome = executor.execute(
                List.of(file("src/main/python/calculator.py", """
                        def add(a, b):
                            return a - b
                        """)),
                List.of(file("src/test/python/test_calculator.py", TEST_SOURCE),
                        file("src/test/python/conftest.py", CONFTEST))
        );

        assertThat(outcome.status()).isEqualTo(RunStatus.TEST_FAILED);
        assertThat(outcome.cases()).singleElement().satisfies(testCase -> {
            assertThat(testCase.status()).isEqualTo(RunCaseStatus.FAILED);
            assertThat(testCase.message()).isNotBlank();
        });
    }

    @Test
    void 테스트_파일이_보조_파일뿐이면_RUNNER_ERROR다() {
        RunOutcome outcome = executor.execute(
                List.of(file("src/main/python/calculator.py", """
                        def add(a, b):
                            return a + b
                        """)),
                List.of(file("src/test/python/conftest.py", CONFTEST))
        );

        assertThat(outcome.status()).isEqualTo(RunStatus.RUNNER_ERROR);
    }

    private static final String CONFTEST = """
            import sys
            from pathlib import Path

            sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "main" / "python"))
            """;

    private RunOutcome runWithoutTests(RunRequestMessage.RunFileMessage file) {
        return executor.execute(List.of(file), List.of());
    }

    private RunRequestMessage.RunFileMessage file(String path, String content) {
        return new RunRequestMessage.RunFileMessage(path, content);
    }

    private static String resolveBinary() {
        String override = System.getProperty("worker.python-binary");

        if (override != null && !override.isBlank()) {
            return override;
        }

        for (String candidate : List.of("python3", "python")) {
            try {
                Process probe = new ProcessBuilder(candidate, "--version").start();

                if (probe.waitFor() == 0) {
                    return candidate;
                }
            } catch (Exception ignored) {
                // 다음 후보를 시도한다.
            }
        }

        return null;
    }

    private static boolean pytestAvailable() {
        if (BINARY == null) {
            return false;
        }

        try {
            return new ProcessBuilder(BINARY, "-m", "pytest", "--version").start().waitFor() == 0;
        } catch (Exception exception) {
            return false;
        }
    }
}
