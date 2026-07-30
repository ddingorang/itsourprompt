package com.promptstudio.attempt.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "attempt_turn")
public class Turn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_prompt", nullable = false, columnDefinition = "text")
    private String userPrompt;

    @Column(name = "ai_summary", nullable = false, columnDefinition = "text")
    private String aiSummary;

    @ElementCollection
    @CollectionTable(name = "turn_file_change", joinColumns = @JoinColumn(name = "turn_id"))
    @OrderColumn(name = "ordinal")
    private List<FileChange> changes = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "turn_tool_call", joinColumns = @JoinColumn(name = "turn_id"))
    @OrderColumn(name = "ordinal")
    private List<ToolCallEntry> toolCalls = new ArrayList<>();

    protected Turn() {
    }

    public Turn(String userPrompt, String aiSummary, List<FileChange> changes, List<ToolCallEntry> toolCalls) {
        this.userPrompt = userPrompt;
        this.aiSummary = aiSummary;
        this.changes = new ArrayList<>(changes);
        this.toolCalls = new ArrayList<>(toolCalls);
    }

    public String userPrompt() {
        return userPrompt;
    }

    public String aiSummary() {
        return aiSummary;
    }

    public List<FileChange> changes() {
        return List.copyOf(changes);
    }

    public List<ToolCallEntry> toolCalls() {
        return List.copyOf(toolCalls);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Turn turn)) {
            return false;
        }

        return id != null && id.equals(turn.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
