/**
 * (c) 2003-2024 MuleSoft, Inc. The software in this package is published under the terms of the Commercial Free Software license V.1 a copy of which has been included with this distribution in the LICENSE.md file.
 */
package org.mule.extension.mulechain.api.config;

/**
 * Enumeration of confidence calculation strategies.
 */
public enum ConfidenceStrategy {

  /**
   * Calculate confidence based on entropy of token probability distribution.
   * Higher entropy indicates lower confidence.
   */
  ENTROPY_BASED("entropy_based"),

  /**
   * Use the average probability of the most likely tokens.
   * Higher average top token probability indicates higher confidence.
   */
  TOP_TOKEN_PROB("top_token_prob"),

  /**
   * Calculate confidence as the average of all token log probabilities.
   * Higher average log probability indicates higher confidence.
   */
  AVERAGE_LOG_PROB("average_log_prob"),

  /**
   * Weighted entropy calculation considering token position importance.
   * Tokens at the beginning of the response may be weighted differently.
   */
  WEIGHTED_ENTROPY("weighted_entropy"),

  /**
   * Calculate confidence based on the variance of token probabilities.
   * Lower variance indicates higher confidence.
   */
  VARIANCE_BASED("variance_based");

  private final String value;

  ConfidenceStrategy(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return value;
  }
}
