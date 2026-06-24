package com.edumentor.engine.llm;

import java.util.Objects;

/**
 * 对话消息 — 表示 LLM 多轮对话中的一条消息。
 *
 * @author EduMentor Team
 */
public class ChatMessage {

    private final String role;
    private final String content;

    public ChatMessage(String role, String content) {
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
    }

    public static ChatMessage userMessage(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistantMessage(String content) {
        return new ChatMessage("assistant", content);
    }

    public static ChatMessage systemMessage(String content) {
        return new ChatMessage("system", content);
    }

    public String getRole() { return role; }
    public String getContent() { return content; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatMessage that = (ChatMessage) o;
        return role.equals(that.role) && content.equals(that.content);
    }

    @Override
    public int hashCode() { return Objects.hash(role, content); }

    @Override
    public String toString() {
        return "ChatMessage{role='" + role + "', content='"
                + (content.length() > 80 ? content.substring(0, 80) + "..." : content) + "'}";
    }
}
