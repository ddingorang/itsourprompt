package com.promptstudio.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 큐에 담아둔 응답을 순서대로 돌려주고 받은 프롬프트를 기록하는 ChatModel. 큐가 비면 등록된 예외를 던진다.
 */
final class StubChatModel implements ChatModel {

    private final Deque<ChatResponse> queued = new ArrayDeque<>();
    private final List<Prompt> receivedPrompts = new ArrayList<>();

    private RuntimeException failure;

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

        if (!queued.isEmpty()) {
            return queued.removeFirst();
        }

        if (failure != null) {
            throw failure;
        }

        throw new IllegalStateException("예상하지 못한 모델 호출입니다.");
    }

    void queue(ChatResponse response) {
        queued.addLast(response);
    }

    void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    List<Prompt> receivedPrompts() {
        return List.copyOf(receivedPrompts);
    }
}
