package org.mule.extension.mulechain.api.metadata;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class LLMResponseAttributes implements Serializable {

  private final TokenUsage tokenUsage;
  private final HashMap<String, String> additionalAttributes;
  private final ConfidenceScore confidenceScore;

  public LLMResponseAttributes(TokenUsage tokenUsage, HashMap<String, String> additionalAttributes) {
    this(tokenUsage, additionalAttributes, null);
  }

  public LLMResponseAttributes(TokenUsage tokenUsage, HashMap<String, String> additionalAttributes,
                               ConfidenceScore confidenceScore) {
    this.tokenUsage = tokenUsage;
    this.additionalAttributes = additionalAttributes;
    this.confidenceScore = confidenceScore;
  }

  public TokenUsage getTokenUsage() {
    return tokenUsage;
  }

  public Map<String, String> getAdditionalAttributes() {
    return additionalAttributes;
  }

  public ConfidenceScore getConfidenceScore() {
    return confidenceScore;
  }
}
