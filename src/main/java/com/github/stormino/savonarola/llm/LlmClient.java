package com.github.stormino.savonarola.llm;

public interface LlmClient {
    /** Returns the raw text completion, or throws LlmException on failure. */
    String complete(String model, LlmCallType callType, String systemPrompt, String userPrompt);
}
