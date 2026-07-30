package com.promptstudio.buildandtest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessCodeExecutorTest {

    private static final long COMPILE_TIMEOUT_SECONDS = 60;
    private static final long RUN_TIMEOUT_SECONDS = 5;
    private static final int MAX_OUTPUT_BYTES = 1024;

    private final ProcessCodeExecutor executor = new ProcessCodeExecutor(
            COMPILE_TIMEOUT_SECONDS, RUN_TIMEOUT_SECONDS, MAX_OUTPUT_BYTES, "256m");

    @Test
    void 정상_코드는_성공하고_표준출력을_돌려준다() {
        RunOutcome outcome = executor.execute(List.of(file("src/main/java/Main.java", """
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("Hello World");
                    }
                }
                """)));

        assertThat(outcome.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(outcome.exitCode()).isZero();
        assertThat(outcome.stdout()).contains("Hello World");
    }

    @Test
    void 한국어_출력이_깨지지_않는다() {
        RunOutcome outcome = executor.execute(List.of(file("src/main/java/Main.java", """
                public class Main {
                    public static void main(String[] args) {
                        // 여기에 코드를 작성하세요.
                        System.out.println("환영합니다");
                    }
                }
                """)));

        assertThat(outcome.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(outcome.stdout()).contains("환영합니다");
    }

    @Test
    void 구문_오류는_COMPILE_ERROR와_컴파일러_메시지를_돌려준다() {
        RunOutcome outcome = executor.execute(List.of(file("src/main/java/Main.java", """
                public class Main {
                    public static void main(String[] args) {
                        this is not java
                    }
                }
                """)));

        assertThat(outcome.status()).isEqualTo(RunStatus.COMPILE_ERROR);
        assertThat(outcome.exitCode()).isNotZero();
        assertThat(outcome.stderr()).isNotBlank();
    }

    @Test
    void 예외를_던지면_RUNTIME_ERROR가_된다() {
        RunOutcome outcome = executor.execute(List.of(file("src/main/java/Main.java", """
                public class Main {
                    public static void main(String[] args) {
                        throw new IllegalStateException("펑");
                    }
                }
                """)));

        assertThat(outcome.status()).isEqualTo(RunStatus.RUNTIME_ERROR);
        assertThat(outcome.exitCode()).isNotZero();
        assertThat(outcome.stderr()).contains("IllegalStateException");
    }

    @Test
    void 무한루프는_타임아웃으로_강제_종료된다() {
        RunOutcome outcome = executor.execute(List.of(file("src/main/java/Main.java", """
                public class Main {
                    public static void main(String[] args) {
                        while (true) {
                        }
                    }
                }
                """)));

        assertThat(outcome.status()).isEqualTo(RunStatus.TIMEOUT);
        assertThat(outcome.exitCode()).isNull();
        // 타임아웃 상한을 넘겨 실제로 죽었는지 확인한다(무한정 매달려 있지 않았다는 뜻).
        assertThat(outcome.durationMs()).isLessThan(RUN_TIMEOUT_SECONDS * 4_000);
    }

    @Test
    void 무한_출력은_상한에서_절단된다() {
        RunOutcome outcome = executor.execute(List.of(file("src/main/java/Main.java", """
                public class Main {
                    public static void main(String[] args) {
                        for (int i = 0; i < 100_000; i++) {
                            System.out.println("네 번째 줄보다 훨씬 긴 출력을 반복해서 뱉는다");
                        }
                    }
                }
                """)));

        assertThat(outcome.stdout()).contains("절단되었습니다");
        // 절단 안내 문구를 더한 길이 이상으로는 커지지 않는다.
        assertThat(outcome.stdout().getBytes()).hasSizeLessThan(MAX_OUTPUT_BYTES + 200);
    }

    @Test
    void 패키지가_있는_클래스도_실행된다() {
        RunOutcome outcome = executor.execute(List.of(file("src/main/java/com/promptstudio/Solution.java", """
                package com.promptstudio;

                public class Solution {
                    public static void main(String[] args) {
                        System.out.println("패키지 안에서 실행");
                    }
                }
                """)));

        assertThat(outcome.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(outcome.stdout()).contains("패키지 안에서 실행");
    }

    @Test
    void main이_여러_개면_Main_클래스를_고른다() {
        RunOutcome outcome = executor.execute(List.of(
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
                        """)));

        assertThat(outcome.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(outcome.stdout()).contains("메인이 실행됨");
    }

    @Test
    void main이_없으면_RUNTIME_ERROR가_된다() {
        RunOutcome outcome = executor.execute(List.of(file("src/main/java/Helper.java", """
                public class Helper {
                    public static int twice(int value) {
                        return value * 2;
                    }
                }
                """)));

        assertThat(outcome.status()).isEqualTo(RunStatus.RUNTIME_ERROR);
        assertThat(outcome.stderr()).contains("main");
    }

    @Test
    void 상위_경로를_참조하는_파일은_거부된다() {
        RunOutcome outcome = executor.execute(List.of(file("../escaped/Main.java", "public class Main {}")));

        assertThat(outcome.status()).isEqualTo(RunStatus.RUNNER_ERROR);
        assertThat(outcome.stderr()).contains("허용되지 않는 파일 경로");
    }

    @Test
    void 절대_경로_파일은_거부된다() {
        RunOutcome outcome = executor.execute(List.of(file("/etc/passwd", "덮어쓰기 시도")));

        assertThat(outcome.status()).isEqualTo(RunStatus.RUNNER_ERROR);
        assertThat(outcome.stderr()).contains("허용되지 않는 파일 경로");
    }

    @Test
    void 자바_파일이_없으면_COMPILE_ERROR가_된다() {
        RunOutcome outcome = executor.execute(List.of(file("README.md", "# 문서만 있다")));

        assertThat(outcome.status()).isEqualTo(RunStatus.COMPILE_ERROR);
        assertThat(outcome.stderr()).contains(".java");
    }

    @Test
    void 파일이_비어있으면_COMPILE_ERROR가_된다() {
        RunOutcome outcome = executor.execute(List.of());

        assertThat(outcome.status()).isEqualTo(RunStatus.COMPILE_ERROR);
    }

    private RunRequestMessage.RunFileMessage file(String path, String content) {
        return new RunRequestMessage.RunFileMessage(path, content);
    }
}
