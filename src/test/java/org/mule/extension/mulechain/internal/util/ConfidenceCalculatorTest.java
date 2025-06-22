/**
 * (c) 2003-2024 MuleSoft, Inc. The software in this package is published under the terms of the Commercial Free Software license V.1 a copy of which has been included with this distribution in the LICENSE.md file.
 */
package org.mule.extension.mulechain.internal.util;

import org.junit.Test;
import org.mule.extension.mulechain.api.config.ConfidenceStrategy;
import org.mule.extension.mulechain.api.metadata.ConfidenceScore;
import org.mule.extension.mulechain.api.model.LogProbsData;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.*;

public class ConfidenceCalculatorTest {

  @Test
  public void testCalculateWithEmptyLogProbsReturnsUnavailable() {
    LogProbsData emptyData = new LogProbsData(Collections.emptyList(), Collections.emptyMap());
    ConfidenceScore result = ConfidenceCalculator.calculate(emptyData, ConfidenceStrategy.ENTROPY_BASED);

    assertFalse("Confidence should be unavailable for empty data", result.isAvailable());
  }

  @Test
  public void testCalculateWithNullDataReturnsUnavailable() {
    ConfidenceScore result = ConfidenceCalculator.calculate(null, ConfidenceStrategy.ENTROPY_BASED);

    assertFalse("Confidence should be unavailable for null data", result.isAvailable());
  }

  @Test
  public void testEntropyBasedConfidenceCalculation() {
    // Create mock log probabilities data
    List<LogProbsData.TokenLogProb> tokens = Arrays.asList(
                                                           createTokenLogProb("Hello", -0.1, Collections.emptyList()),
                                                           createTokenLogProb("world", -0.2, Collections.emptyList()),
                                                           createTokenLogProb("!", -0.05, Collections.emptyList()));

    LogProbsData data = new LogProbsData(tokens, new HashMap<>());
    ConfidenceScore result = ConfidenceCalculator.calculate(data, ConfidenceStrategy.ENTROPY_BASED);

    assertTrue("Confidence should be available", result.isAvailable());
    assertTrue("Confidence score should be between 0 and 1", result.getScore() >= 0.0 && result.getScore() <= 1.0);
    assertEquals("Strategy should match", ConfidenceStrategy.ENTROPY_BASED.getValue(), result.getStrategy());
    assertEquals("Token count should match", 3, result.getTotalTokens());
  }

  @Test
  public void testTopTokenProbabilityConfidenceCalculation() {
    List<LogProbsData.TokenLogProb> tokens = Arrays.asList(
                                                           createTokenLogProb("Good", -0.1, Collections.emptyList()),
                                                           createTokenLogProb("morning", -0.15, Collections.emptyList()));

    LogProbsData data = new LogProbsData(tokens, new HashMap<>());
    ConfidenceScore result = ConfidenceCalculator.calculate(data, ConfidenceStrategy.TOP_TOKEN_PROB);

    assertTrue("Confidence should be available", result.isAvailable());
    assertTrue("Confidence score should be between 0 and 1", result.getScore() >= 0.0 && result.getScore() <= 1.0);
    assertEquals("Strategy should match", ConfidenceStrategy.TOP_TOKEN_PROB.getValue(), result.getStrategy());
  }

  @Test
  public void testAverageLogProbConfidenceCalculation() {
    List<LogProbsData.TokenLogProb> tokens = Arrays.asList(
                                                           createTokenLogProb("The", -0.05, Collections.emptyList()),
                                                           createTokenLogProb("answer", -0.3, Collections.emptyList()),
                                                           createTokenLogProb("is", -0.1, Collections.emptyList()));

    LogProbsData data = new LogProbsData(tokens, new HashMap<>());
    ConfidenceScore result = ConfidenceCalculator.calculate(data, ConfidenceStrategy.AVERAGE_LOG_PROB);

    assertTrue("Confidence should be available", result.isAvailable());
    assertTrue("Confidence score should be between 0 and 1", result.getScore() >= 0.0 && result.getScore() <= 1.0);
    assertEquals("Strategy should match", ConfidenceStrategy.AVERAGE_LOG_PROB.getValue(), result.getStrategy());
  }

  @Test
  public void testVarianceBasedConfidenceCalculation() {
    List<LogProbsData.TokenLogProb> tokens = Arrays.asList(
                                                           createTokenLogProb("Consistent", -0.1, Collections.emptyList()),
                                                           createTokenLogProb("tokens", -0.12, Collections.emptyList()),
                                                           createTokenLogProb("here", -0.11, Collections.emptyList()));

    LogProbsData data = new LogProbsData(tokens, new HashMap<>());
    ConfidenceScore result = ConfidenceCalculator.calculate(data, ConfidenceStrategy.VARIANCE_BASED);

    assertTrue("Confidence should be available", result.isAvailable());
    assertTrue("Confidence score should be between 0 and 1", result.getScore() >= 0.0 && result.getScore() <= 1.0);
    assertEquals("Strategy should match", ConfidenceStrategy.VARIANCE_BASED.getValue(), result.getStrategy());
  }

  @Test
  public void testWeightedEntropyConfidenceCalculation() {
    List<LogProbsData.TokenLogProb> tokens = Arrays.asList(
                                                           createTokenLogProb("First", -0.1, Collections.emptyList()),
                                                           createTokenLogProb("second", -0.2, Collections.emptyList()),
                                                           createTokenLogProb("third", -0.3, Collections.emptyList()));

    LogProbsData data = new LogProbsData(tokens, new HashMap<>());
    ConfidenceScore result = ConfidenceCalculator.calculate(data, ConfidenceStrategy.WEIGHTED_ENTROPY);

    assertTrue("Confidence should be available", result.isAvailable());
    assertTrue("Confidence score should be between 0 and 1", result.getScore() >= 0.0 && result.getScore() <= 1.0);
    assertEquals("Strategy should match", ConfidenceStrategy.WEIGHTED_ENTROPY.getValue(), result.getStrategy());
  }

  @Test
  public void testHighConfidenceTokens() {
    // Very high probability tokens (low log probability) should result in high
    // confidence
    List<LogProbsData.TokenLogProb> tokens = Arrays.asList(
                                                           createTokenLogProb("The", -0.001, Collections.emptyList()),
                                                           createTokenLogProb("cat", -0.002, Collections.emptyList()),
                                                           createTokenLogProb("sat", -0.001, Collections.emptyList()));

    LogProbsData data = new LogProbsData(tokens, new HashMap<>());
    ConfidenceScore result = ConfidenceCalculator.calculate(data, ConfidenceStrategy.TOP_TOKEN_PROB);

    assertTrue("High probability tokens should result in high confidence", result.getScore() > 0.7);
    assertEquals("Confidence level should be HIGH or VERY_HIGH",
                 true,
                 result.getConfidenceLevel().equals("HIGH") || result.getConfidenceLevel().equals("VERY_HIGH"));
  }

  @Test
  public void testLowConfidenceTokens() {
    // Low probability tokens (high log probability) should result in low confidence
    List<LogProbsData.TokenLogProb> tokens = Arrays.asList(
                                                           createTokenLogProb("Maybe", -3.0, Collections.emptyList()),
                                                           createTokenLogProb("perhaps", -4.0, Collections.emptyList()),
                                                           createTokenLogProb("uncertain", -5.0, Collections.emptyList()));

    LogProbsData data = new LogProbsData(tokens, new HashMap<>());
    ConfidenceScore result = ConfidenceCalculator.calculate(data, ConfidenceStrategy.TOP_TOKEN_PROB);

    assertTrue("Low probability tokens should result in low confidence", result.getScore() < 0.3);
    assertEquals("Confidence level should be LOW or VERY_LOW",
                 true,
                 result.getConfidenceLevel().equals("LOW") || result.getConfidenceLevel().equals("VERY_LOW"));
  }

  private LogProbsData.TokenLogProb createTokenLogProb(String token, double logprob,
                                                       List<LogProbsData.TopLogProb> topLogprobs) {
    return new LogProbsData.TokenLogProb(token, logprob, topLogprobs, token.getBytes());
  }
}
