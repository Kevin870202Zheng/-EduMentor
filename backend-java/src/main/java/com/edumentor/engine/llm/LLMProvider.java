package com.edumentor.engine.llm;

public enum LLMProvider {
    OPENAI("OpenAI", "gpt-4o-mini", "https://api.openai.com"),
    DEEPSEEK("DeepSeek", "deepseek-v4-pro", "https://api.deepseek.com"),
    OLLAMA("Ollama", "llama3", "http://localhost:11434"),
    ZHIPU("智谱", "glm-4", "https://open.bigmodel.cn"),
    WENXIN("文心", "ernie-4", "https://aip.baidubce.com"),
    MOCK("Mock", "mock-model", "");

    private final String displayName;
    private final String defaultModel;
    private final String defaultApiBase;

    LLMProvider(String displayName, String defaultModel, String defaultApiBase) {
        this.displayName = displayName;
        this.defaultModel = defaultModel;
        this.defaultApiBase = defaultApiBase;
    }

    public String getDisplayName() { return displayName; }
    public String getDefaultModel() { return defaultModel; }
    public String getDefaultApiBase() { return defaultApiBase; }

    public static LLMProvider fromName(String name) {
        if (name == null || name.isBlank()) return MOCK;
        for (LLMProvider p : values()) {
            if (p.name().equalsIgnoreCase(name.trim())) return p;
        }
        return MOCK;
    }
}
