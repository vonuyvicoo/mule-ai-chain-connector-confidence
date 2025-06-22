/**
 * (c) 2003-2024 MuleSoft, Inc. The software in this package is published under the terms of the Commercial Free Software license V.1 a copy of which has been included with this distribution in the LICENSE.md file.
 */
package org.mule.extension.mulechain.api.metadata;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

/**
 * Represents a confidence score for an LLM response based on log probabilities.
 */
public class ConfidenceScore implements Serializable {

  private final double score;
  private final String strategy;
  private final Map<String, Double> metrics;
  private final int totalTokens;
  private final boolean isAvailable;

  public ConfidenceScore(double score, String strategy, Map<String, Double> metrics, int totalTokens) {
    this.score = Math.max(0.0, Math.min(1.0, score)); // Clamp between 0 and 1
    this.strategy = strategy;
    this.metrics = metrics != null ? Collections.unmodifiableMap(metrics) : Collections.emptyMap();
    this.totalTokens = totalTokens;
    this.isAvailable = true;
  }

  /**
   * Creates an unavailable confidence score (when logprobs are not supported or
   * enabled)
   */
  public static ConfidenceScore unavailable() {
    return new ConfidenceScore(0.0, "UNAVAILABLE", Collections.emptyMap(), 0, false);
  }

  private ConfidenceScore(double score, String strategy, Map<String, Double> metrics, int totalTokens,
                          boolean isAvailable) {
    this.score = score;
    this.strategy = strategy;
    this.metrics = metrics;
    this.totalTokens = totalTokens;
    this.isAvailable = isAvailable;
  }

  /**
   * @return Confidence score between 0.0 (lowest confidence) and 1.0 (highest
   *         confidence)
   */
  public double getScore() {
    return score;
  }

  /**
   * @return The strategy used to calculate this confidence score
   */
  public String getStrategy() {
    return strategy;
  }

  /**
   * @return Additional metrics used in confidence calculation
   */
  public Map<String, Double> getMetrics() {
    return metrics;
  }

  /**
   * @return Number of tokens used in confidence calculation
   */
  public int getTotalTokens() {
    return totalTokens;
  }

  /**
   * @return Whether confidence score is available (logprobs were accessible)
   */
  public boolean isAvailable() {
    return isAvailable;
  }

  /**
   * @return Human-readable confidence level description
   */
  public String getConfidenceLevel() {
    if (!isAvailable) {
      return "UNAVAILABLE";
    }
    if (score >= 0.9) {
      return "VERY_HIGH";
    } else if (score >= 0.75) {
      return "HIGH";
    } else if (score >= 0.5) {
      return "MEDIUM";
    } else if (score >= 0.25) {
      return "LOW";
    } else {
      return "VERY_LOW";
    }
  }

  @Override
  public String toString() {
    return String.format("ConfidenceScore{score=%.3f, level=%s, strategy=%s, tokens=%d, available=%s}",
                         score, getConfidenceLevel(), strategy, totalTokens, isAvailable);
  }
}
