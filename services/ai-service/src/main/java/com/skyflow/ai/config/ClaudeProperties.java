package com.skyflow.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "skyflow.ai")
public class ClaudeProperties {

    /** Populated from ANTHROPIC_API_KEY. Endpoints report 502 until it is set. */
    private String apiKey = "";

    private String model = "claude-opus-5";

    private long maxTokens = 8000;

    /**
     * Effort for the support chat. Chat is not an intelligence-sensitive workload, so medium buys
     * back latency and tokens without a quality drop worth measuring.
     */
    private String chatEffort = "medium";

    /** Effort for turning a search phrase into filters - a narrow extraction task. */
    private String extractionEffort = "low";

    /** Safety valve on the tool loop: a runaway conversation cannot bill forever. */
    private int maxToolIterations = 6;

    /** Turns of history replayed to the model. */
    private int historyTurns = 12;

    private Duration conversationTtl = Duration.ofHours(2);

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public long getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(long maxTokens) {
        this.maxTokens = maxTokens;
    }

    public String getChatEffort() {
        return chatEffort;
    }

    public void setChatEffort(String chatEffort) {
        this.chatEffort = chatEffort;
    }

    public String getExtractionEffort() {
        return extractionEffort;
    }

    public void setExtractionEffort(String extractionEffort) {
        this.extractionEffort = extractionEffort;
    }

    public int getMaxToolIterations() {
        return maxToolIterations;
    }

    public void setMaxToolIterations(int maxToolIterations) {
        this.maxToolIterations = maxToolIterations;
    }

    public int getHistoryTurns() {
        return historyTurns;
    }

    public void setHistoryTurns(int historyTurns) {
        this.historyTurns = historyTurns;
    }

    public Duration getConversationTtl() {
        return conversationTtl;
    }

    public void setConversationTtl(Duration conversationTtl) {
        this.conversationTtl = conversationTtl;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
