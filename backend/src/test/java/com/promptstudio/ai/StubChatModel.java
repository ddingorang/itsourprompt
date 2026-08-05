package com.promptstudio.ai;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 큐에 담아둔 응답을 순서대로 돌려주고 받은 프롬프트를 기록하는 ChatModel. 큐가 비면 등록된 예외를 던진다.
 *
 * <p>피드백 생성기 둘이 동시에 부르므로 자료구조는 모두 동시성 컬렉션이다. 순서만으로 응답을 나눠 주면
 * 어느 브랜치가 어느 응답을 집는지가 동전 던지기가 되므로, 갈라야 하는 테스트는 {@link #queueFor}로
 * 시스템 프롬프트 조각을 지정한다.
 */
final class StubChatModel implements ChatModel {

    private final Queue<ChatResponse> queued = new ConcurrentLinkedDeque<>();
    private final Map<String, Queue<ChatResponse>> queuedBySystemPrompt = new ConcurrentHashMap<>();
    private final List<Prompt> receivedPrompts = new CopyOnWriteArrayList<>();

    private volatile RuntimeException failure;

    /**
     * ChatClient가 기본 옵션 타입으로 요청 옵션을 병합하므로, 운영과 같은 OpenAI 옵션을 돌려준다.
     */
    @Override
    public OpenAiChatOptions getOptions() {
        return OpenAiChatOptions.builder().build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        receivedPrompts.add(prompt);

        ChatResponse matched = matching(prompt);

        if (matched != null) {
            return matched;
        }

        ChatResponse next = queued.poll();

        if (next != null) {
            return next;
        }

        if (failure != null) {
            throw failure;
        }

        throw new IllegalStateException("예상하지 못한 모델 호출입니다.");
    }

    void queue(ChatResponse response) {
        queued.add(response);
    }

    /**
     * 시스템 프롬프트에 조각이 들어 있는 호출에만 이 응답을 준다.
     */
    void queueFor(String systemPromptFragment, ChatResponse response) {
        queuedBySystemPrompt
                .computeIfAbsent(systemPromptFragment, fragment -> new ConcurrentLinkedQueue<>())
                .add(response);
    }

    void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    List<Prompt> receivedPrompts() {
        return List.copyOf(receivedPrompts);
    }

    /**
     * 어느 호출이 어느 렌즈였는지는 시스템 메시지로만 알 수 있다. 응답을 가르는 데도, 테스트가 보낸 것을
     * 되읽는 데도 같은 기준을 쓴다.
     */
    static String systemTextOf(Prompt prompt) {
        StringBuilder text = new StringBuilder();

        for (Message message : prompt.getInstructions()) {
            if (message.getMessageType() == MessageType.SYSTEM) {
                text.append(message.getText());
            }
        }

        return text.toString();
    }

    private ChatResponse matching(Prompt prompt) {
        String systemText = systemTextOf(prompt);

        for (Map.Entry<String, Queue<ChatResponse>> entry : queuedBySystemPrompt.entrySet()) {
            if (!systemText.contains(entry.getKey())) {
                continue;
            }

            ChatResponse response = entry.getValue().poll();

            if (response != null) {
                return response;
            }
        }

        return null;
    }
}
