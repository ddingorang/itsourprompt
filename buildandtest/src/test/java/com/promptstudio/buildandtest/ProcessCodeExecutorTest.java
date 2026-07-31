package com.promptstudio.buildandtest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessCodeExecutorTest {

    private static final long COMPILE_TIMEOUT_SECONDS = 60;
    private static final long RUN_TIMEOUT_SECONDS = 5;
    private static final long TEST_TIMEOUT_SECONDS = 60;
    private static final int MAX_OUTPUT_BYTES = 1024;

    /** build.gradle이 copyJunitConsole로 꺼내 둔 팻자 경로를 넘겨준다. */
    private static final String JUNIT_CONSOLE_JAR = System.getProperty("worker.junit-console-jar");

    /** 채점용 테스트 자리를 대신한다. Calculator.add가 있어야 컴파일된다. */
    private static final String TEST_SOURCE = """
            import org.junit.jupiter.api.Test;

            import static org.junit.jupiter.api.Assertions.assertEquals;

            class CalculatorTest {

                @Test
                void 두_수를_더한다() {
                    assertEquals(5, new Calculator().add(2, 3));
                }
            }
            """;

    private final ProcessCodeExecutor executor = new ProcessCodeExecutor(
            COMPILE_TIMEOUT_SECONDS, RUN_TIMEOUT_SECONDS, TEST_TIMEOUT_SECONDS,
            MAX_OUTPUT_BYTES, "256m", JUNIT_CONSOLE_JAR);

    /**
     * JUnit 리포트는 실패 스택이 붙어 1KB를 쉽게 넘기므로 운영과 같은 출력 상한을 쓴다.
     * {@link #executor}의 좁은 상한은 절단 동작 자체를 보는 테스트용이다.
     */
    private final ProcessCodeExecutor testRunner = new ProcessCodeExecutor(
            COMPILE_TIMEOUT_SECONDS, RUN_TIMEOUT_SECONDS, TEST_TIMEOUT_SECONDS,
            65536, "256m", JUNIT_CONSOLE_JAR);

    @Test
    void 정상_코드는_성공하고_표준출력을_돌려준다() {
        RunOutcome outcome = runWithoutTests(file("src/main/java/Main.java", """
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("Hello World");
                    }
                }
                """));

        assertThat(outcome.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(outcome.exitCode()).isZero();
        assertThat(outcome.stdout()).contains("Hello World");
    }

    @Test
    void 한국어_출력이_깨지지_않는다() {
        RunOutcome outcome = runWithoutTests(file("src/main/java/Main.java", """
                public class Main {
                    public static void main(String[] args) {
                        // 여기에 코드를 작성하세요.
                        System.out.println("환영합니다");
                    }
                }
                """));

        assertThat(outcome.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(outcome.stdout()).contains("환영합니다");
    }

    @Test
    void 구문_오류는_COMPILE_ERROR와_컴파일러_메시지를_돌려준다() {
        RunOutcome outcome = runWithoutTests(file("src/main/java/Main.java", """
                public class Main {
                    public static void main(String[] args) {
                        this is not java
                    }
                }
                """));

        assertThat(outcome.status()).isEqualTo(RunStatus.COMPILE_ERROR);
        assertThat(outcome.exitCode()).isNotZero();
        assertThat(outcome.stderr()).isNotBlank();
    }

    @Test
    void 예외를_던지면_RUNTIME_ERROR가_된다() {
        RunOutcome outcome = runWithoutTests(file("src/main/java/Main.java", """
                public class Main {
                    public static void main(String[] args) {
                        throw new IllegalStateException("펑");
                    }
                }
                """));

        assertThat(outcome.status()).isEqualTo(RunStatus.RUNTIME_ERROR);
        assertThat(outcome.exitCode()).isNotZero();
        assertThat(outcome.stderr()).contains("IllegalStateException");
    }

    @Test
    void 무한루프는_타임아웃으로_강제_종료된다() {
        RunOutcome outcome = runWithoutTests(file("src/main/java/Main.java", """
                public class Main {
                    public static void main(String[] args) {
                        while (true) {
                        }
                    }
                }
                """));

        assertThat(outcome.status()).isEqualTo(RunStatus.TIMEOUT);
        assertThat(outcome.exitCode()).isNull();
        // 타임아웃 상한을 넘겨 실제로 죽었는지 확인한다(무한정 매달려 있지 않았다는 뜻).
        assertThat(outcome.durationMs()).isLessThan(RUN_TIMEOUT_SECONDS * 4_000);
    }

    @Test
    void 무한_출력은_상한에서_절단된다() {
        RunOutcome outcome = runWithoutTests(file("src/main/java/Main.java", """
                public class Main {
                    public static void main(String[] args) {
                        for (int i = 0; i < 100_000; i++) {
                            System.out.println("네 번째 줄보다 훨씬 긴 출력을 반복해서 뱉는다");
                        }
                    }
                }
                """));

        assertThat(outcome.stdout()).contains("절단되었습니다");
        // 절단 안내 문구를 더한 길이 이상으로는 커지지 않는다.
        assertThat(outcome.stdout().getBytes()).hasSizeLessThan(MAX_OUTPUT_BYTES + 200);
    }

    @Test
    void 패키지가_있는_클래스도_실행된다() {
        RunOutcome outcome = runWithoutTests(file("src/main/java/com/promptstudio/Solution.java", """
                package com.promptstudio;

                public class Solution {
                    public static void main(String[] args) {
                        System.out.println("패키지 안에서 실행");
                    }
                }
                """));

        assertThat(outcome.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(outcome.stdout()).contains("패키지 안에서 실행");
    }

    @Test
    void main이_여러_개면_Main_클래스를_고른다() {
        RunOutcome outcome = runWithoutTests(
                file("src/main/java/Helper.java", """
                        public class Helper {
                            public static void main(String[] args) {
                                System.out.println("헬퍼가 실행됨");
                            }
                        }
                        """),
                file("src/main/java/Main.java", """
                        public class Main {
                            public static void main(String[] args) {
                                System.out.println("메인이 실행됨");
                            }
                        }
                        """));

        assertThat(outcome.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(outcome.stdout()).contains("메인이 실행됨");
    }

    @Test
    void main이_없으면_RUNTIME_ERROR가_된다() {
        RunOutcome outcome = runWithoutTests(file("src/main/java/Helper.java", """
                public class Helper {
                    public static int twice(int value) {
                        return value * 2;
                    }
                }
                """));

        assertThat(outcome.status()).isEqualTo(RunStatus.RUNTIME_ERROR);
        assertThat(outcome.stderr()).contains("main");
    }

    @Test
    void 상위_경로를_참조하는_파일은_거부된다() {
        RunOutcome outcome = runWithoutTests(file("../escaped/Main.java", "public class Main {}"));

        assertThat(outcome.status()).isEqualTo(RunStatus.RUNNER_ERROR);
        assertThat(outcome.stderr()).contains("허용되지 않는 파일 경로");
    }

    @Test
    void 절대_경로_파일은_거부된다() {
        RunOutcome outcome = runWithoutTests(file("/etc/passwd", "덮어쓰기 시도"));

        assertThat(outcome.status()).isEqualTo(RunStatus.RUNNER_ERROR);
        assertThat(outcome.stderr()).contains("허용되지 않는 파일 경로");
    }

    @Test
    void 자바_파일이_없으면_COMPILE_ERROR가_된다() {
        RunOutcome outcome = runWithoutTests(file("README.md", "# 문서만 있다"));

        assertThat(outcome.status()).isEqualTo(RunStatus.COMPILE_ERROR);
        assertThat(outcome.stderr()).contains(".java");
    }

    @Test
    void 파일이_비어있으면_COMPILE_ERROR가_된다() {
        RunOutcome outcome = runWithoutTests();

        assertThat(outcome.status()).isEqualTo(RunStatus.COMPILE_ERROR);
    }

    @Test
    void 테스트가_모두_통과하면_SUCCEEDED가_된다() {
        RunOutcome outcome = testRunner.execute(
                List.of(file("src/main/java/Calculator.java", """
                        public class Calculator {
                            public int add(int a, int b) {
                                return a + b;
                            }
                        }
                        """)),
                List.of(file("src/test/java/CalculatorTest.java", TEST_SOURCE)));

        assertThat(outcome.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(outcome.exitCode()).isZero();
        assertThat(outcome.stdout()).contains("1 tests successful");
    }

    /**
     * 통과했을 때도 어떤 케이스가 돌았는지 보여야 한다. summary 모드는 개수만 내므로 tree를 쓴다.
     * 테스트 메서드명이 곧 케이스 명세라 이름이 그대로 결과에 실린다.
     */
    @Test
    void 통과한_테스트도_케이스_이름을_출력한다() {
        RunOutcome outcome = testRunner.execute(
                List.of(file("src/main/java/Calculator.java", """
                        public class Calculator {
                            public int add(int a, int b) {
                                return a + b;
                            }
                        }
                        """)),
                List.of(file("src/test/java/CalculatorTest.java", TEST_SOURCE)));

        assertThat(outcome.stdout()).contains("두_수를_더한다");
        assertThat(outcome.stdout()).contains("CalculatorTest");
    }

    @Test
    void 테스트가_깨지면_TEST_FAILED와_실패_내용을_돌려준다() {
        RunOutcome outcome = testRunner.execute(
                List.of(file("src/main/java/Calculator.java", """
                        public class Calculator {
                            public int add(int a, int b) {
                                return a - b;
                            }
                        }
                        """)),
                List.of(file("src/test/java/CalculatorTest.java", TEST_SOURCE)));

        assertThat(outcome.status()).isEqualTo(RunStatus.TEST_FAILED);
        assertThat(outcome.exitCode()).isEqualTo(1);
        // 실패 리포트는 런처가 표준 출력으로 낸다.
        assertThat(outcome.stdout()).contains("두_수를_더한다");
        assertThat(outcome.stdout()).contains("1 tests failed");
    }

    /**
     * 런처는 tty가 없으면 stty를 실행해 stderr를 더럽힌다. COLUMNS를 넣어 그것을 막고 있다.
     */
    @Test
    void 테스트가_통과하면_표준_에러가_비어_있다() {
        RunOutcome outcome = testRunner.execute(
                List.of(file("src/main/java/Calculator.java", """
                        public class Calculator {
                            public int add(int a, int b) {
                                return a + b;
                            }
                        }
                        """)),
                List.of(file("src/test/java/CalculatorTest.java", TEST_SOURCE)));

        assertThat(outcome.stderr()).isBlank();
    }

    @Test
    void 테스트가_컴파일되지_않으면_COMPILE_ERROR가_된다() {
        RunOutcome outcome = testRunner.execute(
                List.of(file("src/main/java/Calculator.java", """
                        public class Calculator {
                        }
                        """)),
                List.of(file("src/test/java/CalculatorTest.java", TEST_SOURCE)));

        assertThat(outcome.status()).isEqualTo(RunStatus.COMPILE_ERROR);
        assertThat(outcome.stderr()).contains("add");
    }

    /**
     * 테스트가 있으면 main은 실행하지 않는다 — 채점 기준은 테스트뿐이다.
     */
    @Test
    void 테스트가_있으면_main을_실행하지_않는다() {
        RunOutcome outcome = testRunner.execute(
                List.of(file("src/main/java/Calculator.java", """
                        public class Calculator {
                            public int add(int a, int b) {
                                return a + b;
                            }

                            public static void main(String[] args) {
                                System.out.println("main이 실행되면 안 된다");
                            }
                        }
                        """)),
                List.of(file("src/test/java/CalculatorTest.java", TEST_SOURCE)));

        assertThat(outcome.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(outcome.stdout()).doesNotContain("main이 실행되면 안 된다");
    }

    @Test
    void 테스트가_무한루프면_타임아웃으로_강제_종료된다() {
        ProcessCodeExecutor impatient = new ProcessCodeExecutor(
                COMPILE_TIMEOUT_SECONDS, RUN_TIMEOUT_SECONDS, 5, MAX_OUTPUT_BYTES, "256m", JUNIT_CONSOLE_JAR);

        RunOutcome outcome = impatient.execute(
                List.of(file("src/main/java/Calculator.java", """
                        public class Calculator {
                            public int add(int a, int b) {
                                while (true) {
                                }
                            }
                        }
                        """)),
                List.of(file("src/test/java/CalculatorTest.java", TEST_SOURCE)));

        assertThat(outcome.status()).isEqualTo(RunStatus.TIMEOUT);
        assertThat(outcome.stderr()).contains("테스트 실행이");
    }

    private RunOutcome runWithoutTests(RunRequestMessage.RunFileMessage... files) {
        return executor.execute(List.of(files), List.of());
    }

    private RunRequestMessage.RunFileMessage file(String path, String content) {
        return new RunRequestMessage.RunFileMessage(path, content);
    }
}
