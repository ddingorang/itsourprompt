package com.promptstudio.buildandtest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 자식 프로세스 실행의 공통 배관. 언어별 실행기({@link ProcessCodeExecutor},
 * {@link PythonCodeExecutor})가 공유한다 — 환경변수 차단, 타임아웃 후 프로세스 트리 강제 종료,
 * 출력 바이트 상한은 언어와 무관한 격리 규칙이라 한 곳에 둔다.
 */
final class ProcessRunner implements AutoCloseable {

    private final ExecutorService streamReaders = Executors.newVirtualThreadPerTaskExecutor();

    private final int maxOutputBytes;

    ProcessRunner(int maxOutputBytes) {
        this.maxOutputBytes = maxOutputBytes;
    }

    Outcome run(Path workingDirectory, List<String> command, long timeoutSeconds)
            throws IOException, InterruptedException, ExecutionException {
        return run(workingDirectory, command, timeoutSeconds, Map.of());
    }

    /**
     * @param environment 상속을 끊은 뒤 다시 넣을 환경변수. 꼭 필요한 것만 넣는다.
     */
    Outcome run(
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

            return new Outcome(true, null, stdout.get(), stderr.get());
        }

        return new Outcome(false, process.exitValue(), stdout.get(), stderr.get());
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

    record Outcome(boolean timedOut, Integer exitCode, String stdout, String stderr) {
    }

    @Override
    public void close() {
        streamReaders.shutdownNow();
    }
}
